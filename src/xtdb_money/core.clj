(ns xtdb-money.core
  (:require [clojure.set :refer [rename-keys]]
            [clojure.walk :refer [postwalk]]
            [xtdb.api :as xt])
  (:gen-class))


(def ^:private node (xt/start-node {}))

(defn- put
  [node & docs]
  {:pre [(seq docs)]}

  (clojure.pprint/pprint {::put docs})

  (xt/submit-tx node (->> docs
                          (map #(vector ::xt/put %))
                          (into [])))
  (xt/sync node))

(defn- ->xt-map
  [m model-type]
  (postwalk (fn [x]
              (if (and (vector? x)
                       (keyword? (first x))
                       (= 2 (count x)))
                (if (= :id (first x))
                  (assoc-in x [0] :xt/id)
                  (update-in x [0] #(keyword (name model-type) (name %))))
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
