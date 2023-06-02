(ns xtdb-money.core
  (:require [config.core :refer [env]]))

(defprotocol Storage
  "Defines the functions necessary to provider storage for the application"
  (put [this models] "Saves the models to the database in an atomic transaction")
  (select [this criteria options] "Retrieves models from the database")
  (delete [this models] "Removes the models from the database in an atomic transaction")
  (reset [this] "Resets the database")) ; TODO: Is there someplace to put this so it's only available in tests?


(defn storage-dispatch [config & _]
  (::provider config))

(defmulti reify-storage storage-dispatch)
(defmulti start storage-dispatch)
(defmulti stop storage-dispatch)
(defmulti reset-db storage-dispatch)

(def ^:dynamic *storage*)

(defn storage []
  (or *storage*
      (let [active-key (get-in env [:db :active])]
        (reify-storage (get-in env [:db :strategies active-key])))))

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

(defmacro with-storage
  [bindings & body]
  `(let [storage# (reify-storage ~(first bindings))]
     (binding [*storage* storage#]
       ~@body)))

(defmacro dbfn
  [fn-name bindings & body]
  (let [fname (symbol (name fn-name))
        alt-bindings (vec (rest bindings))]
    `(defn ~fname
       (~(vec (rest bindings))
         (apply ~fname ~db ~bindings))
       (~bindings
               ~@body))))
