(ns xtdb-money.models.sql.entities
  (:require [honey.sql.helpers :refer [where]]
            [xtdb-money.sql :as sql]))

(defmethod sql/apply-criteria :entity
  [s {:keys [id]}]
  (if id
    (where s [:= :id id])
    s))

(defmethod sql/attributes :entity [_]
  [:id :name])
