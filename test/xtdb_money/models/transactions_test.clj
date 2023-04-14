(ns xtdb-money.models.transactions-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.test-context :refer [with-context
                                             find-entity
                                             find-account]]
            [xtdb-money.helpers :refer [reset-db]]
            [xtdb-money.models.accounts :as acts]
            [xtdb-money.models.transactions :as trxs]
            [xtdb-money.reports :as rpts]))

(use-fixtures :each reset-db)

(deftest create-a-simple-transaction
  (with-context
    (let [entity (find-entity "Personal")
          checking (find-account "Checking")
          salary (find-account "Salary")]
      (trxs/put {:entity-id (:id entity)
                 :credit-account-id (:id salary)
                 :debit-account-id (:id checking)
                 :amount 1000M})
      (is (= 1000M (:balance (acts/find (:id checking))))
          "The checking account balance is updated correctly")
      (is (= 1000M (:balance (acts/find (:id salary))))
          "The salary account balance is updated correctly")
      (is (= [{:style :header
               :label "Asset"
               :value 1000M}
              {:style :data
               :depth 0
               :label "Checking"
               :value 1000M}
              {:style :header
               :label "Liability"
               :value 0M}
              {:style :header
               :label "Equity"
               :value 1000M}
              {:style :data
               :depth 0
               :label "Retained Earnings"
               :value 1000M}]
             (rpts/balance-sheet (:id entity)))
          "A correct balance sheet is produced")
      (is (= [{:style :header
               :label "Income"
               :value 1000M}
              {:style :data
               :depth 0
               :label "Salary"
               :value 1000M}
              {:style :header
               :label "Expense"
               :value 0M}]
             (rpts/income-statement (:id entity)))
          "A correct income statement is produced"))))

(deftest create-multiple-transactions
  (with-context
    (let [entity (find-entity "Personal")
          checking (find-account "Checking")
          salary (find-account "Salary")
          rent (find-account "Rent")]
      (trxs/put {:entity-id (:id entity)
                 :credit-account-id (:id salary)
                 :debit-account-id (:id checking)
                 :amount 1000M})
      (trxs/put {:entity-id (:id entity)
                 :credit-account-id (:id checking)
                 :debit-account-id (:id rent)
                 :amount 500M})
      (is (= 500M (:balance (acts/find-by-name "Checking")))
          "The checking account balance is updated correctly")
      (is (= 1000M (:balance (acts/find-by-name "Salary")))
          "The salary account balance is updated correctly")
      (is (= 500M (:balance (acts/find-by-name "Rent")))
          "The rend account balance is updated correctly"))))

  ; TODO: find transactions by account
