(ns xtdb-money.models.accounts
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.core :as mny]
            [xtdb-money.models :as models]))

(s/def ::name string?)
(s/def ::type #{:asset :liability :equity :income :expense})
(s/def ::balance decimal?)
(s/def ::account (s/keys :req-un [::models/entity-id
                                  ::name
                                  ::type]
                         :opt-un [::balance]))

(defn- ->model
  [model]
  (as-> model m
      (zipmap [:id :entity-id :name :type :balance] m)
      (vary-meta m assoc :model-type :account)))

(defn select
  ([] (select {}))
  ([{:keys [name id]}]
   (let [query (cond-> {:find '[id entity-id name type balance]
                        :where '[[id :type :account]
                                 [id :account/entity-id entity-id]
                                 [id :account/name name]
                                 [id :account/type type]
                                 [id :account/balance balance]]}
                 name (assoc :in '[name])
                 id (assoc :in '[id]))]
     (map ->model (if-let [param (or name id)]
                    (mny/select query param)
                    (mny/select query))))))

(defn find
  [id]
  (first (select {:id id})))

(defn find-by-name
  [account-name]
  (first (select {:name account-name})))

(defn- find-first
  [[id]] (find id))

(defn put
  [account]
  {:pre [(s/valid? ::account account)]}

  (-> account
      (update-in [:balance] (fnil identity 0M))
      (vary-meta assoc :model-type :account)
      (mny/put)
      find-first))

(defn- left-side?
  [{:keys [type]}]
  (#{:asset :expense} type))

(defn debit
  [account amount]
  (let [f (if (left-side? account) + -)]
    (update-in account [:balance] f amount)))

(defn credit
  [account amount]
  (let [f (if (left-side? account) - +)]
    (update-in account [:balance] f amount)))
