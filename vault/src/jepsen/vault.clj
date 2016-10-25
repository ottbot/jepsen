(ns jepsen.vault
  (:require [clojure.tools.logging :as log]
            [jepsen.tests :as tests]
            [jepsen.db :as db]
            [jepsen.core :as core]
            [jepsen.control :as c]
            [jepsen.tests :as tests]
            [jepsen.os.debian :as debian]
            [jepsen.control.net :as net]
            [clojure.data.json :as json]
            [jepsen.util :as util]))

;; Start consul
;; consul agent -server -bootstrap-expect 1 -data-dir /tmp/consul

(defn missing-command? [cmd]
  (empty?
    (c/exec :command :-v cmd c/| :cat)))


(defn hashicorp-url [cmd version]
  (let [cmd (name cmd)]
    (str "https://releases.hashicorp.com/" cmd "/"
         version "/" cmd "_" version "_linux_amd64.zip")))


(def consul-pidfile "/var/run/consul.pid")
(def consul-data-dir "/var/lib/consul")
(def consul-log-file "/var/log/consul.log")

(def vault-pidfile "/var/run/vault.pid")
(def vault-log-file "/var/log/vault.log")
(def vault-config-file "/tmp/vault.json")


(defn primary? [test node]
  (= node (core/primary test)))

(defn start-consul!
  [test node]
  (log/info node "starting consul")
  (c/exec :start-stop-daemon
          :--start
          :--background
          :--make-pidfile
          :--pidfile consul-pidfile
          :--chdir "/opt/consul"
          :--exec "/usr/local/bin/consul"
          :--no-close
          :--
          :agent
          :-server
          :-log-level "debug"
          :-client "0.0.0.0"
          :-bind (net/ip (name node))
          :-data-dir consul-data-dir
          :-node (name node)
          :-bootstrap-expect (count (:nodes test))
          :>> consul-log-file
          (c/lit "2>&1"))

  (when (primary? test node)
    (log/info node "joining consul cluster")
    (c/exec :consul
            :join
            (map (comp net/ip name) (:nodes test)))))


(def vault-keys (promise))

(defn vault-config [node]
  {:backend      {:consul
                  {:address       "127.0.0.1:8500"
                   :path          "vault"
                   :redirect_addr (str "https://"
                                       (name node)
                                       ":8600")}}

   :listener     {:tcp
                  {:address     "0.0.0.0:8200"
                   :tls_disable 1}}

   :cluster_name "jepsen-vault"})


(defn start-vault!
  [test node]
  (log/info node "starting vault")

  (c/exec :echo
          (json/write-str
            (vault-config node)
            :escape-slash false)
          :> vault-config-file)

  (c/exec :start-stop-daemon
          :--start
          :--background
          :--make-pidfile
          :--pidfile vault-pidfile
          :--chdir "/opt/vault"
          :--exec "/usr/local/bin/vault"
          :--no-close
          :--
          :server
          :-log-level "debug"
          :-config vault-config-file
          :>> vault-log-file
          (c/lit "2>&1"))

  (when (primary? test node)
    (log/info node "initializing vault")
    (deliver
      vault-keys
      (re-seq #"[a-f0-9]{66}"
              (c/exec :vault :init :-address "http://127.0.0.1:8200"))))

  (doseq [k @vault-keys]
    (log/info node "unsealing vault with " k)
    (c/exec :vault :unseal :-address "http://127.0.0.1:8200" k)))

(defn stop-consul!
  [test node]
  (c/su
    (util/meh
      (c/exec :killall :-9 :consul))
    (c/exec :rm :-rf consul-pidfile consul-data-dir))
  (log/info node "stopping consul"))

(defn stop-vault!
  [test node]
  (c/su
    (util/meh
      (c/exec :killall :-9 :vault)))
  (log/info node "stopping vault"))


(defn install-hashicorp-tool [node cmd version]
  (if (missing-command? cmd)
    (do
      (log/info node "installing" cmd version)
      (c/exec :curl :-o :tmp.zip (hashicorp-url cmd version))
      (c/exec :unzip :-o :tmp.zip)
      (c/exec :rm :tmp.zip)
      (c/exec :mv cmd (str "/usr/local/bin/" (name cmd)))
      (c/exec :mkdir (str "/opt/" (name cmd))))

    (log/info node cmd "already installed")))


(defn db
  "Vault transit"
  [version]
  (reify db/DB
    (setup! [_ test node]
      (c/su
        (install-hashicorp-tool node :consul "0.7.0")
        (install-hashicorp-tool node :vault version)
        (start-consul! test node)
        (start-vault! test node)))
    (teardown! [_ test node]
      (stop-consul! test node)
      (stop-vault! test node)
      )))

(defn vault-test
  [version]
  (assoc tests/noop-test
         :ssh {:username "admin"}                           ;; TODO -- change test nodes to allow root..
         :os debian/os
         :db (db version)))