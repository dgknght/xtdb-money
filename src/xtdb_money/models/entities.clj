(ns xtdb-money.models.entities
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.core :as mny]))

(s/def ::name string?)
(s/def ::entity (s/keys :req-un [::name]))

(defn- ->model
  [model]
  (as-> model m
      (zipmap [:id :name] m)
      (vary-meta m assoc :model-type :entity)))

(defn select
  ([] (select {}))
  ([{:keys [id]}]
   (let [query (cond-> {:find '[id name]
                        :where '[[id :entity/name name]]}
                 id (assoc :in '[id]))]
     (map ->model (if id
                    (mny/select query id)
                    (mny/select query))))))

(defn find
  [id]
  (first (select {:id id})))

(defn- find-first
  [[id]]
  (find id))

(defn put
  [entity]
  {:pre [(s/valid? ::entity entity)]}

  (-> entity
      (vary-meta assoc :model-type :entity)
      (mny/put)
      find-first))
