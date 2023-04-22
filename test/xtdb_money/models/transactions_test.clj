(ns xtdb-money.models.transactions-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]
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

(defn- format-money
  [m]
  (format "%.2f" m))

(defn- format-date
  [d]
  (tf/unparse (tf/formatters :date) (tc/to-date-time d)))

(defn- mapify
  "Accept a UnilateralTransaction and return a map of the attributes"
  [t]
  {:index (trxs/index t)
   :amount (format-money (trxs/amount t))
   :balance (format-money (trxs/balance t))
   :transaction-date (format-date (trxs/transaction-date t))
   :other-account (:name (trxs/other-account t))})

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
      (testing "return value"
        (is (comparable? attr result)
            "The correct attributes are returned"))
      (testing "transaction query by account"
        (is (seq-of-maps-like? [{:index 1
                                 :amount "1000.00"
                                 :balance "1000.00"
                                 :transaction-date "2000-01-01"}]
                               (map mapify
                                    (trxs/select-by-account
                                      checking
                                      (t/local-date 2000 1 1)
                                      (t/local-date 2000 2 1))))
            "The transaction is included in the debit account query")
        (is (seq-of-maps-like? [{:index 1
                                 :amount "1000.00"
                                 :balance "1000.00"
                                 :transaction-date "2000-01-01"}]
                               (map mapify
                                    (trxs/select-by-account
                                      salary
                                      (t/local-date 2000 1 1)
                                      (t/local-date 2000 2 1))))
            "The transaction is included in the credit account query"))
      (testing "account updates"
        (is (= 1000M (:balance (acts/find (:id checking))))
            "The checking account balance is updated correctly")
        (is (= 1000M (:balance (acts/find (:id salary))))
            "The salary account balance is updated correctly"))
      (testing "reports"
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
               (rpts/balance-sheet {:entity-id (:id entity)
                                    :as-of (t/local-date 2000 12 31)}))
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
               (rpts/income-statement {:entity-id (:id entity)
                                       :start-date (t/local-date 2000 1 1)
                                       :end-date (t/local-date 2001 1 1)}))
            "A correct income statement is produced")))))

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
      (is (= 500M (:balance (acts/find (:id (find-account "Checking")))))
          "The checking account balance is updated correctly")
      (is (= 1000M (:balance (acts/find (:id (find-account "Salary")))))
          "The salary account balance is updated correctly")
      (is (= 500M (:balance (acts/find (:id (find-account "Rent")))))
          "The rent account balance is updated correctly"))

    (testing "transactions can be retrieved by account"
      (is (seq-of-maps-like? [{:transaction-date "2000-01-01"
                               :index 1
                               :amount "1000.00"
                               :balance "1000.00"}
                              {:transaction-date "2000-01-02"
                               :index 2
                               :amount "-500.00"
                               :balance "500.00"}]
                             (map mapify
                                  (trxs/select-by-account
                                    (find-account "Checking")
                                    (t/local-date 2000 1 1)
                                    (t/local-date 2000 2 1))))
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
             (rpts/balance-sheet {:entity-id (:id (find-entity "Personal"))
                                  :as-of (t/local-date 2000 12 31)}))
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
             (rpts/income-statement {:entity-id (:id (find-entity "Personal"))
                                     :start-date (t/local-date 2000 1 1)
                                     :end-date (t/local-date 2001 1 1)}))
          "A correct income statement is produced"))))

(def ^:private insert-before-context
  (assoc basic-context
         :transactions [{:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 1)
                         :credit-account-id "Salary"
                         :debit-account-id "Checking"
                         :amount 1000M}
                        {:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 2)
                         :credit-account-id "Checking"
                         :debit-account-id "Groceries"
                         :amount 50M}
                        {:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 3)
                         :credit-account-id "Checking"
                         :debit-account-id "Dining"
                         :amount 20M}
                        {:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 2)
                         :credit-account-id "Checking"
                         :debit-account-id "Rent"
                         :amount 500M}]))

(deftest insert-a-transaction-before-another
  (with-context insert-before-context
    (is (seq-of-maps-like? [{:transaction-date "2000-01-01"
                             :other-account "Salary"
                             :index 1
                             :amount "1000.00"
                             :balance "1000.00"}
                            {:transaction-date "2000-01-02"
                             :other-account "Rent"
                             :index 2
                             :amount "-500.00"
                             :balance "500.00"}
                            {:transaction-date "2000-01-02"
                             :other-account "Groceries"
                             :index 3
                             :amount "-50.00"
                             :balance "450.00"}
                            {:transaction-date "2000-01-03"
                             :other-account "Dining"
                             :index 4
                             :amount "-20.00"
                             :balance "430.00"}]
                           (->> (trxs/select-by-account
                                  (find-account "Checking")
                                  (t/local-date 2000 1 1)
                                  (t/local-date 2000 2 1))
                                (map mapify)))
        "The indexes and balances are updated up the chain")
    (is (= 430M
           (:balance (acts/find (find-account "Checking"))))
        "The account balance is updated.")))

; TODO: add a complex transaction, like a paycheck, with taxes, etc.
; TODO: add transaction description
; TODO: add reports test ns and get reports with explicit dates
; TODO: set the first transaction and last transaction dates on the account
