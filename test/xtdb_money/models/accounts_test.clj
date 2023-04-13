(ns xtdb-money.models.accounts-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.helpers :refer [reset-db]]
            [xtdb-money.models.accounts :as acts]))

(use-fixtures :each reset-db)

(deftest create-an-account
  (let [account {:name "Checking"
                 :type :asset}]
    (acts/put account)
    (is (seq-of-maps-like? [account]
           (acts/select))
        "A saved account can be retrieved")))

(deftest find-an-account-by-name
  (doseq [a [{:name "Checking"
              :type :asset}
             {:name "Salary"
              :type :income}]]
    (acts/put a))
  (is (comparable? {:name "Salary"
                    :type :income}
                   (acts/find-by-name "Salary"))
      "The account with the specified name is returned"))
