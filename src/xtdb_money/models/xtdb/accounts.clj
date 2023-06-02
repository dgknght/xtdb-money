(ns xtdb-money.models.xtdb.accounts
  (:require [xtdb-money.models.accounts :as acts]
            [xtdb-money.xtdb :as x]))

(def ^:private query-base
  (x/query-map :account entity-id name type balance first-trx-date last-trx-date))

(defmethod x/criteria->query :account
  [criteria]
  (reduce (fn [res [k v]]
            (-> res
                (update-in [0 :in]
                           (fnil conj [])
                           (symbol (name k)))
                (update-in [1] (fnil conj []) v)))
          [query-base []]
          (select-keys criteria [:id :entity-id])))
