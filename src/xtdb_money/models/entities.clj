(ns xtdb-money.models.entities
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.models :as mdls]
            [xtdb-money.util :refer [->id]]
            [xtdb-money.core :as mny]))

(s/def ::name string?)
(s/def ::user-id ::mdls/id)
(s/def ::entity (s/keys :req-un [::name ::user-id]))

(defn select
  ([criteria] (select criteria {}))
  ([criteria options]
   {:pre [(s/valid? ::mny/options options)]}
   (map #(mny/set-meta % :entity)
        (mny/select (mny/storage)
                    (mny/model-type criteria :entity)
                    (update-in options [:order-by] (fnil identity [:name]))))))

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
  {:pre [entity (s/valid? ::entity entity)]}

  (let [records-or-ids (mny/put (mny/storage)
                                [(mny/model-type entity :entity)])]
    (resolve-put-result (first records-or-ids)))) ; TODO: return all of the saved models instead of the first?

(defn delete
  [entity]
  {:pre [entity (map? entity)]}
  (mny/delete (mny/storage) [entity]))
