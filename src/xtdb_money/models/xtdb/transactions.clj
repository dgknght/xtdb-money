(ns xtdb-money.models.xtdb.transactions
  (:require [xtdb-money.xtdb :as x]))

(def ^:private base-query
  (x/query-map :transaction
               entity-id
               correlation-id
               transaction-date
               description
               debit-account-id
               debit-index
               debit-balance
               credit-account-id
               credit-index
               credit-balance
               amount))

(defn- apply-account-id
  [query {:keys [account-id]}]
  (if account-id
    (-> query
        (update-in [::x/args] (fnil conj []) account-id)
        (update-in [:in] (fnil conj []) 'account-id)
        (update-in [:where]
                   conj
                   '(or [id :transaction/debit-account-id account-id]
                        [id :transaction/credit-account-id account-id])))
    query))

(defmethod x/criteria->query :transaction
  [criteria options]
  (-> base-query
      (x/apply-criteria (dissoc criteria :account-id))
      (apply-account-id criteria)
      (x/apply-options options)))
