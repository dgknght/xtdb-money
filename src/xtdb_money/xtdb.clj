(ns xtdb-money.xtdb
  (:require [clojure.walk :refer [postwalk]]
            [xtdb.api :as xt]
            [cljs.core :as c]
            [xtdb-money.core :as mny]
            [xtdb-money.util :refer [local-date?
                                     make-id
                                     ->storable-date]]))

; This is a no-op with the memory implementation
(defmethod mny/reset-db :xtdb [_])

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

(defmulti ^:private ->xt-map #(cond
                                (map? %) :map
                                (vector? %) :vector))

(defmethod ->xt-map :map
  [m]
  {:pre [(-> m meta :model-type)]}

  (let [model-type (-> m meta :model-type)]
    (-> m
        (update-in [:id] make-id)
        (->xt-keys model-type))))

(defmethod ->xt-map :vector
  [m]
  (update-in m [1] ->xt-map))

(defmethod ->xt-map :default
  [m]
  m)

(def ^:private action-map
  {::mny/delete ::xt/delete})

(defmulti ^:private wrap-trans #(cond
                                  (map? %) :map
                                  (vector? %) :vector))

(defmethod wrap-trans :vector
  [t]
  (update-in t [0] action-map)) ; exchange the generic action for the xtdb action

(defmethod wrap-trans :map
  [t]
  [::xt/put t]) ; assume put if no action is specified

(defn submit
  "Give a list of model maps, or vector tuples with an action in the
  1st position and a model in the second, execute the actions and
  return the id values of the models"
  [node docs]
  {:pre [(sequential? docs)]} ; TODO: Use the "&" syntax for any number of documents

  (let [prepped (->> docs
                     (map (comp ->xt-map
                                wrap-trans))
                     (into []))]
    (xt/submit-tx node prepped)
    (xt/sync node)
    (map #(get-in % [1 :xt/id]) prepped)))

(defn select
  ([node query]
   (xt/q (xt/db node) query))
  ([node query params]
   (xt/q (xt/db node) query params)))

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

(defmulti criteria->query mny/model-type)

(defmethod mny/reify-storage :xtdb
  [config]
  (let [node (xt/start-node (dissoc config ::mny/provider))]
    (reify mny/Storage
      (put [_ models] (submit node models))
      (select [_ criteria _options]
        (let [{::keys [args] :as query} (criteria->query criteria)]
          (apply xt/q (xt/db node) (dissoc query ::args) args)))
      (delete [_ models] (submit node (map #(vector :xt/delete %) models)))
      (reset [_] (comment "This is a no-op with in-memory implementation")))))
