(ns xtdb-money.models.entities
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.core :as mny]))

(s/def ::name string?)
(s/def ::entity (s/keys :req-un [::name]))

(defmulti query mny/storage-dispatch)
(defmulti submit mny/storage-dispatch)

(defn select
  ([criteria] (select (mny/storage) criteria))
  ([db criteria]
   (map #(mny/model-type % :entity)
        (query db criteria))))

(defn find
  ([id] (find (mny/storage) id))
  ([db id]
   (first (select db {:id id}))))

(defn put
  ([entity] (put (mny/storage) entity))
  ([db entity]
   {:pre [(s/valid? ::entity entity)
          (::mny/provider db)]}

   (-> (submit db (mny/model-type entity :entity))
       first
       find)))
