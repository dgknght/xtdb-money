(ns xtdb-money.models.transactions
  (:require [clojure.spec.alpha :as s]
            [xtdb-money.core :as mny]
            [xtdb-money.models :as models]
            [xtdb-money.models.accounts :as acts]))

(s/def ::debit-account-id uuid?)
(s/def ::credit-account-id uuid?)
(s/def ::amount (s/and decimal?
                       #(< 0M %)))
(s/def ::transaction (s/keys :req-un [::models/entity-id
                                      ::credit-account-id
                                      ::debit-account-id
                                      ::amount]))

(defn put
  [{:keys [debit-account-id credit-account-id amount] :as trx}]
  {:pre [(s/valid? ::transaction trx)]}

  (let [d-account (acts/find debit-account-id)
        c-account (acts/find credit-account-id)]
    (->> [trx d-account c-account]
         (map :entity-id)
         (apply =)
         assert)
    (mny/put (vary-meta trx assoc :model-type :transaction)
             (acts/debit d-account amount)
             (acts/credit c-account amount))))
