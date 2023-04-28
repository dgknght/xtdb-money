(ns xtdb-money.core
  (:require [clojure.walk :refer [postwalk]]
            [xtdb.api :as xt]
            [cljs.core :as c]
            [xtdb-money.util :refer [local-date?
                                     ->storable-date]])
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
    (c/cond
      (map-tuple? x) :tuple
      (local-date? x) :date)))

(defmethod ->xt* :default
  [x _]
  x)

(defmethod ->xt* :date
  [x _]
  (->storable-date x))

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

(defn- wrap-trans
  [t]
  [::xt/put t])

(defn- prepare-trans
  [t]
  (if (vector? t)
    t
    (-> t ->xt-map wrap-trans)))

(defn submit
  [& docs]
  {:pre [(seq docs)
         (every? (some-fn map?
                          vector?) docs)]}

  (let [n @node
        prepped (->> docs
                     (map prepare-trans)
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

(defmacro query-map
  [model-type & fields]
  {:pre [(keyword? model-type)
         (every? symbol? fields)]}

  (let [type (name model-type)
        flds (cons 'id fields)]
    `{:find (quote ~(vec flds))
      :keys (quote ~(vec flds))
      :where (quote ~(mapv (fn [field]
                             ['id (keyword type (name field)) field])
                           fields))}))
