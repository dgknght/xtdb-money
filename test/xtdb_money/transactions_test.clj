(ns xtdb-money.transactions-test
  (:require [clojure.test :refer [deftest testing is]]
            [clj-time.core :as t]
            [xtdb-money.transactions :as trx]))

(def checking 101)
(def salary   201)
(def fit      301)
(def soc-sec  302)
(def medicare 303)
(def rent     304)

(def ^:private complex
  {:transaction-date (t/local-date 2000 1 1)
   :description "Paycheck"
   :items #{{:action :credit
             :account-id salary
             :quantity 1000M}
            {:action :debit
             :account-id fit
             :quantity 100M}
            {:action :debit
             :account-id soc-sec
             :quantity 6.2M}
            {:action :debit
             :account-id medicare
             :quantity 0.45M}
            {:action :debit
             :account-id checking
             :quantity 893.35M}}})

(def ^:private trxs
  #{{:transaction-date (t/local-date 2000 1 1)
     :debit-account-id medicare
     :credit-account-id salary
     :description "Paycheck"
     :quantity 0.45M}
    {:transaction-date (t/local-date 2000 1 1)
     :debit-account-id soc-sec
     :credit-account-id salary
     :description "Paycheck"
     :quantity 6.2M}
    {:transaction-date (t/local-date 2000 1 1)
     :debit-account-id fit
     :credit-account-id salary
     :description "Paycheck"
     :quantity 100M}
    {:transaction-date (t/local-date 2000 1 1)
     :debit-account-id checking
     :credit-account-id salary
     :description "Paycheck"
     :quantity 893.35M}})

(deftest convert-a-complex-transaction-into-simple-transactions
  (testing "One to one"
    (is (= [{:transaction-date (t/local-date 2000 1 1)
             :debit-account-id rent
             :credit-account-id checking
             :description "Landlord"
             :quantity 100M}]
           (seq (trx/split {:transaction-date (t/local-date 2000 1 1)
                            :description "Landlord"
                            :items #{{:action :credit
                                      :quantity 100M
                                      :account-id checking}
                                     {:action :debit
                                      :quantity 100M
                                      :account-id rent}}})))))
  (testing "Uneven distribution"
    (is (= trxs
           (trx/split complex)))))

(deftest join-simple-transactions
  (is (= complex
         (trx/join trxs))))

(deftest round-trip
  (is (= complex
         (-> complex
             trx/split
             trx/join))
      "A complex transaction makes the round trip successfully")
  (is (= trxs
         (-> trxs
             trx/join
             trx/split))
      "A series of simple transactions makes the round trip successfully"))
