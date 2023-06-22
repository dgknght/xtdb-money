(ns xtdb-money.models.xtdb.transactions
  (:require [xtdb-money.xtdb :as x]))

(defn- apply-account-id
  [query {:keys [account-id]}]
  (if account-id
    (-> query
        (update-in [::x/args] conj account-id)
        (update-in [:in] conj '?account-id)
        (update-in [:where]
                   conj
                   '(or [id :transaction/debit-account-id ?account-id]
                        [id :transaction/credit-account-id ?account-id])))
    query))

(defmethod x/criteria->query :transaction
  [criteria options]
  (-> '{:find [(pull ?t [*])]
        :in [$]
        :where [[?t :transaction/transaction-date ?transaction-date]]
        ::x/args []}
      (x/apply-criteria (dissoc criteria :account-id))
      (apply-account-id criteria)
      (x/apply-options options)))
