(ns xtdb-money.models.entities
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.core :as mny :refer [dbfn]]))

(s/def ::name string?)
(s/def ::entity (s/keys :req-un [::name]))

(dbfn select
  [db criteria options]
  (map #(mny/set-meta % :entity)
    (mny/select db
                (mny/model-type criteria :entity)
                options)))

(dbfn find
  [db id]
  (first (select db {:id id} {:limit 1})))

(dbfn put
  [db entity]
  {:pre [(s/valid? ::entity entity)]}

  (let [ids (mny/put db [(mny/model-type entity :entity)])]
    (find db (first ids)))) ; TODO: return all of the saved models instead of the first?
