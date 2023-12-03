(ns xtdb-money.sql.types
  (:require [clj-time.coerce :refer [to-sql-date
                                     to-local-date]])
  (:import org.joda.time.LocalDate))

(derive LocalDate ::date)

(defn coerce-id
  [id]
  (if (string? id)
    (Long/parseLong id)
    id))

(defmulti ->storable type)

(defmethod ->storable :default [x] x)

(defmethod ->storable ::date
  [d]
  (to-sql-date d))

(defmulti <-storable type)

(defmethod <-storable :default [x] x)

(defmethod <-storable java.sql.Date
  [d]
  (to-local-date d))
