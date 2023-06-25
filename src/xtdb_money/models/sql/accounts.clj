(ns xtdb-money.models.sql.accounts
  (:require [next.jdbc :as jdbc]
            [next.jdbc.plan :refer [select!]]
            [next.jdbc.sql.builder :refer [for-insert]]
            [honey.sql :as h]
            [honey.sql.helpers :refer [select from where]]
            [xtdb-money.sql :as sql]))

(defn- before-save
  [account]
  (update-in account [:type] name))

(defn- after-read
  [account]
  (update-in account [:type] keyword))

(defmethod sql/insert :account
  [db model]

  (let [s (for-insert :accounts
                      (before-save model)
                      jdbc/snake-kebab-opts)]

    ; TODO: add logging

    (jdbc/execute-one! db s {:return-keys true})))

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

(defmethod sql/select :account
  [db criteria _options]
  (let [query (-> (select :*)
                  (from :accounts)
                  (apply-id criteria)
                  (apply-entity-id criteria)
                  h/format)]

    ; TODO: add logging

    (map after-read
         (select! db
                  [:id :entity-id :type :name :balance :first-trx-date :last-trx-date]
                  query
                  jdbc/snake-kebab-opts))))
