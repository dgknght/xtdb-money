(ns xtdb-money.models.transactions-test
  (:require [clojure.test :refer [deftest is are use-fixtures testing]]
            [clojure.spec.alpha :as s]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [dgknght.app-lib.test-assertions]
            [xtdb-money.core :as mny]
            [xtdb-money.test-context :refer [with-context
                                             basic-context
                                             find-entity
                                             find-account
                                             find-transaction]]
            [xtdb-money.helpers :refer [reset-db
                                        dbtest
                                        *strategy*]]
            [xtdb-money.models.accounts :as acts]
            [xtdb-money.models.transactions :as trxs]
            [xtdb-money.reports :as rpts]
            [xtdb-money.models.mongodb.ref]
            [xtdb-money.models.sql.ref]
            [xtdb-money.models.xtdb.ref]
            [xtdb-money.models.datomic.ref]))

(use-fixtures :each reset-db)

(deftest validate-search-criteria
  (are [c] (empty? (s/explain-data ::trxs/criteria c))
       {:account-id 1
        :transaction-date (t/local-date 2020 1 1)}
       {:account-id 1
        :transaction-date [:< (t/local-date 2020 1 1)]}
       {:account-id 1
        :transaction-date [:<= (t/local-date 2020 1 1)]}
       {:account-id 1
        :transaction-date [:> (t/local-date 2020 1 1)]}
       {:account-id 1
        :transaction-date [:>= (t/local-date 2020 1 1)]}
       {:account-id 1
        :transaction-date [:and
                           [:>= (t/local-date 2020 1 1)]
                           [:< (t/local-date 20201 1 1)]]}
       {:entity-id 1
        :transaction-date (t/local-date 2020 1 1)}
       {:entity-id 1
        :transaction-date [:< (t/local-date 2020 1 1)]}
       {:entity-id 1
        :transaction-date [:<= (t/local-date 2020 1 1)]}
       {:entity-id 1
        :transaction-date [:> (t/local-date 2020 1 1)]}
       {:entity-id 1
        :transaction-date [:>= (t/local-date 2020 1 1)]}
       {:entity-id 1
        :transaction-date [:and
                           [:>= (t/local-date 2020 1 1)]
                           [:< (t/local-date 20201 1 1)]]}))

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
   :description (trxs/description t)
   :other-account (:name (trxs/other-account t))})

(dbtest create-a-simple-transaction
  (with-context
    (let [entity (find-entity "Personal")
          checking (find-account "Checking")
          salary (find-account "Salary")
          attr {:transaction-date (t/local-date 2000 1 1)
                :correlation-id (random-uuid)
                :description "Paycheck"
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
                                 :description "Paycheck"
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
                                 :description "Paycheck"
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
        (is (= {:balance 1000M
                :first-trx-date (t/local-date 2000 1 1)
                :last-trx-date (t/local-date 2000 1 1)}
               (select-keys (acts/find (:id checking))
                            [:balance :first-trx-date :last-trx-date]))
            "The checking account balance is updated correctly")
        (is (= {:balance 1000M
                :first-trx-date (t/local-date 2000 1 1)
                :last-trx-date (t/local-date 2000 1 1)}
               (select-keys (acts/find (:id salary))
                            [:balance :first-trx-date :last-trx-date]))
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
                         :description "Paycheck"
                         :credit-account-id "Salary"
                         :debit-account-id "Checking"
                         :amount 1000M}
                        {:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 2)
                         :description "The Landlord"
                         :credit-account-id "Checking"
                         :debit-account-id "Rent"
                         :amount 500M}
                        {:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 2)
                         :credit-account-id "Credit Card"
                         :description "Kroger"
                         :debit-account-id "Groceries"
                         :amount 50M}]))

(dbtest create-multiple-transactions
  (with-context multi-context
    (testing "account balances are set"
      (is (= {:balance 500M
              :first-trx-date (t/local-date 2000 1 1)
              :last-trx-date (t/local-date 2000 1 2)}
             (select-keys (acts/find (:id (find-account "Checking")))
                          [:balance :first-trx-date :last-trx-date]))
          "The checking account balance is updated correctly")
      (is (= {:balance 1000M
              :first-trx-date (t/local-date 2000 1 1)
              :last-trx-date (t/local-date 2000 1 1)}
             (select-keys (acts/find (:id (find-account "Salary")))
                          [:balance :first-trx-date :last-trx-date]))
          "The salary account balance is updated correctly")
      (is (= {:balance 500M
              :first-trx-date (t/local-date 2000 1 2)
              :last-trx-date (t/local-date 2000 1 2)}
             (select-keys (acts/find (:id (find-account "Rent")))
                          [:balance :first-trx-date :last-trx-date]))
          "The rent account balance is updated correctly")
      (is (= {:balance 50M
              :first-trx-date (t/local-date 2000 1 2)
              :last-trx-date (t/local-date 2000 1 2)}
             (select-keys (acts/find (:id (find-account "Groceries")))
                          [:balance :first-trx-date :last-trx-date]))
          "The groceries account balance is updated correctly")
      (is (= {:balance 50M
              :first-trx-date (t/local-date 2000 1 2)
              :last-trx-date (t/local-date 2000 1 2)}
             (select-keys (acts/find (:id (find-account "Credit Card")))
                          [:balance :first-trx-date :last-trx-date]))
          "The credit card account balance is updated correctly"))

    (testing "transactions can be retrieved by account"
      (is (seq-of-maps-like? [{:transaction-date "2000-01-01"
                               :index 1
                               :description "Paycheck"
                               :amount "1000.00"
                               :balance "1000.00"}
                              {:transaction-date "2000-01-02"
                               :index 2
                               :description "The Landlord"
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
                         :description "Paycheck"
                         :debit-account-id "Checking"
                         :amount 1000M}
                        {:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 2)
                         :description "Kroger"
                         :credit-account-id "Checking"
                         :debit-account-id "Groceries"
                         :amount 50M}
                        {:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 3)
                         :description "Nice Restaurant"
                         :credit-account-id "Checking"
                         :debit-account-id "Dining"
                         :amount 20M}
                        {:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 2)
                         :description "The Landlord"
                         :credit-account-id "Checking"
                         :debit-account-id "Rent"
                         :amount 500M}]))

(dbtest insert-a-transaction-before-another
  (with-context insert-before-context
    (is (seq-of-maps-like? [{:transaction-date "2000-01-01"
                             :other-account "Salary"
                             :description "Paycheck"
                             :index 1
                             :amount "1000.00"
                             :balance "1000.00"}
                            {:transaction-date "2000-01-02"
                             :other-account "Rent"
                             :description "The Landlord"
                             :index 2
                             :amount "-500.00"
                             :balance "500.00"}
                            {:transaction-date "2000-01-02"
                             :other-account "Groceries"
                             :description "Kroger"
                             :index 3
                             :amount "-50.00"
                             :balance "450.00"}
                            {:transaction-date "2000-01-03"
                             :other-account "Dining"
                             :description "Nice Restaurant"
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

(def ^:private delete-context
  (assoc basic-context
         :transactions [{:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 1)
                         :description "Paycheck"
                         :credit-account-id "Salary"
                         :debit-account-id "Checking"
                         :amount 1000M}
                        {:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 2)
                         :description "The Landlord"
                         :credit-account-id "Checking"
                         :debit-account-id "Rent"
                         :amount 500M}
                        {:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 3)
                         :credit-account-id "Checking"
                         :description "Kroger"
                         :debit-account-id "Groceries"
                         :amount 50M}]))

(dbtest delete-a-transaction
  (with-context delete-context
    (let [trx (find-transaction (t/local-date 2000 1 2) "The Landlord")]
      (trxs/delete trx)
      (testing "the transaction is unavailable after delete"
        (is (nil? (trxs/find (:id trx))))))
    (testing "affected transactions are updated"
      (is (seq-of-maps-like? [{:transaction-date "2000-01-01"
                               :index 1
                               :description "Paycheck"
                               :amount "1000.00"
                               :balance "1000.00"}
                              {:transaction-date "2000-01-03"
                               :index 2
                               :description "Kroger"
                               :amount "-50.00"
                               :balance "950.00"}]
                             (map mapify
                                  (trxs/select-by-account
                                    (find-account "Checking")
                                    (t/local-date 2000 1 1)
                                    (t/local-date 2000 2 1))))
          "The correct list of transactions is returned"))
    (testing "account balances are set"
      (is (comparable? {:balance 950M
                        :first-trx-date (t/local-date 2000 1 1)
                        :last-trx-date (t/local-date 2000 1 3)}
                       (acts/find (:id (find-account "Checking"))))
          "The checking account balance is updated correctly")
      (let [rent (acts/find (:id (find-account "Rent")))]
        (is (= 0M (:balance rent))
            "The rent balance is zeroed out.")
        (is (nil? (:first-trx-date rent))
            "The :first-trx-date is unset")
        (is (nil? (:last-trx-date rent))
            "The :last-trx-date is unset"))
      (is (comparable? {:balance 50M
                        :first-trx-date (t/local-date 2000 1 3)
                        :last-trx-date (t/local-date 2000 1 3)}
                       (acts/find (:id (find-account "Groceries"))))
          "The groceries account balance is updated correctly"))
    (testing "reports are correct"
      (is (= [{:style :header
               :label "Asset"
               :value 950M}
              {:style :data
               :depth 0
               :label "Checking"
               :value 950M}
              {:style :header
               :label "Liability"
               :value 0M}
              {:style :header
               :label "Equity"
               :value 950M}
              {:style :data
               :depth 0
               :label "Retained Earnings"
               :value 950M}]
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
               :value 50M}
              {:style :data
               :depth 0
               :label "Groceries"
               :value 50M}]
             (rpts/income-statement {:entity-id (:id (find-entity "Personal"))
                                     :start-date (t/local-date 2000 1 1)
                                     :end-date (t/local-date 2001 1 1)}))
          "A correct income statement is produced"))))

(def ^:private prop-context
  (assoc basic-context
         :transactions [{:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 1)
                         :description "Paycheck"
                         :credit-account-id "Salary"
                         :debit-account-id "Checking"
                         :amount 1000M}
                        {:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 3)
                         :description "The Landlord"
                         :credit-account-id "Checking"
                         :debit-account-id "Rent"
                         :amount 500M}
                        {:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 4)
                         :credit-account-id "Checking"
                         :description "Kroger"
                         :debit-account-id "Groceries"
                         :amount 50M}
                        {:entity-id "Personal"
                         :transaction-date (t/local-date 2000 1 9)
                         :credit-account-id "Checking"
                         :description "Kroger"
                         :debit-account-id "Groceries"
                         :amount 51M}]))

(dbtest shortcut-propagation 
  (with-context prop-context
    (let [trx (find-transaction (t/local-date 2000 1 4)
                                "Kroger")
          submissions (atom [])
          orig-submit trxs/put]
      (with-redefs [trxs/put (fn [& args]
                                  (swap! submissions conj args)
                                  (apply orig-submit args))]
        (trxs/put (assoc trx :transaction-date (t/local-date 2000 1 2))))
      (testing "transaction propogation"
        (is (seq-of-maps-like? [{:transaction-date "2000-01-01"
                                 :index 1
                                 :description "Paycheck"
                                 :amount "1000.00"
                                 :balance "1000.00"}
                                {:transaction-date "2000-01-02"
                                 :index 2
                                 :description "Kroger"
                                 :amount "-50.00"
                                 :balance "950.00"}
                                {:transaction-date "2000-01-03"
                                 :index 3
                                 :description "The Landlord"
                                 :amount "-500.00"
                                 :balance "450.00"}
                                {:transaction-date "2000-01-09"
                                 :index 4
                                 :description "Kroger"
                                 :amount "-51.00"
                                 :balance "399.00"}]
                               (map mapify
                                    (trxs/select-by-account
                                      (find-account "Checking")
                                      (t/local-date 2000 1 1)
                                      (t/local-date 2000 2 1))))
            "The transactions are updated correctly"))
      (testing "Unaffected models are not saved"
        (is (= (filter #(= (t/local-date 2000 1 9)
                           (:transaction-date %))
                       (apply concat @submissions))
               [])
            "The first unaffected transaction is not updated.")
        (is (= (filter #(and (= :account
                                (mny/model-type %))
                             (= "Checking"
                                (:name %)))
                       (apply concat @submissions))
               [])
            "The checking account is not updated"))
      (testing "account balances are set"
        (is (= {:balance 399M
                :first-trx-date (t/local-date 2000 1 1)
                :last-trx-date (t/local-date 2000 1 9)}
               (select-keys (acts/find (:id (find-account "Checking")))
                            [:balance :first-trx-date :last-trx-date]))
            "The checking account balance is updated correctly")
        (is (= {:balance 101M
                :first-trx-date (t/local-date 2000 1 2)
                :last-trx-date (t/local-date 2000 1 9)}
               (select-keys (acts/find (:id (find-account "Groceries")))
                            [:balance :first-trx-date :last-trx-date]))
            "The groceries account balance is updated correctly"))
      (testing "reports are correct"
        (is (= [{:style :header
                 :label "Asset"
                 :value 399M}
                {:style :data
                 :depth 0
                 :label "Checking"
                 :value 399M}
                {:style :header
                 :label "Liability"
                 :value 0M}
                {:style :header
                 :label "Equity"
                 :value 399M}
                {:style :data
                 :depth 0
                 :label "Retained Earnings"
                 :value 399M}]
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
                 :value 601M}
                {:style :data
                 :depth 0
                 :label "Groceries"
                 :value 101M}
                {:style :data
                 :depth 0
                 :label "Rent"
                 :value 500M}]
               (rpts/income-statement {:entity-id (:id (find-entity "Personal"))
                                       :start-date (t/local-date 2000 1 1)
                                       :end-date (t/local-date 2001 1 1)}))
            "A correct income statement is produced")))))

; TODO: add a complex transaction, like a paycheck, with taxes, etc.
; TODO: add reports test ns and get reports with explicit dates
