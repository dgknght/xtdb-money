(ns xtdb-money.models.xtdb.accounts
  (:require [xtdb-money.models.accounts :as acts]
            [xtdb-money.xtdb :as x]))

(def ^:private query-base
  (x/query-map :account entity-id name type balance first-trx-date last-trx-date))

(defmethod acts/query :xtdb
  [{:keys [id]}]
  (let [query (cond-> query-base
                id (assoc :in '[id]))]
    (if id
      (x/select query id)
      (x/select query))))

(defmethod acts/submit :xtdb
  [& models]
  (apply x/submit models))
