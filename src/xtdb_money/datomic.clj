(ns xtdb-money.datomic
  (:require [clojure.set :refer [rename-keys]]
            [datomic.client.api :as d]
            [xtdb-money.util :refer [qualify-keys]]
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

(defn- prepend-db
  [coll db-fn]

  (clojure.pprint/pprint {::prepend-db coll})

  (cons (db-fn coll))
  #_(if (instance? com.datomic.Db (first coll))
    coll
    (cons (db-fn) coll)))

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
                                  :db-name db-name})))
          (throw (ex-info "Unable to connect to the database" d)))))))

(defn- criteria->query
  [_criteria {::keys [db]}]
  {:query '[:find ?e
            :where [?e :entity/name]]
   :args [db]})

(defmethod mny/reify-storage :datomic
  [config]
  (let [client (d/client config)
        conn (init-conn client)]
    (reify mny/Storage
      (put [_ models]
        (let [prepped (map (comp #(update-in % [:db/id] (fnil identity "new-entity"))
                                 #(rename-keys % {:entity/id :db/id})
                                 qualify-keys)
                           models)
              result (d/transact conn {:tx-data prepped})]
          (map (fn [{:db/keys [id]}]
                 (get-in result [:tempids id] id))
               prepped)))
      (select [_ criteria options]
        (let [opts (update-in options
                              [::db]
                              (fnil identity (d/db conn)))]
          (d/q (criteria->query criteria opts))))
      (reset [_]
        (d/delete-database client {:db-name db-name})))))
