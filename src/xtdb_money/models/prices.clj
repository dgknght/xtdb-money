(ns xtdb-money.models.prices
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.util :refer [->id
                                     local-date?
                                     valid-id?
                                     <-storable-date]]
            [xtdb-money.core :as mny]))

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
  ([criteria options]
   {:pre [(s/valid? ::criteria criteria)
          (s/valid? ::mny/options options)
          (or (:id criteria)
              (and (:commodity-id criteria)
                   (:trade-date criteria)))]}
   (map after-read
        (mny/select (mny/storage)
                    (mny/model-type criteria :price)
                    (merge {:order-by [[:trade-date :desc]]} options)))))

(defn find
  [id]
  {:pre [(or (valid-id? id)
             (valid-id? (:id id)))]}
  (first (select {:id (->id id)}
                 {:limit 1})))

(defn- before-save
  [price]
  (mny/model-type price :price))

(defn put
  [price]
  {:pre [(s/valid? ::price price)]}

  (let [ids (mny/put (mny/storage) [(before-save price)])]
    (find (first ids))))
