(ns xtdb-money.api.entities
  (:refer-clojure :exclude [update])
  (:require [dgknght.app-lib.api-3 :as api :refer [path]]))

(defn select
  [& {:as opts}]
  (api/get (path :entities) opts))

(defn create
  [entity & {:as opts}]
  (api/post (path :entities)
            entity
            opts))

(defn update
  [{:keys [id] :as entity} & {:as opts}]
  {:pre [(:id entity)]}
  (api/patch (path :entities id)
             (dissoc entity :id)
             opts))

(defn put
  [entity & {:as opts}]
  (if (:id entity)
    (apply update entity opts)
    (apply create entity opts)))

(defn delete
  [entity & {:as opts}]
  {:pre [(:id entity)]}
  (api/delete {:id (:id entity)}
              opts))
