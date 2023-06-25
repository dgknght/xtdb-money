(ns xtdb-money.sql
  (:require [xtdb-money.core :as mny]
            [next.jdbc :as jdbc]))

(defn- dispatch
  [_db model & _]
  (mny/model-type model))

(defmulti insert dispatch)
(defmulti select dispatch)

(defn- put*
  [db models]
  (jdbc/with-transaction [tx db]
    (mapv (comp :entities/id
                #(insert tx %))
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
