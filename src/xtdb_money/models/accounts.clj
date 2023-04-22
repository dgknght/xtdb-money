(ns xtdb-money.models.accounts
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.util :refer [->id]]
            [xtdb-money.core :as mny]
            [xtdb-money.models :as models]))

(s/def ::name string?)
(s/def ::type #{:asset :liability :equity :income :expense})
(s/def ::balance decimal?)
(s/def ::account (s/keys :req-un [::models/entity-id
                                  ::name
                                  ::type]
                         :opt-un [::balance]))

(defn- after-read
  [account]
  (with-meta account {:model-type :account}))

(defn select
  [{:keys [id entity-id] :as criteria}]
  {:per [(or (uuid? (:id criteria))
             (uuid? (:entity-id criteria)))]}

  (let [query (cond-> (mny/query-map :account entity-id name type balance)
                entity-id (assoc :in '[entity-id])
                id (assoc :in '[id]))]
    (map after-read
         (mny/select query (or id entity-id)))))

(defn find
  [id]
  (first (select {:id (->id id)})))

(defn- find-first
  [[id]]
  (find id))

(defn put
  [account]
  {:pre [(s/valid? ::account account)]}

  (-> account
      (update-in [:balance] (fnil identity 0M))
      (vary-meta assoc :model-type :account)
      (mny/put)
      find-first))
