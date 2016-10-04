(ns jepsen.vault
  (:require [clojure.tools.logging :as log]
            [jepsen.tests :as tests]
            [jepsen.db :as db]
            [jepsen.control :as c]
            [jepsen.tests :as tests]
            [jepsen.os.debian :as debian]))


(defn db
  "Vault"
  [version]
  (reify db/DB
    (setup! [_ test node]
      (log/info node "installing Vault" version))
    (teardown! [_ test node]
      (log/info node "tearing down Vault"))))

(defn vault-test
  [version]
  (assoc tests/noop-test
         :ssh {:username "admin"}                           ;; TODO -- change test nodes to allow root
         :os debian/os
         :db (db version)))
