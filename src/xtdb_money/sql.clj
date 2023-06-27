(ns xtdb-money.sql
  (:refer-clojure :exclude [update])
  (:require [xtdb-money.core :as mny]
            [next.jdbc :as jdbc]
            [next.jdbc.plan :refer [select!]]
            [next.jdbc.sql.builder :refer [for-insert
                                           for-update]]
            [honey.sql.helpers :as h]
            [honey.sql :as hsql]
            [dgknght.app-lib.inflection :refer [plural]]))

(defn- dispatch
  [_db model & _]
  (mny/model-type model))

(defmulti insert dispatch)
(defmulti select dispatch)
(defmulti update dispatch)

(defmulti before-save mny/model-type)
(defmethod before-save :default [m] m)

(def ^:private infer-table-name
  (comp plural
        mny/model-type))

(defmethod insert :default
  [db model]
  (let [table (infer-table-name model)
        s (for-insert table
                      (before-save model)
                      jdbc/snake-kebab-opts)
        result (jdbc/execute-one! db s {:return-keys [:id]})]

    ; TODO: add logging

    (get-in result [(keyword (name table) "id")])))

(defmethod update :default
  [db {:keys [id] :as model}]
  (let [table (infer-table-name model)
        s (for-update table
                      (dissoc (before-save model) :id)
                      {:id id}
                      jdbc/snake-kebab-opts)
        result (jdbc/execute-one! db s {:return-keys [:id]})]

    ; TODO: add logging

    (get-in result [(keyword (name table) "id")])))

(defmulti apply-criteria (fn [_s c] (mny/model-type c)))

(defn apply-id
  [s {:keys [id]}]
  (if id
    (h/where s [:= :id id])
    s))

(defmulti after-read mny/model-type)
(defmethod after-read :default [m] m)

(defmulti attributes identity)

(defmethod select :default
  [db criteria _options]
  (let [query (-> (h/select :*)
                  (h/from (infer-table-name criteria))
                  (apply-criteria criteria)
                  hsql/format)]

    ; TODO: add logging

    (map (comp after-read
               #(mny/model-type % criteria))
         (select! db
                  (attributes (mny/model-type criteria))
                  query
                  jdbc/snake-kebab-opts))))

(defn- upsert
  [db m]
  (if (:id m)
    (update db m)
    (insert db m)))

(defn- put*
  [db models]
  (jdbc/with-transaction [tx db]
    (mapv #(upsert tx %)
          models)))

(defn- select*
  [db criteria options]
  (select db criteria options))

(defn- delete*
  [_db _models])

(defn- reset*
  [db]
  (jdbc/execute! db ["truncate table entities cascade"]))

(defmethod mny/reify-storage :sql
  [config]
  (let [db (jdbc/get-datasource config)]
    (reify mny/Storage
      (put [_ models]       (put* db models))
      (select [_ crit opts] (select* db crit opts))
      (delete [_ models]    (delete* db models))
      (reset [_]            (reset* db)))))
