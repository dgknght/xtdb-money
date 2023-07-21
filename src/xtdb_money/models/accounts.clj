(ns xtdb-money.models.accounts
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [dgknght.app-lib.core :refer [index-by]]
            [xtdb-money.util :refer [->id
                                     local-date?
                                     non-nil?]]
            [xtdb-money.core :as mny :refer [dbfn]]))

(s/def ::entity-id non-nil?)
(s/def ::commodity-id non-nil?)
(s/def ::name string?)
(s/def ::type #{:asset :liability :equity :income :expense})
(s/def ::balance decimal?)
(s/def ::first-trx-date (s/nilable local-date?))
(s/def ::last-trx-date (s/nilable local-date?))
(s/def ::account (s/keys :req-un [::entity-id
                                  ::commodity-id
                                  ::name
                                  ::type]
                         :opt-un [::balance
                                  ::first-trx-date
                                  ::last-trx-date]))

(defn- after-read
  [account]
  (mny/set-meta account :account))

(defn select
  ([criteria]         (select criteria {}))
  ([criteria options] (select (mny/storage) criteria options))
  ([db criteria options]
   {:pre [(satisfies? mny/Storage db)
          (s/valid? ::mny/options options)
          ((some-fn :id :entity-id) criteria)]}
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
      (update-in [:balance] (fnil identity 0M))
      (mny/model-type :account)))

(dbfn put
  [db account]
  {:pre [(s/valid? ::account account)]}

  (let [ids (mny/put db [(before-save account)])]
    (find db (first ids))))
