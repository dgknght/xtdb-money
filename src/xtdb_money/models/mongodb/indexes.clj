(ns xtdb-money.models.mongodb.indexes
  (:refer-clojure :exclude [ensure])
  (:require [clojure.pprint :refer [pprint]]
            [config.core :refer [env]]
            [somnium.congomongo :as m]
            [xtdb-money.mongodb :as mdb]))

(def ^:private all-indexes
  {:users {"uk_user_email" {:fields [:email]
                            :options {:unique true}}
           "uk_user_oauth_id" {:fields [:oauth-ids.provider :oauth-ids.provider-id]}}
   :entities {"uk_entity_name" {:fields [:name]
                                :options {:unique true}}}
   :accounts {"ix_account_entity" {:fields [:entity-id]}}
   :transactions {"ix_transaction_date" {:fields [:transaction-date]}}})

(defn- config []
  (get-in env [:db :strategies "mongodb"]))

(defn ensure
  "Ensures that all indexes are present on all collections"
  []

  (m/with-mongo (mdb/connect (config))
    (doseq [[coll indexes] all-indexes]
      (let [existing (->> (m/get-indexes coll)
                          (map #(% "name"))
                          (into #{}))]
        (doseq [[name {:keys [fields options]}] (remove #(existing (first %)) indexes)]
          (pprint {::create-index {:name name
                                   :fields fields
                                   :options options}})
          (m/add-index! coll fields (assoc options :name name)))))))
