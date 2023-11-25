(ns xtdb-money.xtdb
  (:require [clojure.java.io :as io]
            [clojure.set :refer [rename-keys]]
            [clojure.pprint :refer [pprint]]
            [xtdb.api :as xt]
            [dgknght.app-lib.core :refer [update-in-if]]
            [xtdb-money.datalog :as dtl]
            [xtdb-money.core :as mny]
            [xtdb-money.util :refer [make-id
                                     unqualify-keys
                                     ->storable-date]])
  (:import org.joda.time.LocalDate
           java.util.UUID
           java.lang.String
           [clojure.lang
            PersistentVector
            PersistentArrayMap
            PersistentHashMap]))

(derive PersistentVector ::vector)
(derive PersistentArrayMap ::map)
(derive PersistentHashMap ::map)

(defmulti coerce-id type)
(defmethod coerce-id :default [id] id)
(defmethod coerce-id String
  [id]
  (UUID/fromString id))

(defmulti before-save mny/model-type)
(defmethod before-save :default [m] m)
(defmulti after-read mny/model-type)
(defmethod after-read :default [m] m)

(def ^:private action-map
  {::mny/delete ::xt/delete})

(defmulti ^:private wrap-trans type)

(defmethod wrap-trans ::vector
  [t]
  (update-in t [0] action-map)) ; exchange the generic action for the xtdb action

(defmethod wrap-trans ::map
  [t]
  [::xt/put t]) ; assume put if no action is specified

(defn- qualify-keys
  [m]
  ; this only qualifies the top-level keys, while the fn in util
  ; does it recursively
  (let [nspace (name (mny/model-type m))
        +nspace (fn [k]
                  (if (namespace k) ; if it's already qualified, leave it as-is
                    k
                    (keyword nspace (name k))))]
    (with-meta (->> m
                    (map #(update-in % [0] +nspace))
                    (into {}))
               (meta m))))

(defn- prep-for-put
  [[oper :as tuple]]
  (update-in tuple [1] (if (= ::xt/delete oper)
                         :id
                         (comp qualify-keys
                               #(rename-keys % {:id :xt/id})
                               #(update-in % [:id] make-id)))))

(defn- submit
  "Given a list of model maps, or vector tuples with an action in the
  1st position and a model in the second, execute the actions and
  return the id values of the models"
  [node docs]
  {:pre [(sequential? docs)]} ; TODO: Use the "&" syntax for any number of documents

  (let [prepped (->> docs
                     (map (comp prep-for-put
                                wrap-trans
                                before-save))
                     (into []))]
    (xt/submit-tx node prepped)
    (xt/sync node)
    (map #(get-in % [1 :xt/id]) prepped)))

(defmulti ->storable type)

(defmethod ->storable :default [v] v)

(defmethod ->storable LocalDate
  [v]
  (->storable-date v))

(defmulti identifying-where-clause mny/model-type)

(defmethod identifying-where-clause :default [_] nil)

(defn- ensure-where
  [query criteria]
  (if (:where query)
    query
    (assoc query :where [(identifying-where-clause criteria)])))

(defmulti before-query mny/model-type)
(defmethod before-query :default [c] c)

(defn- criteria->query
  [criteria opts]
  (let [model-type (mny/model-type criteria)]
    (-> '{:find [(pull ?x [*])]
          :in [$]}
        (dtl/apply-criteria (-> criteria
                                (update-in-if [:id] coerce-id)
                                before-query)
                            {:coerce ->storable
                             :qualifier model-type
                             :args-key [::args]
                             :remap {:id :xt/id}})
        (ensure-where criteria)
        (dtl/apply-options opts :qualifier model-type))))

(defmulti prepare-criteria mny/model-type)
(defmethod prepare-criteria :default [c] c)

(defn- select*
  [node criteria options]
  (let [{::keys [args] :as query} (-> criteria
                                      prepare-criteria
                                      (criteria->query options))
        raw-result (apply xt/q
                          (xt/db node)
                          (dissoc query ::args)
                          args)]
    (map (comp after-read
               (mny/+model-type criteria)
               unqualify-keys
               first)
         raw-result)))

(defn- delete*
  [node models]
  (->> models
       (mapv (comp #(vector ::xt/delete %)
                   :id))
       (xt/submit-tx node))
  (xt/sync node))

(defmulti ^:private resolve-store
  (fn [[type _]] type))

(defmethod resolve-store :kv-store
  [[_ path]]
  {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
              :db-dir (io/file path)
              :sync? true}})

(defn- resolve-stores
  [config]
  (->> config
       (map #(update-in % [1] resolve-store))
       (into {})))

(defn- prepare-config
  [config]
  (-> config
      (dissoc ::mny/provider)
      resolve-stores))

(defmethod mny/reify-storage :xtdb
  [config]
  (let [node (-> config prepare-config xt/start-node) ]
    (reify mny/Storage
      (put    [_ models]           (submit node models))
      (select [_ criteria options] (select* node criteria options))
      (delete [_ models]           (delete* node models))
      (close  [_]                  (.close node))
      (reset  [_]                  (comment "This is a no-op with in-memory implementation")))))
