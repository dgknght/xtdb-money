(ns xtdb-money.helpers
  (:require [config.core :refer [env]]
            [xtdb-money.core :as mny]))

(defn dbs []
  (get-in env [:db :strategies]))

(defn reset-db [f]
  (let [dbs (->> (get-in env [:db :strategies])
                 vals
                 (map mny/reify-storage))]
    (doseq [db dbs]
      (mny/reset db)
      (binding [mny/*storage* db]
        (f)))))

(defmacro with-strategy
  [id & body]
  `(let [orig-env# env]
     (with-redefs [env (assoc-in orig-env# [:db :active] ~id)]
       (mny/reset-db)
       (mny/start)
       ~@body
       (mny/stop))))
