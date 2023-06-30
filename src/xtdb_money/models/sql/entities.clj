(ns xtdb-money.models.sql.entities
  (:require [xtdb-money.sql :as sql]))

(defmethod sql/apply-criteria :entity
  [s criteria]
  (sql/apply-id s criteria))

(defmethod sql/attributes :entity [_]
  [:id :name])
