(ns xtdb-money.datomic
  (:require [clojure.set :refer [rename-keys]]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [config.core :refer [env]]
            [datomic.client.api :as d]
            [datomic.client.api.protocols :refer [Connection]]
            [clj-time.coerce :as tc]
            [xtdb-money.datalog :as dtl]
            [xtdb-money.util :as u :refer [qualify-keys
                                           unqualify-keys
                                           prepend
                                           apply-sort
                                           split-nils]]
            [xtdb-money.core :as mny])
  (:import org.joda.time.LocalDate
           java.lang.String))

(derive clojure.lang.PersistentVector ::vector)
(derive clojure.lang.PersistentArrayMap ::map)
(derive clojure.lang.PersistentHashMap ::map)

(defmulti deconstruct mny/model-type)
(defmulti before-save mny/model-type)
(defmulti after-read mny/model-type)

(defmethod deconstruct :default [m] [m])
(defmethod before-save :default [m] m)
(defmethod after-read :default [m] m)

(defn- conj* [& args]
  (apply (fnil conj []) args))

(defn- schema []
  (mapcat (comp edn/read-string
                slurp
                io/resource
                #(format "datomic/schema/%s.edn" %))
          ["account"
           "commodity"
           "entity"
           "model"
           "price"
           "transaction"
           "user"]))

(def ^:private db-name "money")

(defn- apply-schema*
  [client]
  (d/create-database client {:db-name db-name})
  (d/transact (d/connect client {:db-name db-name})
              {:tx-data (schema)
               :db-name db-name}))

(defn apply-schema
  [& [config-key]]
  (let [cfg (dissoc (get-in env [:db
                                 :strategies
                                 (or config-key "datomic")])
                    ::mny/provider)]
    (assert cfg (str "No datomic configuration found for " (or config-key "datomic")))
    (apply-schema*
      (d/client
        cfg))))

(defmulti coerce-id type)
(defmethod coerce-id :default [id] id)
(defmethod coerce-id String
  [id]
  (Long/parseLong id))

(defmulti ->storable type)
(defmethod ->storable :default [x] x)
(defmethod ->storable LocalDate [d] (tc/to-long d))

(defmulti bounding-where-clause
  (fn [crit-or-model-type]
    (if (keyword? crit-or-model-type)
      crit-or-model-type
      (mny/model-type crit-or-model-type))))

(defn- unbounded-query?
  [{{:keys [in where]} :query}]
  (and (empty? where)
       (not-any? #(= '?x %) in)))

(defn- ensure-bounded-query
  [query criteria]
  (if (unbounded-query? query)
    (assoc-in query [:query :where] [(bounding-where-clause criteria)])
    query))

(defn- exclude-deleted
  [query _opts]
  (update-in query [:query :where] conj* '(not [?x :model/deleted? true])))

(defmulti prepare-criteria mny/model-type)

(defmethod prepare-criteria :default [c] c)

(defn- criteria->query
  [criteria opts]
  (let [m-type (or (mny/model-type criteria)
                   (:model-type opts))]
    (-> '{:query {:find [(pull ?x [*])]
                  :in [$]}
          :args []}
        (dtl/apply-criteria (prepare-criteria criteria)
                            {:qualifier m-type
                             :query-prefix [:query]
                             :coerce ->storable
                             :coerce-id coerce-id
                             :entity-key :id
                             :entity-symbol '?x})
        (ensure-bounded-query criteria)
        (exclude-deleted opts)
        (dtl/apply-options opts :qualifier m-type))))

(defmulti ^:private prep-for-put type)

(defmethod prep-for-put ::map
  [m]
  (let [[m* nils] (split-nils m)
        mt (mny/model-type m)]
    (cons (-> m*
              (mny/model-type mt)
              before-save
              (rename-keys {:id :db/id})
              qualify-keys)
          (->> nils
               (remove #(nil? (-> m meta :original %)))
               (map #(vector :db/retract (:id m) (keyword (name mt) (name %))))))))

(def ^:private action-map
  {::mny/delete :db/retract
   ::mny/put    :db/add})

(defmethod prep-for-put ::vector
  [[action {:keys [id] :as model} :as v]]
  (if (map? model)
    ; This is primarily for delete (retract), which seems to want
    ; the list form instead of the map form, so we retract each datom
    ; that is part of the map
    ; It's also necessary to nullify an existing value, such as an
    ; accounts :first-trx-date after deleting the only transaction
    (let [model-type (-> model mny/model-type name)
          op (action-map action)]
      (if (= :db/retract op)
        [[:db/retractEntity id]]
        (->> (-> model
                 (dissoc :id)
                 before-save)
             (map (fn [kv] ; qualify the key to make it a datomic attribute
                    (update-in kv [0] #(keyword model-type (name %)))))
             (reduce (fn [res [k v]]
                       (conj res [op id k v]))
                     []))))
    [v]))

(derive ::mny/put ::map+id)

(defmulti ^:private +id
  (fn [x]
    (if (vector? x)
      (first x)
      ::map+id)))

(defmethod +id :default [m] m)

(defmethod +id ::map+id [m]
  (u/+id m (comp str random-uuid)))

(defn- put*
  [models {:keys [conn]}]
  {:pre [(satisfies? Connection conn)]}
  (let [prepped (->> models
                     (map +id)
                     (mapcat deconstruct)
                     (mapcat prep-for-put)
                     vec)
        {:keys [tempids]} (d/transact conn {:tx-data prepped})]
    (map #(or (tempids (:db/id %))
              (:db/id %))
         prepped)))

; It seems that after an entire entity has been retracted, the id
; can still be returned
(def ^:private naked-id?
  (every-pred map?
              #(= 1 (count %))
              #(= :db/id (first (keys %)))))

(defn- extract-ref-ids
  [m]
  (->> m
       (map #(update-in %
                        [1]
                        (fn [v]
                          (if (naked-id? v)
                            (:db/id v)
                            v))))
       (into {})))

(defn- select*
  [criteria options {:keys [conn]}]
  (let [query (-> criteria
                  (criteria->query options)
                  (update-in [:args]
                             prepend
                             (or (::db options)
                                 (d/db conn))))
        raw-result (d/q query)]
    (->> raw-result
         (map first)
         (remove naked-id?)
         (map (comp after-read
                    #(mny/model-type % criteria)
                    unqualify-keys
                    extract-ref-ids))
         (apply-sort options))))

(defn- delete*
  [models {:keys [conn]}]
  (d/transact conn {:tx-data (mapv #(vector :db/add (:id %) :model/deleted? true)
                                   models)}))

(defn- reset*
  [client]
  (d/delete-database client {:db-name db-name})
  (apply-schema* client))

(defmethod mny/reify-storage :datomic
  [config]
  (let [client (d/client config)
        conn (d/connect client {:db-name db-name})]
    (reify mny/Storage
      (put [_ models]       (put* models {:conn conn}))
      (select [_ crit opts] (select* crit opts {:conn conn}))
      (delete [_ models]    (delete* models {:conn conn}))
      (close [_])
      (reset [_]            (reset* client)))))
