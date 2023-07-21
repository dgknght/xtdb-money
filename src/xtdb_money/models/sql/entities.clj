(ns xtdb-money.models.sql.entities
  (:require [xtdb-money.sql :as sql]))

(defmethod sql/attributes :entity [_]
  [:id :name :default-commodity-id])
