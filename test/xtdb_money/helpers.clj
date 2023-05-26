(ns xtdb-money.helpers
  (:require [config.core :refer [env]]
            [xtdb-money.core :as mny]))

(defn reset-db [f]
  (mny/start)
  (f)
  (mny/stop))

(defmacro with-strategy
  [id & body]
  `(let [orig-env# env]
     (with-redefs [env (assoc-in orig-env# [:db :active] ~id)]
       (mny/reset-db)
       (mny/start)
       ~@body
       (mny/stop))))
