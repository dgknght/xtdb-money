(ns xtdb-money.api.entities
  (:require [xtdb-money.models.entities :as ents]
            [dgknght.app-lib.api :as api]))

(defn- extract-entity
  [{:keys [body]}]
  (-> body
      (select-keys [:name])))

(defn- create
  [req]
  (-> req
      extract-entity
      ents/put
      api/creation-response))

(defn- extract-criteria
  [_req]
  {})

(defn- index
  [req]
  (-> req
      extract-criteria
      ents/select
      api/response))

(def routes
  ["/entities"
   ["" {:get {:handler index}
        :post {:handler create}}]])
