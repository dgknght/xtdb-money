(ns xtdb-money.helpers
  (:require [xtdb-money.core :as mny]))

(defn reset-db [f]
  (mny/start)
  (f)
  (mny/stop))
