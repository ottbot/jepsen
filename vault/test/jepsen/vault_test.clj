(ns jepsen.vault-test
  (:require [clojure.test :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.vault :as vault]))

(deftest vault-test
  (is
    (:valid?
      (:results
        (jepsen/run!
          (vault/vault-test "0.6.1"))))))
