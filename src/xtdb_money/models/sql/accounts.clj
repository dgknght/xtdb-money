(ns xtdb-money.models.sql.accounts
  (:require [honey.sql.helpers :refer [where]]
            [xtdb-money.sql :as sql]))

(defmethod sql/before-save :account
  [account]
  (update-in account [:type] name))

(defmethod sql/after-read :account
  [account]
  (update-in account [:type] keyword))

(defn- apply-id
  [sql {:keys [id]}]
  (if id
    (where sql [:= :id id])
    sql))

(defn- apply-entity-id
  [sql {:keys [entity-id]}]
  (if entity-id
    (where sql [:= :entity-id entity-id])
    sql))

(defmethod sql/apply-criteria :account
  [s criteria]
  (-> s
      (apply-id criteria)
      (apply-entity-id criteria)))

(defmethod sql/attributes :account [_]
  [:id :entity-id :type :name :balance :first-trx-date :last-trx-date])
