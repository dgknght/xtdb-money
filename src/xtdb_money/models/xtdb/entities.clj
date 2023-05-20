(ns xtdb-money.models.xtdb.entities
  (:require [xtdb-money.models.entities :as ents]
            [xtdb-money.xtdb :as x]))

(def ^:private query-base
  (x/query-map :entity name))

(defmethod ents/query :xtdb
  [{:keys [id]}]
  (let [query (cond-> query-base
                id (assoc :in '[id]))]
    (if id
      (x/select query id)
      (x/select query))))

(defmethod ents/submit :xtdb
  [& models]
  (apply x/submit models))
