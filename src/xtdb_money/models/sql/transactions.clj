(ns xtdb-money.models.sql.transactions
  (:require [xtdb-money.sql.types :refer [->storable
                                          <-storable]]
            [xtdb-money.sql :as sql]
            [dgknght.app-lib.core :refer [uuid
                                          update-in-if]]))

; TODO: Dedupe this with what is also in mongodb
(def ^:private attr
  [:id
   :entity-id
   :transaction-date
   :description
   :quantity
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
      (update-in [:transaction-date] ->storable)
      (select-keys attr)))

(defmethod sql/after-read :transaction
  [transaction]
  (-> transaction
      (update-in-if [:correlation-id] uuid)
      (update-in [:transaction-date] <-storable)))
