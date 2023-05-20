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
      (mny/model-type :account)
      (update-in-if [:first-trx-date] <-storable-date)
      (update-in-if [:last-trx-date] <-storable-date)))

(defmulti query mny/storage-dispatch)
(defmulti submit mny/storage-dispatch)

(defn select
  [criteria]
  {:per [(or (uuid? (:id criteria))
             (uuid? (:entity-id criteria)))]}

  (map after-read
         (query criteria)))

(defn find
  [id]
  (first (select {:id (->id id)})))

(defn- before-save
  [account]
  (-> account
      (update-in [:first-trx-date] identity) ; force a key with nil value is absent
      (update-in [:last-trx-date] identity)
      (update-in [:balance] (fnil identity 0M))
      (mny/model-type :account)))

(defn put
  [account]
  {:pre [(s/valid? ::account account)]}

  (-> account
      before-save
      submit
      first
      find))
