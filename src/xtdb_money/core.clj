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

(defn- ->xt-map
  [m model-type]
  (postwalk (fn [x]
              (if (map-tuple? x)
                (if (= :id (first x))
                  (assoc-in x [0] :xt/id)
                  (update-in x [0] #(keyword (name model-type)
                                             (name %))))
                x))
            m))

(defn accounts []
  (map
    #(zipmap [:id :name :type]
              %)
    (xt/q (xt/db node)
          '{:find [id name type]
            :where [[id :type :account]
                    [id :account/name name]
                    [id :account/type type]]})))

(defn- make-id
  [id]
  (if id id (java.util.UUID/randomUUID)))

(defn create-account
  [account]
  (put node (-> account
                (update-in [:id] make-id)
                (->xt-map :account)
                (assoc :type :account))))
