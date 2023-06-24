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
                   '(or [?t :transaction/debit-account-id ?account-id]
                        [?t :transaction/credit-account-id ?account-id])))
    query))

(defn- apply-id
  [query {:keys [id]}]
  (if id
    (-> query
        (update-in [::x/args] conj id)
        (update-in [:in] conj '?id)
        (update-in [:where] conj '[?t :xt/id ?id]))
    query))

(defmethod x/criteria->query :transaction
  [criteria options]
  (-> '{:find [(pull ?t [*]) ?transaction-date]
        :in [$]
        :where [[?t :transaction/transaction-date ?transaction-date]]
        ::x/args []}
      (x/apply-criteria (dissoc criteria :id :account-id))
      (apply-id criteria)
      (apply-account-id criteria)
      (x/apply-options options)))
