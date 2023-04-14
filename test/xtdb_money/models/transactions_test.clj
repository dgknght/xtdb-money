(ns xtdb-money.models.transactions-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.test-context :refer [with-context
                                             find-account]]
            [xtdb-money.helpers :refer [reset-db]]
            [xtdb-money.models.accounts :as acts]
            [xtdb-money.models.transactions :as trxs]))

(use-fixtures :each reset-db)

(deftest create-a-simple-transaction
  (with-context
    (let [checking (find-account "Checking")
          salary (find-account "Salary")]
      (trxs/put {:credit-account-id (:id salary)
                 :debit-account-id (:id checking)
                 :amount 1000M})
      (is (= 1000M (:balance (acts/find (:id checking))))
          "The checking account balance is updated correctly")
      (is (= 1000M (:balance (acts/find (:id salary))))
          "The salary account balance is updated correctly"))))

(deftest create-multiple-transactions
  (with-context
    (let [checking (find-account "Checking")
          salary (find-account "Salary")
          rent (find-account "Rent")]
      (trxs/put {:credit-account-id (:id salary)
                 :debit-account-id (:id checking)
                 :amount 1000M})
      (trxs/put {:credit-account-id (:id checking)
                 :debit-account-id (:id rent)
                 :amount 500M})
      (is (= 500M (:balance (acts/find-by-name "Checking")))
          "The checking account balance is updated correctly")
      (is (= 1000M (:balance (acts/find-by-name "Salary")))
          "The salary account balance is updated correctly")
      (is (= 500M (:balance (acts/find-by-name "Rent")))
          "The rend account balance is updated correctly"))))

  ; TODO: find transactions by account
