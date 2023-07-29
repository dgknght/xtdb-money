(ns xtdb-money.models.prices
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.util :refer [->id
                                     local-date?
                                     valid-id?
                                     <-storable-date]]
            [xtdb-money.core :as mny :refer [dbfn]]))

(s/def ::commodity-id valid-id?)
(s/def ::trade-date local-date?)
(s/def ::value decimal?)
(s/def ::price (s/keys :req-un [::commodity-id
                                ::trade-date
                                ::value]))

(s/def ::criteria (s/keys :opt-un [::commodity-id]))

(defn- after-read
  [price]
  (-> price
      (update-in [:trade-date] <-storable-date)
      (mny/set-meta :price)))

(defn select
  ([criteria]         (select criteria {}))
  ([criteria options] (select (mny/storage) criteria options))
  ([db criteria options]
   {:pre [(satisfies? mny/Storage db)
          (s/valid? ::criteria criteria)
          (s/valid? ::mny/options options)
          (or (:id criteria)
              (and (:commodity-id criteria)
                   (:trade-date criteria)))]}
   (map after-read
        (mny/select db
                    (mny/model-type criteria :price)
                    (merge {:order-by [[:trade-date :desc]]} options)))))

(dbfn find
  [db id]
  {:pre [(or (valid-id? id)
             (valid-id? (:id id)))]}
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
