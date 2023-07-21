(ns xtdb-money.models.entities
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.util :refer [->id]]
            [xtdb-money.core :as mny :refer [dbfn]]))

(s/def ::name string?)
(s/def ::entity (s/keys :req-un [::name]))

(dbfn select
  [db criteria options]
  {:pre [(satisfies? mny/Storage db)
         (s/valid? ::mny/options options)
         (:id criteria)]}
  (map #(mny/set-meta % :entity)
       (mny/select db
                   (mny/model-type criteria :entity)
                   options)))

(dbfn find
  [db id]
  (first (select db {:id (->id id)} {:limit 1})))

(defn- resolve-put-result
  [db x]
  (if (map? x)
    (mny/model-type x :entity)
    (find db x)))

(dbfn put
  [db entity]
  {:pre [(s/valid? ::entity entity)]}

  (let [records-or-ids (mny/put db [(mny/model-type entity :entity)])]
    (resolve-put-result db (first records-or-ids)))) ; TODO: return all of the saved models instead of the first?
