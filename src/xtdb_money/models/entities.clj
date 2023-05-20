(ns xtdb-money.models.entities
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.core :as mny]))

(s/def ::name string?)
(s/def ::entity (s/keys :req-un [::name]))

(defmulti query mny/storage-dispatch)
(defmulti submit mny/storage-dispatch)

(defn select
  ([] (select {}))
  ([criteria]
   (map #(mny/model-type % :entity)
        (query criteria))))

(defn find
  [id]
  (first (select {:id id})))

(defn put
  [entity]
  {:pre [(s/valid? ::entity entity)]}

  (-> entity
        (mny/model-type :entity)
        submit
        first
        find))
