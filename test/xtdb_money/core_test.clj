(ns xtdb-money.core-test
  (:require [clojure.test :refer [deftest is]]
            [xtdb-money.core :as mny]))

(deftest create-an-account
  (let [account {:id (java.util.UUID/randomUUID)
                 :name "Checking"
                 :type :asset}]
    (mny/create-account account)
    (is (= [account]
           (mny/accounts))
        "A saved account can be retrieved")))
