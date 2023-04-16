(ns xtdb-money.models.transactions-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [clj-time.core :as t]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.test-context :refer [with-context
                                             basic-context
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
          salary (find-account "Salary")
          attr {:transaction-date (t/local-date 2000 1 1)
                 :entity-id (:id entity)
                 :credit-account-id (:id salary)
                 :debit-account-id (:id checking)
                 :amount 1000M}
          result (trxs/put attr)]
      (is (comparable? attr result)
          "The correct attributes are returned")
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

(def ^:private multi-context
  (assoc basic-context
         :transactions [{:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 1)
                         :credit-account-id "Salary"
                         :debit-account-id "Checking"
                         :amount 1000M}
                        {:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 2)
                         :credit-account-id "Checking"
                         :debit-account-id "Rent"
                         :amount 500M}
                        {:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 2)
                         :credit-account-id "Credit Card"
                         :debit-account-id "Groceries"
                         :amount 50M}]))
(deftest create-multiple-transactions
  (with-context multi-context
    (testing "account balances are set"
      (is (= 500M (:balance (acts/find-by-name "Checking")))
          "The checking account balance is updated correctly")
      (is (= 1000M (:balance (acts/find-by-name "Salary")))
          "The salary account balance is updated correctly")
      (is (= 500M (:balance (acts/find-by-name "Rent")))
          "The rent account balance is updated correctly"))

    (testing "transactions can be retrieved by account"
      (is (seq-of-maps-like? [{:transaction-date (t/local-date 2000 1 1)
                               :index 1
                               :amount 1000M
                               :balance 1000M}
                              {:transaction-date (t/local-date 2000 1 2)
                               :index 2
                               :amount -500M
                               :balance 500M}
                              {:transaction-date (t/local-date 2000 1 31)
                               :index 3
                               :amount -50
                               :balance 500M}]
                             (trxs/select {:account-id (:id (find-account "Checking"))
                                           :start-date (t/local-date 2000 1 1)
                                           :end-date (t/local-date 2000 2 1)}))
          "The correct list of transactions is returned"))
    (testing "reports are correct"
      (is (= [{:style :header
               :label "Asset"
               :value 500M}
              {:style :data
               :depth 0
               :label "Checking"
               :value 500M}
              {:style :header
               :label "Liability"
               :value 50M}
              {:style :data
               :depth 0
               :label "Credit Card"
               :value 50M}
              {:style :header
               :label "Equity"
               :value 450M}
              {:style :data
               :depth 0
               :label "Retained Earnings"
               :value 450M}]
             (rpts/balance-sheet (:id (find-entity "Personal"))))
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
               :value 550M}
              {:style :data
               :depth 0
               :label "Groceries"
               :value 50M}
              {:style :data
               :depth 0
               :label "Rent"
               :value 500M}]
             (rpts/income-statement (:id (find-entity "Personal"))))
          "A correct income statement is produced"))))

; TODO: find transactions by account, they should be in order and reflect the transaction amount and reulsing blance
; TODO: add a transaction to the start or middle of a list of existing transactions
; TODO: add a complex transaction, like a paycheck, with taxes, etc.
; TODO: add transaction description
; TODO: add reports test ns and get reports with explicit dates
