(ns xtdb-money.datomic
  (:require [clojure.set :refer [rename-keys]]
            #_[config.core :refer [env]]
            [datomic.client.api :as d]
            [xtdb-money.util :refer [+id qualify-keys]]
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

(def config {:server-type :dev-local
             :system "money-dev-system"
             :storage-dir "/Users/dknight/.datomic-storage"})

(defn- client []
  (d/client config))

(def ^:private conn (atom nil))

(defn- db [] (d/db @conn))

(defmethod mny/start :datomic []
  (let [cl (client)
        _ (d/create-database cl {:db-name "money-dev"}) ; TODO: should this be run every time? only once?
        cn (d/connect cl {:db-name "money-dev"})]
    (reset! conn cn)
    (d/transact cn {:tx-data schema
                    :db-name "money-dev"}))) ; TODO: should this be run every time? only once?

(defmethod mny/stop :datomic []
  (reset! conn nil))

(defmethod mny/reset-db :datomic []
  (d/delete-database (client) {:db-name "money-dev"}))

(defn transact
  [models]
  (d/transact @conn
              {:tx-data (map (comp #(rename-keys % {:entity/id :db/id}) ; TODO: unkludge this
                                   qualify-keys)
                             models)}))

(defn query
  [q & args]
  (d/q {:query q
        :args (if (instance? datomic.core.db.Db
                             (first args))
                args
                (cons (db) args))}))

(defn index-pull
  [q]
  (d/index-pull (db) q))
