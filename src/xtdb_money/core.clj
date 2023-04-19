(ns xtdb-money.core
  (:require [clojure.walk :refer [postwalk]]
            [xtdb.api :as xt])
  (:gen-class))


(defonce ^:private node (atom nil))

(defn start []
  (reset! node (xt/start-node {})))

(defn stop []
  (reset! node nil))

(defn- make-id
  [id]
  (if id id (java.util.UUID/randomUUID)))

(defn- two-count?
  [coll]
  (= 2 (count coll)))

(defn- f-keyword?
  [[x]]
  (keyword? x))

(def map-tuple?
  (every-pred vector?
              two-count?
              f-keyword?))

(defmulti ^:private ->xt*
  (fn [x _]
    (when (map-tuple? x)
      :tuple)))

(defmethod ->xt* :default
  [x _]
  x)

(defmethod ->xt* :tuple
  [x model-type-name]
  (if (= :id (first x))
    (assoc-in x [0] :xt/id)
    (update-in x [0] #(keyword model-type-name
                               (name %)))))

(defn- ->xt-keys
  [m model-type]
  (postwalk #(->xt* % (name model-type)) m))

(defn- ->xt-map
  [m]
  {:pre [(-> m meta :model-type)]}

  (let [model-type (-> m meta :model-type)]
    (-> m
        (update-in [:id] make-id)
        (->xt-keys model-type))))

(defn put
  [& docs]
  {:pre [(seq docs)]}

  (let [n @node
        prepped (->> docs
                     (map (comp #(vector ::xt/put %)
                                ->xt-map))
                     (into []))]
    (xt/submit-tx n prepped)
    (xt/sync n)
    (map #(get-in % [1 :xt/id]) prepped)))

(defn select
  ([query]
   (xt/q (xt/db @node)
         query))
  ([query param]
   (xt/q (xt/db @node)
         query
         param)))
