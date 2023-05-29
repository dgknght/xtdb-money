(ns xtdb-money.models.datomic.entities
  (:require [datomic.client.api :as api]
            [xtdb-money.models.entities :as ents]
            [xtdb-money.datomic :as d]))

(def ^:private entity-keys [:id :name])

(defmethod ents/query :datomic
  [cfg criteria]
  (if-let [id (:id criteria)]
    (map #(zipmap entity-keys %)
         (d/query cfg '[:find ?e ?entity-name
                        :where [?e :entity/name ?entity-name]
                        :in $ ?e]
                  id))
    (map #(zipmap entity-keys %)
         (d/query cfg '[:find ?e ?entity-name
                        :where [?e :entity/name ?entity-name]]))))

(defmethod ents/submit :datomic
  [cfg models]
  (let [result (d/transact cfg [models])]
    ; TODO: Here I need to be able to pass in the database, not a configuration
    (map first (api/q cfg '[:find ?e
                            :where [?e :entity/name _]]
                      (:db-after result)))))
