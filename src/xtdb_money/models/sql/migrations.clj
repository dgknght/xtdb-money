(ns xtdb-money.models.sql.migrations
  (:require [ragtime.jdbc :as jdbc]
            [ragtime.repl :as repl]
            [config.core :refer [env]]))

(defn- config []
  {:datastore (jdbc/sql-database (get-in env [:db :strategies "sql"]))
   :migrations (jdbc/load-resources "migrations")})

(defn migrate []
  (repl/migrate (config)))

(defn rollback []
  (repl/rollback (config)))

(defn remigrate []
  (let [cfg (config)]
    (repl/rollback cfg)
    (repl/migrate cfg)))
