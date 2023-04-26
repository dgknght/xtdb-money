(ns xtdb-money.models.accounts
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.util :refer [->id
                                     local-date?
                                     <-storable-date
                                     update-in-if]]
            [xtdb-money.core :as mny]
            [xtdb-money.models :as models]))

(s/def ::name string?)
(s/def ::type #{:asset :liability :equity :income :expense})
(s/def ::balance decimal?)
(s/def ::first-trx-date (s/nilable local-date?))
(s/def ::last-trx-date (s/nilable local-date?))
(s/def ::account (s/keys :req-un [::models/entity-id
                                  ::name
                                  ::type]
                         :opt-un [::balance
                                  ::first-trx-date
                                  ::last-trx-date]))

(defn- after-read
  [account]
  (-> account
      (with-meta {:model-type :account})
      (update-in-if [:first-trx-date] <-storable-date)
      (update-in-if [:last-trx-date] <-storable-date)))

(def ^:private query-base
  (mny/query-map :account entity-id name type balance first-trx-date last-trx-date))

(defn select
  [{:keys [id entity-id] :as criteria}]
  {:per [(or (uuid? (:id criteria))
             (uuid? (:entity-id criteria)))]}

  (let [query (cond-> query-base
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

(defn- before-save
  [account]
  (-> account
      (update-in [:first-trx-date] identity) ; force a key with nil value is absent
      (update-in [:last-trx-date] identity)
      (update-in [:balance] (fnil identity 0M))
      (vary-meta assoc :model-type :account)))

(defn put
  [account]
  {:pre [(s/valid? ::account account)]}

  (-> account
      before-save
      (mny/submit)
      find-first))
