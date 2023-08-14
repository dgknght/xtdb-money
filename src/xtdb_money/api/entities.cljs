(ns xtdb-money.api.entities
  (:refer-clojure :exclude [update])
  (:require [dgknght.app-lib.api-async :as api :refer [path]]))

(defn- handle-api-error
  [error]
  (.error js/console "Unable to get a list of entities from the server.")
  (.dir js/console error))

(defn select
  [xf]
  (api/get (path :entities)
           {}
           {:transform xf
            :handle-ex handle-api-error}))

(defn create
  [entity xf]
  (api/post (path :entities)
            entity
            {:transform xf
             :handle-ex handle-api-error}))

(defn update
  [{:keys [id] :as entity} xf]
  {:pre [(:id entity)]}
  (api/patch (path :entities id)
             (dissoc entity :id)
             {:transform xf
              :handle-ex handle-api-error}))

(defn put
  ([entity xf]
   (if (:id entity)
     (update entity xf)
     (create entity xf))))
