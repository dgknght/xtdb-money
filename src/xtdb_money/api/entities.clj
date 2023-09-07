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

(defn- find-and-authorize
  [{:keys [path-params]}]
  ; TODO: Add authorization
  (ents/find (:id path-params)))

(defn- extract-entity
  [{:keys [body]}]
  (select-keys body [:name]))

(defn- update
  [req]
  (-> (find-and-authorize req)
      (merge (extract-entity req))
      ents/put
      api/response))

(defn- delete
  [req]
  (ents/delete (find-and-authorize req))
  (api/response))


(def routes
  ["/entities"
   ["" {:get {:handler index}
        :post {:handler create}}]
   ["/:id" {:patch {:handler update}
            :delete {:handler delete}}]])
