(ns xtdb-money.models.sql.users
  (:require [xtdb-money.sql :as sql]))

(defmethod sql/attributes :user [_]
  [:id :email :given-name :surname])
