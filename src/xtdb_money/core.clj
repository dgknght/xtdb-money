(ns xtdb-money.core
  (:require [config.core :refer [env]]))

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

(defn storage-dispatch [& _]
  (when-let [active (get-in env [:db :active])]
    (get-in env [:db :strategies active :provider])))

(defmulti start storage-dispatch)
(defmulti stop storage-dispatch)
