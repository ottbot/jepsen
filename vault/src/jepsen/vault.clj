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


(defn node-ip-addresses [test])

(defn db
  "Vault transit"
  [version]
  (reify db/DB
    (setup! [_ test node]

      (c/su
        (c/exec :echo
                (str
                  (slurp (io/resource "tasks")))
                :> "/tmp/jepsen-db-tasks")

        (c/exec :chmod :+x "/tmp/jepsen-db-tasks")

        (log/info node "installing Vault" version)
        (exec-task :install-vault version)

        (log/info node "starting Consul")
        (exec-task :install-consul)

        (log/info node (:nodes test))
        (exec-task :start-consul (count (:nodes test)))
        ;; when node is :n1, join the servers. Looks up hostnames
        ))

    (teardown! [_ test node]
      (log/info node "trearing down Consul")
      (c/su
        (exec-task :stop-consul))
      (log/info node "tearing down Vault"))))

(defn vault-test
  [version]
  (assoc tests/noop-test
         :ssh {:username "admin"}                           ;; TODO -- change test nodes to allow root..
         :os debian/os
         :db (db version)))
