(ns xtdb-money.models.entities
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.util :refer [->id]]
            [xtdb-money.core :as mny]))

(s/def ::name string?)
(s/def ::entity (s/keys :req-un [::name]))

(defn select
  ([criteria] (select criteria {}))
  ([criteria options]
   {:pre [(s/valid? ::mny/options options)]}
   (map #(mny/set-meta % :entity)
        (mny/select (mny/storage)
                    (mny/model-type criteria :entity)
                    options))))

(defn find
  [id]
  (first (select {:id (->id id)} {:limit 1})))

(defn- resolve-put-result
  [x]
  (if (map? x)
    (mny/model-type x :entity)
    (find x)))

(defn put
  [entity]
  {:pre [(s/valid? ::entity entity)]}

  (let [records-or-ids (mny/put (mny/storage)
                                [(mny/model-type entity :entity)])]
    (resolve-put-result (first records-or-ids)))) ; TODO: return all of the saved models instead of the first?
