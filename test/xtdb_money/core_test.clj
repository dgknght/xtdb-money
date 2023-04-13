(ns xtdb-money.core-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.core :as mny]))

(use-fixtures :each (fn [f]
                      (mny/start)
                      (f)
                      (mny/stop)))

(deftest create-an-account
  (let [account {:name "Checking"
                 :type :asset}]
    (mny/put-account account)
    (is (seq-of-maps-like? [account]
           (mny/accounts))
        "A saved account can be retrieved")))

(deftest find-an-account-by-name
  (doseq [a [{:name "Checking"
              :type :asset}
             {:name "Salary"
              :type :income}]]
    (mny/put-account a))
  (is (comparable? {:name "Salary"
                    :type :income}
                   (mny/find-account-by-name "Salary"))
      "The account with the specified name is returned"))

(deftest create-a-simple-transaction
  (doseq [a [{:name "Checking"
              :type :asset}
             {:name "Salary"
              :type :income}]]
    (mny/put-account a))
  (let [checking (mny/find-account-by-name "Checking")
        salary (mny/find-account-by-name "Salary")]
    (mny/create-transaction {:credit-account-id (:id salary)
                             :debit-account-id (:id checking)
                             :amount 1000M}))
  (is (= 1000M (:balance (mny/find-account-by-name "Checking")))
      "The checking account balance is updated correctly")
  (is (= 1000M (:balance (mny/find-account-by-name "Salary")))
      "The salary account balance is updated correctly"))

(deftest create-multiple-transactions
  (doseq [a [{:name "Checking"
              :type :asset}
             {:name "Salary"
              :type :income}
             {:name "Rent"
              :type :expense}]]
    (mny/put-account a))
  (let [checking (mny/find-account-by-name "Checking")
        salary (mny/find-account-by-name "Salary")
        rent (mny/find-account-by-name "Rent")]
    (mny/create-transaction {:credit-account-id (:id salary)
                             :debit-account-id (:id checking)
                             :amount 1000M})
    (mny/create-transaction {:credit-account-id (:id checking)
                             :debit-account-id (:id rent)
                             :amount 500M}))
  (is (= 500M (:balance (mny/find-account-by-name "Checking")))
      "The checking account balance is updated correctly")
  (is (= 1000M (:balance (mny/find-account-by-name "Salary")))
      "The salary account balance is updated correctly")
  (is (= 500M (:balance (mny/find-account-by-name "Rent")))
      "The rend account balance is updated correctly")
  ; TODO: find transactions by account
  )
