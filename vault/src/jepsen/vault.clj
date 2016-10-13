(ns jepsen.vault
  (:require [clojure.tools.logging :as log]
            [jepsen.tests :as tests]
            [jepsen.db :as db]
            [jepsen.control :as c]
            [jepsen.tests :as tests]
            [jepsen.os.debian :as debian]
            [clojure.java.io :as io]))




;; Start consul
;; consul agent -server -bootstrap-expect 1 -data-dir /tmp/consul

(def exec-task
  (partial c/exec "/tmp/jepsen-db-tasks"))


(defn missing-command? [cmd]
  (empty?
    (c/exec :command :-v cmd c/| :cat)))


(defn hashicorp-url [cmd version]
  (let [cmd (name cmd)]
    (str "https://releases.hashicorp.com/" cmd "/"
         version "/" cmd "_" version "_linux_amd64.zip")))

(defn install-hashicorp-tool [node cmd version]
  (if (missing-command? cmd)
    (do
      (log/info node "installing" cmd version)
      (c/exec :curl :-o :tmp.zip (hashicorp-url cmd version))
      (c/exec :unzip :-o :tmp.zip)
      (c/exec :rm :tmp.zip)
      (c/exec :mv cmd (str "/usr/local/bin/" (name cmd))))
    (log/info node cmd "already installed")))



(defn db
  "Vault transit"
  [version]
  (reify db/DB
    (setup! [_ test node]
      (c/su
        (install-hashicorp-tool node :consul "0.7.0")
        (install-hashicorp-tool node :vault version)

        ))

    (teardown! [_ test node]
      )))

(defn vault-test
  [version]
  (assoc tests/noop-test
         :ssh {:username "admin"}                           ;; TODO -- change test nodes to allow root..
         :os debian/os
         :db (db version)))
