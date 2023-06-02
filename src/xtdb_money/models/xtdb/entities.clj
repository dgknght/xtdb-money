(ns xtdb-money.models.xtdb.entities
  (:require [xtdb-money.xtdb :as x]))

(defmethod x/criteria->query :entity
  [{:keys [id]}]
  (if id
    [(assoc (x/query-map :entity name)
            :in '[id])
     [id]]
    [(x/query-map :entity name)]))
