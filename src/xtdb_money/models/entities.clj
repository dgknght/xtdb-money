(ns xtdb-money.models.entities
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.core :as mny :refer [dbfn]]))

(s/def ::name string?)
(s/def ::entity (s/keys :req-un [::name]))

(dbfn select [db criteria]
      (map #(mny/model-type % :entity)
        (mny/select db
                    (mny/model-type criteria :entity)
                    nil)))

#_(defn select
  ([criteria] (select (mny/storage) criteria))
  ([db criteria]
   (map #(mny/model-type % :entity)
        (mny/select db
                    (mny/model-type criteria :entity)
                    nil))))

(defn find
  ([id] (find (mny/storage) id))
  ([db id]
   (first (select db {:id id}))))

(defn put
  ([entity] (put (mny/storage) entity))
  ([db entity]
   {:pre [(s/valid? ::entity entity)]}

   (find db
         (first (mny/put db [(mny/model-type entity :entity)])))))
