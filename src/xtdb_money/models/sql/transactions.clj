(ns xtdb-money.models.sql.transactions
  (:require [honey.sql.helpers :refer [where]]
            [clj-time.coerce :refer [to-sql-date
                                     to-local-date]]
            [xtdb-money.sql :as sql]
            [clojure.core :as c]))

; local-date ->                  [:= :transaction-date local-date}
; [:< local-date] ->             [:<= :transaction-date local-date]]
; [:and [:>= start] [:< end]] -> [:and [:>= :transaction-date start] [:< :transaction-date end]]]

(defmulti apply-transaction-date
  (fn [_s {:keys [transaction-date]}]
    (when transaction-date
      (if (vector? transaction-date)
        (if (#{:and :or} (first transaction-date))
          :conjunction
          :explicit)
        :implicit))))

(defmethod apply-transaction-date :default
  [s _]
  s)

(defmethod apply-transaction-date :implicit
  [s {:keys [transaction-date]}]
  (where s [:= :transaction-date (to-sql-date transaction-date)]))

(defmethod apply-transaction-date :explicit
  [s {[oper transaction-date] :transaction-date}]
  (where s [oper :transaction-date (to-sql-date transaction-date)])) ; TODO: when this is generalized, to-sql-date needs to be generalized too

(defmethod apply-transaction-date :conjunction
  [s {[oper & stmts] :transaction-date}]
  (apply where s oper (map (fn [[oper v]]
                             [oper :transaction-date (to-sql-date v)])
                           stmts)))

(defn- apply-account-id
  [s {:keys [account-id]}]
  (if account-id
    (where s [:or
              [:= :debit-account-id account-id]
              [:= :credit-account-id account-id]])
    s))

(defmethod sql/apply-criteria :transaction
  [s criteria]
  (-> s
      (sql/apply-id criteria)
      (apply-account-id criteria)
      (apply-transaction-date criteria)))

(def ^:private attr
  [:id
   :entity-id
   :transaction-date
   :description
   :amount
   :debit-account-id
   :debit-index
   :debit-balance
   :credit-account-id
   :credit-index
   :credit-balance
   :correlation-id])

(defmethod sql/attributes :transaction [_] attr)

(defmethod sql/before-save :transaction
  [transaction]
  (-> transaction
      (update-in [:transaction-date] to-sql-date)
      (select-keys attr)))

(defmethod sql/after-read :transaction
  [transaction]
  (update-in transaction [:transaction-date] to-local-date))
