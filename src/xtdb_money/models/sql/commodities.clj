(ns xtdb-money.models.sql.commodities
  (:require [xtdb-money.sql :as sql]))

(defmethod sql/before-save :commodity
  [commodity]
  (update-in commodity [:type] name))

(defmethod sql/after-read :commodity
  [commodity]
  (update-in commodity [:type] keyword))

(defmethod sql/attributes :commodity [_]
  [:id :entity-id :type :name :symbol])
