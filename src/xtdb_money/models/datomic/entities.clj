(ns xtdb-money.models.datomic.entities
  (:require [datomic.client.api :as api]
            [xtdb-money.models.entities :as ents]
            [xtdb-money.datomic :as d]))

(def ^:private entity-keys [:id :name])

(defmethod ents/query :datomic
  [criteria]
  (if-let [id (:id criteria)]
    (map #(zipmap entity-keys %)
         (d/query '[:find ?e ?entity-name
                    :where [?e :entity/name ?entity-name]
                    :in $ ?e]
                  id))
    (map #(zipmap entity-keys %)
         (d/query '[:find ?e ?entity-name
                    :where [?e :entity/name ?entity-name]]))))

(defmethod ents/submit :datomic
  [& models]
  (let [result (d/transact models)]
    (map first (api/q '[:find ?e
                        :where [?e :entity/name _]]
                      (:db-after result)))))
