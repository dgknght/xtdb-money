(ns xtdb-money.models.accounts
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.core :as mny]
            [xtdb-money.accounts :refer [polarize]]
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

(s/def ::criteria (s/keys :req-un [::models/entity-id]
                          :opt-un [::name
                                   ::models/id]))

(defn select
  ([] (select {}))
  ([{:keys [name id entity-id] :as criteria}]
   {:per [(s/valid? ::criteria criteria)]}

   (let [query (cond-> {:find '[id entity-id name type balance]
                        :where '[[id :type :account]
                                 [id :account/entity-id entity-id]
                                 [id :account/name name]
                                 [id :account/type type]
                                 [id :account/balance balance]]}
                 entity-id (assoc :in '[entity-id])
                 name (assoc :in '[name])
                 id (assoc :in '[id]))]
     (map ->model (if-let [param (or name id entity-id)]
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

(defn debit
  [account amount]
  (update-in account [:balance] + (polarize amount :debit account)))

(defn credit
  [account amount]
  (update-in account [:balance] + (polarize amount :credit account)))
