(ns xtdb-money.core
  (:require [config.core :refer [env]]))

(def ^:dynamic *storage*)

(defn storage []
  (or *storage*
      (get-in env [:db :strategies (get-in env [:db :active])])))

(defn model-type
  ([m]
   (-> m meta :model-type))
  ([m model-type]
   (vary-meta m assoc :model-type model-type)))

(defn prepare
  [m model-type]
  (vary-meta m assoc
             :model-type model-type
             :original m))

(defn changed?
  [m]
  (not= m (-> m meta :original)))

(defn storage-dispatch [db & _]
  (some ::provider [db (meta db)]))

(defmulti start storage-dispatch)
(defmulti stop storage-dispatch)
(defmulti reset-db storage-dispatch)
