(ns xtdb-money.models.datomic.transactions
  (:require [xtdb-money.util :refer [->storable-date
                                     <-storable-date]]
            [xtdb-money.datomic :as d]))

(defn- apply-id
  [query {:keys [id]}]
  (if id
    (-> query
        (update-in [:query :in] conj '?t)
        (update-in [:args] conj id))
    query))

(defn- apply-account-id
  [query {:keys [account-id]}]
  (if account-id
    (-> query
        (update-in [:query :in] conj '?a)
        (update-in [:args] conj account-id)
        (update-in [:query :where]
                   (comp vec concat)
                   '[(or [?t :transaction/debit-account-id ?a]
                         [?t :transaction/credit-account-id ?a])]))
    query))

(defn- apply-entity-id
  [query {:keys [entity-id]}]
  (if entity-id
    (-> query
        (update-in [:query :in] conj '?e)
        (update-in [:args] conj entity-id)
        (update-in [:query :where]
                  conj '[?t :transaction/entity-id ?e]))
    query))

(defmulti ^:private apply-transaction-date
  (fn [_query {:keys [transaction-date]}]
    (when transaction-date
      (if (vector? transaction-date)
        (case (first transaction-date)
          (:< :<= :> :>=) :comparison
          :and            :intersection
          :or             :union)
        :simple))))

(defmethod apply-transaction-date :default
  [query _criteria]
  query)

(defmethod apply-transaction-date :simple
  [query {:keys [transaction-date]}]
  (-> query
      (update-in [:query :in] conj '?d)
      (update-in [:args] conj transaction-date)
      (update-in [:query :where] conj '[?t :transaction/transaction-date ?d])))

(defmethod apply-transaction-date :comparison
  [query {[oper d] :transaction-date}]
  (-> query
      (update-in [:query :in] conj '?d)
      (update-in [:args] conj d)
      (update-in [:query :where]
                 (comp vec concat)
                 ['[?t :transaction/transaction-date ?trx-date]
                  [(list (symbol (name oper))
                         '?trx-date
                         '?d)]])))

(defmethod d/criteria->query :transaction
  [criteria _options]
  (-> '{:query {:find [(pull ?t [*])]
                :in [$]
                :where []}
        :args []}
      (apply-id criteria)
      (apply-account-id criteria)
      (apply-entity-id criteria)
      (apply-transaction-date criteria)))

(defmethod d/before-save :transaction
  [trx]
  (-> trx
      (update-in [:transaction-date] ->storable-date)
      (select-keys [:entity-id
                    :transaction-date
                    :correlation-id
                    :debit-account-id
                    :debit-index
                    :debit-balance
                    :credit-account-id
                    :credit-index
                    :credit-balance
                    :description
                    :amount])))

(defmethod d/after-read :transaction
  [trx]
  (-> trx
      (update-in [:debit-account-id] :id)
      (update-in [:credit-account-id] :id)
      (update-in [:entity-id] :id)
      (update-in [:transaction-date] <-storable-date)))
