(ns xtdb-money.core
  (:require [clojure.walk :refer [postwalk]]
            [xtdb.api :as xt])
  (:gen-class))


(def ^:private node (xt/start-node {}))

(defn- put
  [node & docs]
  {:pre [(seq docs)]}

  (xt/submit-tx node (->> docs
                          (map #(vector ::xt/put %))
                          (into [])))
  (xt/sync node))

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

(defn- make-id
  [id]
  (if id id (java.util.UUID/randomUUID)))

(defn- ->xt-map
  [m model-type]
  (-> m
      (update-in [:id] make-id)
      (->xt-keys model-type)
      (assoc :type model-type)))

(defn accounts []
  (map
    #(zipmap [:id :name :type]
              %)
    (xt/q (xt/db node)
          '{:find [id name type]
            :where [[id :type :account]
                    [id :account/name name]
                    [id :account/type type]]})))

(defn create-account
  [account]
  (put node (->xt-map account :account)))
