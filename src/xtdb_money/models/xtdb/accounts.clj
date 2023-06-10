(ns xtdb-money.models.xtdb.accounts
  (:require [xtdb-money.models.accounts :as acts]
            [xtdb-money.xtdb :as x]))

(def ^:private query-base
  (x/query-map :account entity-id name type balance first-trx-date last-trx-date))

(defmethod x/criteria->query :account
  [criteria _]
  (reduce (fn [res [k v]]
            (-> res
                (update-in [:in]
                           (fnil conj [])
                           (symbol (name k)))
                (update-in [::x/args] (fnil conj []) v)))
          query-base
          (select-keys criteria [:id :entity-id])))