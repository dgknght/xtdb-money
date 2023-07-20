(ns xtdb-money.models.datomic.transactions
  (:require [xtdb-money.util :refer [->storable-date
                                     <-storable-date]]
            [xtdb-money.datalog :as dtl]
            [xtdb-money.datomic :as d])
  (:import org.joda.time.LocalDate))

(defmulti ->storable type)

(defmethod ->storable :default [v] v)

(defmethod ->storable LocalDate
  [v]
  (->storable-date v))

(defn- apply-account-id
  [query {:keys [account-id]}]
  (if account-id
    (-> query
        (update-in [:query :in] conj '?account-id)
        (update-in [:args] conj account-id)
        (update-in [:query :where]
                   (comp vec concat)
                   '[(or [?x :transaction/debit-account-id ?account-id]
                         [?x :transaction/credit-account-id ?account-id])]))
    query))

(defmethod d/criteria->query :transaction
  [criteria options]
  (-> '{:query {:find [(pull ?x [*])]
                :in [$]
                :where []}
        :args []}
      (apply-account-id criteria)
      (d/apply-id criteria)
      (dtl/apply-criteria (dissoc criteria
                                  :id
                                  :account-id)
                          {:query-prefix [:query]
                           :coerce ->storable})
      (dtl/apply-options (dissoc options :order-by))))

(defmethod d/before-save :transaction
  [trx]
  (-> trx
      (update-in [:transaction-date] ->storable-date)
      (select-keys [:id
                    :entity-id
                    :transaction-date
                    :correlation-id
                    :debit-account-id
                    :debit-index
                    :debit-balance
                    :credit-account-id
                    :credit-index
                    :credit-balance
                    :description
                    :quantity])))

(defmethod d/after-read :transaction
  [trx]
  (update-in trx [:transaction-date] <-storable-date))
