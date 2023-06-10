(ns xtdb-money.models.accounts
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.util :refer [->id
                                     local-date?
                                     <-storable-date
                                     update-in-if]]
            [xtdb-money.core :as mny :refer [dbfn]]))

(def non-nil? (complement nil?))
(s/def ::entity-id non-nil?)
(s/def ::name string?)
(s/def ::type #{:asset :liability :equity :income :expense})
(s/def ::balance decimal?)
(s/def ::first-trx-date (s/nilable local-date?))
(s/def ::last-trx-date (s/nilable local-date?))
(s/def ::account (s/keys :req-un [::entity-id
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

(defn select
  ([criteria]         (select criteria {}))
  ([criteria options] (select (mny/storage) criteria options))
  ([db criteria options]
  {:per [(satisfies? mny/Storage db)
         (s/valid? ::mny/options options)
         ((some-fn :id :entity) criteria)]}

  (map after-read
       (mny/select db
                   (mny/model-type criteria :account)
                   options))))

(dbfn find
  [db id]
  (first (select db
                 {:id (->id id)}
                 {:limit 1})))

(defn- before-save
  [account]
  (-> account
      (update-in [:first-trx-date] identity) ; force a key with nil value is absent
      (update-in [:last-trx-date] identity)
      (update-in [:balance] (fnil identity 0M))
      (mny/model-type :account)))

(dbfn put
  [db account]
  {:pre [(s/valid? ::account account)]}

  (find db (first (mny/put db [(before-save account)]))))
