(ns xtdb-money.api.entities
  (:refer-clojure :exclude [update])
  (:require [xtdb-money.models.entities :as ents]
            [dgknght.app-lib.api :as api]))

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
  [{:keys [authenticated]}]
  ; TODO: move this to the authorization ns
  {:user-id (:id authenticated)})

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

(defn- update
  [req]
  (if-let [entity (find-and-authorize req)]
    (-> entity
        (merge (extract-entity req))
        ents/put
        api/response)
    api/not-found))

(defn- delete
  [req]
  (if-let [entity (find-and-authorize req)]
    (do (ents/delete entity)
        api/no-content)
    api/not-found))


(def routes
  ["/entities"
   ["" {:get {:handler index}
        :post {:handler create}}]
   ["/:id" {:patch {:handler update}
            :delete {:handler delete}}]])
