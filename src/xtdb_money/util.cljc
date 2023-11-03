(ns xtdb-money.util
  (:require [clojure.walk :refer [prewalk]]
            [clojure.string :as string]
            #?(:clj [clojure.pprint :refer [pprint]]
               :cljs [cljs.pprint :refer [pprint]])
            #?(:clj [clj-time.coerce :as tc]
               :cljs [cljs-time.coerce :as tc]))
  #?(:clj (:import org.joda.time.LocalDate)))

(def ->storable-date tc/to-long)

(def <-storable-date tc/to-local-date)

(defn ->id
  [id-or-model]
  (or (:id id-or-model) id-or-model))

(def local-date?
  #?(:clj (partial instance? LocalDate)
     :cljs #(throw (js/Error "Not implemented"))))

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

(defn- normalize-sort-key
  [x]
  (if (vector? x)
    (if (= 1 (count x))
      (conj x :asc)
      x)
    [x :asc]))

(defn- compare-fn
  [& ms]
  (fn [_ [k dir]]
    (let [[v1 v2] (map k ms)
          f (if (= :desc dir)
              #(compare %2 %1)
              compare)
          res (f v1 v2)]
      (if (= 0 res)
        0
        (reduced res)))))

(defn- sort-fn
  [order-by]
  {:pre [(vector? order-by)]}
  (fn [m1 m2]
    (->> order-by
         (map normalize-sort-key)
         (reduce (compare-fn m1 m2)
                 0))))

(defn apply-sort
  [{:keys [order-by]} models]
  (if order-by
    (sort (sort-fn order-by) models)
    models))

(defn split-nils
  "Given a map, return a tuple containing the map
  with all nil attributes removed in the first position
  and a vector containing the keys that had nil values
  in the second"
  [m]
  (reduce (fn [res [k v]]
            (if v
              (update-in res [0] assoc k v)
              (update-in res [1] conj k)))
          [{} []]
          m))

(def scalar?
  (complement coll?))

(def valid-id?
  (every-pred non-nil?
              scalar?))

(defn truncate
  ([s] (truncate s {}))
  ([s {:keys [length]
       :or {length 10}}]
   (if (> length (count s))
     s
     (reduce (fn [result s]
               (if (< length (+ (count result) (count s) 1))
                 (reduced (str result " " (first s)))
                 (str result " " s)))
             (string/split s #"\s+")))))

(defn truncate-html
  ([s] (truncate-html s {}))
  ([s opts]
   [:span {:title s}
    (truncate s opts)]))
