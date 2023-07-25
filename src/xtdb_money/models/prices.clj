(ns xtdb-money.models.prices
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.util :refer [->id
                                     local-date?
                                     non-nil?]]
            [xtdb-money.core :as mny :refer [dbfn]]))

(s/def ::commodity-id non-nil?)
(s/def ::trade-date local-date?)
(s/def ::value decimal?)
(s/def ::price (s/keys :req-un [::commodity-id
                                ::trade-date
                                ::value]))

(defn- after-read
  [price]
  (mny/set-meta price :price))

(defn select
  ([criteria]         (select criteria {}))
  ([criteria options] (select (mny/storage) criteria options))
  ([db criteria options]
   {:pre [(satisfies? mny/Storage db)
          (s/valid? ::mny/options options)
          (or (:id criteria)
              (and (:commodity-id criteria)
                   (:trade-date criteria)))]}
   (map after-read
        (mny/select db
                    (mny/model-type criteria :price)
                    options))))

(dbfn find
  [db id]
  (first (select db
                 {:id (->id id)}
                 {:limit 1})))

(defn- before-save
  [price]
  (mny/model-type price :price))

(dbfn put
  [db price]
  {:pre [(s/valid? ::price price)]}

  (let [ids (mny/put db [(before-save price)])]
    (find db (first ids))))
