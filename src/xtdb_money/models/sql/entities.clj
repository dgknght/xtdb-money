(ns xtdb-money.models.sql.entities
  (:require [next.jdbc.sql :refer [insert!]]
            [next.jdbc.plan :refer [select!]]
            [xtdb-money.sql :as sql]))

(defmethod sql/insert :entity
  [db model]
  (insert! db :entities model))

(defmethod sql/select :entity
  [db criteria _options]
  (select! db [:id :name] ["select * from entities where id = ? limit 1" (:id criteria)]))
