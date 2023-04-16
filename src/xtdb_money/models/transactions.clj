(ns xtdb-money.models.transactions
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.core :as mny]
            [xtdb-money.models :as models]
            [xtdb-money.models.accounts :as acts])
  (:import org.joda.time.LocalDate))

(s/def ::transaction-date (partial instance? LocalDate))
(s/def ::debit-account-id uuid?)
(s/def ::credit-account-id uuid?)
(s/def ::amount (s/and decimal?
                       #(< 0M %)))
(s/def ::transaction (s/keys :req-un [::models/entity-id
                                      ::transaction-date
                                      ::credit-account-id
                                      ::debit-account-id
                                      ::amount]))

(defn- ->model
  [model]
  (as-> model m
      (zipmap [:id
               :entity-id
               :transaction-date
               :debit-account-id
               :credit-account-id
               :amount]
              m)
      (vary-meta m assoc :model-type :account)))

(defn select
  ([] (select {}))
  ([{:keys [entity-id id account-id]}]

   (let [query (cond-> {:find '[id entity-id transaction-date debit-account-id credit-account-id amount]
                        :where '[[id :type :transaction]
                                 [id :transaction/entity-id entity-id]
                                 [id :transaction/transaction-date transaction-date]
                                 [id :transaction/amount amount]
                                 [id :transaction/debit-account-id debit-account-id]
                                 [id :transaction/credit-account-id credit-account-id]]}
                 id        (assoc :in '[id])
                 entity-id (assoc :in '[entity-id])
                 account-id (assoc :in '[account-id]))]
     (map ->model (if-let [param (or id entity-id account-id)]
                    (mny/select query param)
                    (mny/select query))))))

(defn find
  [id]
  (first (select {:id id})))

(defn put
  [{:keys [debit-account-id credit-account-id amount] :as trx}]
  {:pre [(s/valid? ::transaction trx)]}

  (let [d-account (acts/find debit-account-id)
        c-account (acts/find credit-account-id)
        _ (->> [trx d-account c-account]
               (map :entity-id)
               (apply =)
               assert)
        [id] (mny/put (vary-meta trx assoc :model-type :transaction)
                      (acts/debit d-account amount)
                      (acts/credit c-account amount))]
    (find id)))
