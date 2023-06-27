(ns xtdb-money.models.sql.accounts
  (:require [honey.sql.helpers :refer [where]]
            [clj-time.coerce :refer [to-sql-date]]
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
  (update-in account [:type] keyword))

(defn- apply-entity-id
  [sql {:keys [entity-id]}]
  (if entity-id
    (where sql [:= :entity-id entity-id])
    sql))

(defmethod sql/apply-criteria :account
  [s criteria]
  (-> s
      (sql/apply-id criteria)
      (apply-entity-id criteria)))

(defmethod sql/attributes :account [_]
  [:id :entity-id :type :name :balance :first-trx-date :last-trx-date])
