(ns xtdb-money.models.transactions
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.util :refer [->storable-date
                                     <-storable-date]]
            [xtdb-money.core :as mny]
            [xtdb-money.accounts :as a]
            [xtdb-money.models :as mdls]
            [xtdb-money.models.accounts :as acts])
  (:import org.joda.time.LocalDate))

(s/def ::transaction-date (partial instance? LocalDate))
(s/def ::debit-account-id uuid?)
(s/def ::credit-account-id uuid?)
(s/def ::amount (s/and decimal?
                       #(< 0M %)))
(s/def ::transaction (s/keys :req-un [::mdls/entity-id
                                      ::transaction-date
                                      ::credit-account-id
                                      ::debit-account-id
                                      ::amount]
                             :opt-un [::mdls/id]))

(s/def ::start-date (partial instance? LocalDate))
(s/def ::end-date (partial instance? LocalDate))

(defn- criteria-dispatch
  [& args]
  (let [{:keys [entity-id account-id id]} (last args)]
    (cond
      id :id
      entity-id :entity-id
      account-id :account-id)))

(defmulti ^:private criteria criteria-dispatch)
(defmethod criteria :id
  [_]
  (s/keys :req-un [::mdls/id]))
(defmethod criteria :account-id
  [_]
  (s/keys :req-un [::account-id
                   ::start-date
                   ::end-date]))
(defmethod criteria :entity-id
  [_]
  (s/keys :req-un [::entity-id
                   ::start-date
                   ::end-date]))

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

(s/def ::criteria (s/multi-spec criteria :account-id))

(s/def ::offset integer?)
(s/def ::limit integer?)
(s/def ::options (s/keys :opt-un [::offset ::limit]))

(defmulti ^:private apply-criteria criteria-dispatch)

(defmethod apply-criteria :id
  [query {:keys [id]}]
  [(assoc query :in '[id])
   id])

(defn- apply-date-range
  [query]
  (-> query
      (update-in [:where] conj '[(<= start-date transaction-date)])
      (update-in [:where] conj '[(< transaction-date end-date)])))

(defmethod apply-criteria :account-id
  [query {:keys [account-id] :as criteria}]
  [(-> query
       (assoc :in '[[account-id start-date end-date]])
       (apply-date-range))
   (cons account-id (map (comp ->storable-date criteria)
                         [:start-date :end-date]))])

(defmethod apply-criteria :entity-id
  [query {:keys [entity-id] :as criteria}]
  [(-> query
       (assoc :in '[[entity-id start-date end-date]])
       (apply-date-range))
   (cons entity-id (map (comp ->storable-date criteria)
                        [:start-date :end-date]))])

(defn- apply-options
  [query {:keys [limit offset]}]
  (cond-> query
    limit (assoc :limit limit)
    offset (assoc :offset offset)))

(defn- after-read
  [trx]
  (update-in trx [:transaction-date] <-storable-date))

(defn select
  ([] (select {}))
  ([criteria] (select criteria {}))
  ([criteria options]
   {:pre [(s/valid? ::criteria criteria)
          (s/valid? ::options options)]}
   (let [[query param] (-> (mny/query-map :transaction
                                          entity-id
                                          transaction-date
                                          debit-account-id
                                          credit-account-id
                                          amount)
                           (assoc :order-by [['transaction-date :asc]])
                           (apply-options options)
                           (apply-criteria criteria))]
     (map after-read (mny/select query param)))))

(defn- split
  "Accepts a storable transaction and returns two split transactions"
  [{:keys [amount
           debit-account-id
           credit-account-id
           transaction-date]}]
  [{:account-id debit-account-id
    :transaction-date transaction-date
    :action :debit
    :amount amount}
   {:account-id credit-account-id
    :transaction-date transaction-date
    :action :credit
    :amount amount}])

(defn select-by-account
  [account start-date end-date]
  (->> (select {:account-id (:id account)
                :start-date start-date
                :end-date end-date})
       (mapcat split)
       (filter #(= (:id account)
                   (:account-id %)))
       (map (comp #(assoc % :amount (a/polarize %))
                  #(assoc % :account account)))))

(defn find
  [id]
  (first (select {:id id})))

(defn- before-save
  [trx]
  (-> trx
      (vary-meta assoc :model-type :transaction)
      (update-in [:transaction-date] ->storable-date)))

(defn put
  [{:keys [debit-account-id credit-account-id amount] :as trx}]
  {:pre [(s/valid? ::transaction trx)]}

  (let [d-account (acts/find debit-account-id)
        c-account (acts/find credit-account-id)
        _ (->> [trx d-account c-account]
               (map :entity-id)
               (apply =)
               assert)
        [id] (mny/put (before-save trx)
                      (a/debit d-account amount)
                      (a/credit c-account amount))]
    (find id)))
