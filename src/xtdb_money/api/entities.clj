(ns xtdb-money.api.entities
  (:refer-clojure :exclude [update])
  (:require [dgknght.app-lib.authorization :as auth :refer [+scope
                                                            authorize]]
            [dgknght.app-lib.api :as api]
            [xtdb-money.authorization.entities]
            [xtdb-money.models.entities :as ents]))

(defn- extract-entity
  [{:keys [body]}]
  (-> body
      (select-keys [:name])))

(defn- create
  [{:as req :keys [authenticated]}]
  (-> req
      extract-entity
      (assoc :user-id (:id authenticated))
      ents/put
      api/creation-response))

(defn- extract-criteria
  [{:keys [authenticated params]}]
  (-> params
      (select-keys [:name :user-id])
      (+scope :entity authenticated)))

(defn- index
  [req]
  (-> req
      extract-criteria
      ents/select
      api/response))

(defn- find-and-authorize
  [{:keys [path-params authenticated]} action]
  (some-> (-> path-params
              (select-keys [:id])
              (+scope :entity authenticated)
              ents/find-by)
          (authorize action authenticated)))

(defn- update
  [req]
  (or (some-> (find-and-authorize req ::auth/update)
              (merge (extract-entity req))
              ents/put
              api/response)
      api/not-found))

(defn- no-content
  [& _]
  api/no-content)

(defn- delete
  [req]
  (or (some-> req
              (find-and-authorize ::auth/destroy)
              ents/delete
              no-content)
      api/not-found))

(def routes
  ["/entities"
   ["" {:get {:handler index}
        :post {:handler create}}]
   ["/:id" {:patch {:handler update}
            :delete {:handler delete}}]])
