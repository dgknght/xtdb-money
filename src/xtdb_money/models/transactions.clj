(ns xtdb-money.models.transactions
  (:require [xtdb-money.core :as mny]
            [xtdb-money.models.accounts :as acts]))

(defn put
  [{:keys [debit-account-id credit-account-id amount] :as trx}]
  (let [d-account (acts/find debit-account-id)
        c-account (acts/find credit-account-id)]
    (mny/put (vary-meta trx assoc :model-type :transaction)
             (acts/debit d-account amount)
             (acts/credit c-account amount))))
