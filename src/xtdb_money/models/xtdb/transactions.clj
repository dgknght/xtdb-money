(ns xtdb-money.models.xtdb.transactions
  (:require [xtdb-money.datalog :as dtl]
            [xtdb-money.xtdb :as x]))

(defn- apply-account-id
  [query {:keys [account-id]}]
  (if account-id
    (-> query
        (update-in [::x/args] conj account-id)
        (update-in [:in] conj '?account-id)
        (update-in [:where]
                   conj
                   '(or [?x :transaction/debit-account-id ?account-id]
                        [?x :transaction/credit-account-id ?account-id])))
    query))

(defn- ensure-transaction-date
  [{:keys [where] :as query}]
  (if (some #(= :transaction/transaction-date
                      (second %))
            where)
    query
    (update-in query
               [:where]
               (fnil conj [])
               '[?x :transaction/transaction-date ?transaction-date])))

(defmethod x/criteria->query :transaction
  [criteria options]
  (-> '{:find [(pull ?x [*]) ?transaction-date]
        :in [$]
        ::x/args []}
      (dtl/apply-criteria (dissoc criteria :account-id)
                          {:coerce x/->storable
                           :args-key [::x/args]
                           :remap {:id :xt/id}})
      ensure-transaction-date
      (apply-account-id criteria)
      (dtl/apply-options options)))
