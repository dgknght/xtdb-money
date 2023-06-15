(ns xtdb-money.datomic
  (:require [clojure.set :refer [rename-keys]]
            [datomic.client.api :as d]
            [datomic.client.api.protocols :refer [Connection]]
            [xtdb-money.util :refer [qualify-keys +id]]
            [xtdb-money.core :as mny]))

(def schema
  [
   ; Entity
   {:db/ident :entity/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The name of the entity"}
   
   ; Account
   {:db/ident :account/entity-id
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Identifies the entity to which the account belongs"}
   {:db/ident :account/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The name of the account"}
   {:db/ident :account/type
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The type of the account (asset, liability, equity, income, expense)"}
   {:db/ident :account/balance
    :db/valueType :db.type/bigdec
    :db/cardinality :db.cardinality/one
    :db/doc "The final balance of the account"}
   {:db/ident :account/first-trx-date
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The date of the first transaction in the account"}
   {:db/ident :account/last-trx-date
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The date of the last transaction in the account"}])

(def ^:private db-name "money")

(defn- init-conn
  [client]
  (try
    (d/connect client {:db-name db-name})
    (catch clojure.lang.ExceptionInfo e
      ; TODO: is there are better way that catching this exception?
      (let [d (ex-data e)]
        (if (= :cognitect.anomalies/not-found
               (:cognitect.anomalies/category d))
          (do (d/create-database client {:db-name db-name})
              (let [conn (d/connect client {:db-name db-name})]
                (d/transact conn {:tx-data schema
                                  :db-name db-name})
                conn))
          (throw (ex-info "Unable to connect to the database" d)))))))

(defn- criteria->query
  [_criteria {::keys [db]}]
  {:query '[:find ?e ?name
            :keys id name
            :where [?e :entity/name ?name]]
   :args [db]})

(defn- put*
  [models {:keys [conn]}]
  {:pre [(satisfies? Connection conn)]}
  (let [prepped (map (comp qualify-keys
                           #(rename-keys % {:id :db/id})
                           #(+id % (comp str random-uuid)))
                     models)
        result (d/transact conn {:tx-data prepped})]
    (map (fn [m]
           (get-in result [:tempids (:db/id m)] (:db/id m)))
         prepped)))

(defn- select*
  [criteria options {:keys [conn]}]
  (let [opts (update-in options
                        [::db]
                        (fnil identity (d/db conn)))
        query (criteria->query criteria opts)]
    (d/q query)))

(defmethod mny/reify-storage :datomic
  [config]
  (let [client (d/client config)
        conn (init-conn client)]
    (reify mny/Storage
      (put [_ models]       (put* models {:conn conn}))
      (select [_ crit opts] (select* crit opts {:conn conn}))
      (reset [_]            (d/delete-database client {:db-name db-name})))))
