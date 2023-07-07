(ns xtdb-money.models.sql.transactions
  (:require [honey.sql.helpers :refer [where]]
            [xtdb-money.sql :as sql]
            [dgknght.app-lib.core :refer [uuid
                                          update-in-if]]))

(defn- apply-account-id
  [s {:keys [account-id]}]
  (if account-id
    (where s [:or
              [:= :debit-account-id account-id]
              [:= :credit-account-id account-id]])
    s))

(defmethod sql/apply-criteria :transaction
  [s criteria]
  (reduce-kv sql/apply-criterion
             (apply-account-id s criteria)
             (dissoc criteria :account-id)))

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
      (update-in [:transaction-date] sql/->storable)
      (select-keys attr)))

(defmethod sql/after-read :transaction
  [transaction]
  (-> transaction
      (update-in-if [:correlation-id] uuid)
      (update-in [:transaction-date] sql/<-storable)))
