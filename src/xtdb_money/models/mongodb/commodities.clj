(ns xtdb-money.models.mongodb.commodities
  (:require [xtdb-money.mongodb :as mdb]))

(defmethod mdb/after-read :commodity
  [commodity]
  (update-in commodity [:type] keyword))
