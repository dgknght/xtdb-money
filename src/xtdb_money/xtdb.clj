(ns xtdb-money.xtdb
  (:require [clojure.walk :refer [postwalk]]
            [clojure.java.io :as io]
            [xtdb.api :as xt]
            [cljs.core :as c]
            [xtdb-money.datalog :as dtl]
            [xtdb-money.core :as mny]
            [xtdb-money.util :refer [local-date?
                                     make-id
                                     unqualify-keys
                                     ->storable-date]])
  (:import org.joda.time.LocalDate))

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

(defn- simplify-deletions
  "Given a tuple with a delete operation, extracts the
  :id from the model in the second position"
  [[oper :as tuple]]
  (if (= ::xt/delete oper)
    (update-in tuple [1] :id)
    tuple))

(defn submit
  "Give a list of model maps, or vector tuples with an action in the
  1st position and a model in the second, execute the actions and
  return the id values of the models"
  [node docs]
  {:pre [(sequential? docs)]} ; TODO: Use the "&" syntax for any number of documents

  (let [prepped (->> docs
                     (map (comp ->xt-map
                                simplify-deletions
                                wrap-trans))
                     (into []))]
    (xt/submit-tx node prepped)
    (xt/sync node)
    (map #(get-in % [1 :xt/id]) prepped)))

(defmulti after-read mny/model-type)
(defmethod after-read :default [m] m)

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

(defmulti criteria->query
  (fn [criteria _opts] (mny/model-type criteria)))

(defmethod criteria->query :default
  [criteria opts]
  (let [model-type (mny/model-type criteria)]
    (-> '{:find [(pull ?x [*])]
        :in [$]}
      (dtl/apply-criteria criteria
                          :coerce ->storable
                          :model-type model-type
                          :args-key [::args]
                          :remap {:id :xt/id})
      (ensure-where criteria)
      (dtl/apply-options opts :model-type model-type))))

(defmulti ^:private apply-criterion
  (fn [_query [_k v]]
    (when (vector? v)
      (case (first v)
        (:< :<= :> :>=) :comparison
        :and            :intersection
        :or             :union))))

(defn- arg-ident
  ([k] (arg-ident k nil))
  ([k suffix]
  (symbol (str "?" (name k) suffix))))

(defmethod apply-criterion :default
  [query [k v]]
  (-> query
      (update-in [:in] (fnil conj []) (arg-ident k))
      (update-in [::args] (fnil conj []) (->storable v))))

(defmethod apply-criterion :comparison
  [query [k [oper v]]]
  (let [arg (arg-ident k "-in")]
    (-> query
        (update-in [::args] (fnil conj []) (->storable v))
        (update-in [:in] (fnil conj []) arg)
        (update-in [:where] conj [(list (symbol (name oper))
                                        (arg-ident k)
                                        arg)]))))

(defmethod apply-criterion :intersection
  [query [k [_ & v]]]
  (update-in query
             [:where]
             (comp vec concat)
             (map (fn [[oper x]]
                    (vector
                      (list (-> oper name symbol)
                            (symbol (str "?" (name k)))
                            (->storable x))))
                  v)))

(defmethod apply-criterion :union
  [query [k [_ & v]]]
  ; This shape of this actually depends on the operator.
  ; If it's equality, then it can look like [attr :qualified/attr value]
  ; If it's comparison, it should look like ((< attr value))
  ; It should also support nested ands and ors, which it currently does not
  (update-in query [:where] conj [(list 'or
                                        (map (fn [[oper x]]
                                               (list (-> oper name symbol)
                                                     (symbol (str "?" k))
                                                     x))
                                             v))]))

(defn- select*
  [node criteria options]
  (let [{::keys [args] :as query} (criteria->query criteria options)
        raw-result (apply xt/q
                          (xt/db node)
                          (dissoc query ::args)
                          args)]
    (map (comp after-read
               #(mny/model-type % criteria)
               unqualify-keys
               first)
         raw-result)))

(defn- delete*
  [node models]
  (->> models
       (map #(vector :xt/delete %))
       (submit node)))

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
      (put [_ models]              (submit node models))
      (select [_ criteria options] (select* node criteria options))
      (delete [_ models]           (delete* node models))
      (reset [_] (comment "This is a no-op with in-memory implementation")))))
