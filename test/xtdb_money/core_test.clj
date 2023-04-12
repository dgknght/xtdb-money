(ns xtdb-money.core-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.core :as mny]))

(use-fixtures :each (fn [f]
                      (mny/start)
                      (f)
                      (mny/stop)))

(deftest create-an-account
  (let [account {:id (java.util.UUID/randomUUID)
                 :name "Checking"
                 :type :asset}]
    (mny/create-account account)
    (is (= [account]
           (mny/accounts))
        "A saved account can be retrieved")))

(deftest find-an-account-by-name
  (doseq [a [{:name "Checking"
              :type :asset}
             {:name "Salary"
              :type :income}]]
    (mny/create-account a))
  (is (comparable? {:name "Salary"
                    :type :income}
                   (mny/find-account-by-name "Salary"))
      "The account with the specified name is returned"))
