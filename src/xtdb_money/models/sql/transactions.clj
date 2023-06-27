(ns xtdb-money.models.sql.transactions
  (:require [honey.sql.helpers :refer [where]]
            [clj-time.coerce :refer [to-sql-date]]
            [xtdb-money.sql :as sql]
            [clojure.core :as c]))

(defn- apply-transaction-date
  [s {:keys [transaction-date]}]
  ; TODO: Change this to support conjunctions
  (where s
         (if (vector? transaction-date)
           [(first transaction-date)
            :transaction-date
            (to-sql-date (second transaction-date))]
           [:= :transaction-date (to-sql-date transaction-date)])))

(defn- apply-account-id
  [s {:keys [account-id]}]
  (where s [:or
            [:= :debit-account-id account-id]
            [:= :credit-account-id account-id]]))

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
