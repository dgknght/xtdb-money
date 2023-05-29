(ns xtdb-money.helpers
  (:require [config.core :refer [env]]
            [xtdb-money.core :as mny]))

(defn dbs []
  (get-in env [:db :strategies]))

(defn reset-db [f]
  (doseq [db (vals (dbs))]
    (mny/reset-db db)
    (mny/start db))
  (f)
  (doseq [db (vals (dbs))]
    (mny/stop db)))

(defmacro with-strategy
  [id & body]
  `(let [orig-env# env]
     (with-redefs [env (assoc-in orig-env# [:db :active] ~id)]
       (mny/reset-db)
       (mny/start)
       ~@body
       (mny/stop))))
