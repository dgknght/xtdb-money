(ns xtdb-money.models.sql.accounts
  (:require [clj-time.coerce :refer [to-sql-date
                                     to-local-date]]
            [dgknght.app-lib.core :refer [update-in-if]]
            [xtdb-money.sql :as sql]))

(defmethod sql/before-save :account
  [account]
  (-> account
      (update-in-if [:first-trx-date] to-sql-date)
      (update-in-if [:last-trx-date] to-sql-date)
      (update-in [:type] name)))

(defmethod sql/after-read :account
  [account]
  (-> account
      (update-in-if [:first-trx-date] to-local-date)
      (update-in-if [:last-trx-date] to-local-date)
      (update-in [:type] keyword)))


(defmethod sql/attributes :account [_]
  [:id :entity-id :type :name :balance :first-trx-date :last-trx-date])
