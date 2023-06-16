(ns xtdb-money.util
  (:require [clojure.walk :refer [prewalk]]
            #?(:clj [clj-time.coerce :as tc]
               :cljs [cljs-time.coerce :as tc])
            [cljs.core :as c])
  #?(:clj (:import org.joda.time.LocalDate)))

(def ->storable-date tc/to-long)

(def <-storable-date tc/to-local-date)

(defn ->id
  [id-or-model]
  (or (:id id-or-model) id-or-model))

(def local-date?
  #?(:clj (partial instance? LocalDate)
     :cljs (throw (js/Error "Not implemented"))))

(defn update-in-if
  [m k f & args]
  (if (= :absent (get-in m k :absent))
    m
    (apply update-in m k f args)))

(defn make-id
  [id]
  (or id (random-uuid)))

(defn- key-value-tuple?
  [x]
  (and (vector? x)
       (= 2 (count x))
       (keyword? (first x))))

(defmulti qualify-key
  (fn [x _]
    (when (key-value-tuple? x)
      :tuple)))

(defmethod qualify-key :default
  [x _]
  x)

(defmethod qualify-key :tuple
  [[k :as x] nspace]
  (if (namespace k)
    x
    (update-in x [0] #(keyword nspace (name %)))))

(defn qualify-keys
  "Creates fully-qualified entity attributes by applying
  the :model-type from the meta data to the keys of the map."
  [m]
  {:pre [(-> m meta :model-type)]}
  (prewalk #(qualify-key %
                         (-> m meta :model-type name))
           m))

(defn unqualify-keys
  "Replaces qualified keys with the simple values"
  [m]
  (prewalk (fn [x]
             (if (key-value-tuple? x)
               (update-in x [0] (comp keyword name))
               x))
           m))

(defn +id
  "Given a map without an :id value, adds one with a random UUID as a value"
  ([m] (+id m random-uuid))
  ([m id-fn]
   (if (:id m)
     m
     (assoc m :id (id-fn)))))

(defmulti prepend
  (fn [coll _]
    {:pre [(sequential? coll)]}
    (cond
      (vector? coll) :vector
      (list? coll)   :list)))

(defmethod prepend :vector
  [coll v]
  (vec (concat [v] coll)))

(defmethod prepend :list
  [coll v]
  (conj coll v))

(def non-nil? (complement nil?))
