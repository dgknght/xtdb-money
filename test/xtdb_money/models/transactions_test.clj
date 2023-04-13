(ns xtdb-money.models.transactions-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.helpers :refer [reset-db]]
            [xtdb-money.models.accounts :as acts]
            [xtdb-money.models.transactions :as trxs]))

(use-fixtures :each reset-db)

(deftest create-a-simple-transaction
  (doseq [a [{:name "Checking"
              :type :asset}
             {:name "Salary"
              :type :income}]]
    (acts/put a))
  (let [checking (acts/find-by-name "Checking")
        salary (acts/find-by-name "Salary")]
    (trxs/put {:credit-account-id (:id salary)
                             :debit-account-id (:id checking)
                             :amount 1000M}))
  (is (= 1000M (:balance (acts/find-by-name "Checking")))
      "The checking account balance is updated correctly")
  (is (= 1000M (:balance (acts/find-by-name "Salary")))
      "The salary account balance is updated correctly"))

(deftest create-multiple-transactions
  (doseq [a [{:name "Checking"
              :type :asset}
             {:name "Salary"
              :type :income}
             {:name "Rent"
              :type :expense}]]
    (acts/put a))
  (let [checking (acts/find-by-name "Checking")
        salary (acts/find-by-name "Salary")
        rent (acts/find-by-name "Rent")]
    (trxs/put {:credit-account-id (:id salary)
                             :debit-account-id (:id checking)
                             :amount 1000M})
    (trxs/put {:credit-account-id (:id checking)
                             :debit-account-id (:id rent)
                             :amount 500M}))
  (is (= 500M (:balance (acts/find-by-name "Checking")))
      "The checking account balance is updated correctly")
  (is (= 1000M (:balance (acts/find-by-name "Salary")))
      "The salary account balance is updated correctly")
  (is (= 500M (:balance (acts/find-by-name "Rent")))
      "The rend account balance is updated correctly")
  ; TODO: find transactions by account
  )
