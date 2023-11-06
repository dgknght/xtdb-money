(ns xtdb-money.authorization.entities
  (:require [dgknght.app-lib.authorization :as auth]))

(defmethod auth/allowed? [:entity ::auth/manage]
  [entity _action user]
  (= (:user-id entity)
     (:id user)))

(defmethod auth/scope :entity
  [_ user]
  {:user-id (:id user)})
