(ns xtdb-money.models.entities
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.core :as mny]))

(s/def ::name string?)
(s/def ::entity (s/keys :req-un [::name]))

(def ^:private query-base
  (mny/query-map :entity name))

(defn select
  ([] (select {}))
  ([{:keys [id]}]
   (let [query (cond-> query-base
                 id (assoc :in '[id]))]
     (map #(mny/model-type % :entity)
          (if id
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
      (mny/model-type :entity)
      (mny/submit)
      find-first))
