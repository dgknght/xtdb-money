(ns xtdb-money.core
  (:require [clojure.spec.alpha :as s]
            [clojure.set :refer [union]]
            [config.core :refer [env]]))

(def comparison-opers #{:< :<= :> :>=})
(def set-opers #{:and :or})
(def opers (union comparison-opers set-opers))
(def oper? (partial contains? opers))

(s/def ::offset integer?)
(s/def ::limit integer?)
(s/def ::order-by vector?) ; TODO: flesh out this spec
(s/def ::options (s/keys :opt-un [::offset ::limit ::order-by]))

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

(declare model-type)

(defn- extract-model-type
  [m-or-t]
  (if (keyword? m-or-t)
   m-or-t
    (model-type m-or-t)))

(defn model-type
  "The 1 arity retrieves the type for the given model. The 2 arity sets
  the type for the given model. The 2nd argument is either a key identyfying
  the model type, or another model from which the type is to be extracted"
  ([m]
   (-> m meta :model-type))
  ([m model-or-type]
   (vary-meta m assoc :model-type (extract-model-type model-or-type))))

(defn set-meta
  [m model-or-type]
  (vary-meta m assoc
               :model-type (extract-model-type model-or-type)
               :original (with-meta m nil)))

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
       (~alt-bindings
         (apply ~fname (storage) ~alt-bindings))
       (~bindings
               ~@body))))
