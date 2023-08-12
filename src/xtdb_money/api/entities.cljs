(ns xtdb-money.api.entities
  (:require [dgknght.app-lib.api-async :as api]))

(defn- handle-api-error
  [error]
  (.error js/console "Unable to get a list of entities from the server.")
  (.dir js/console error))

(defn select
  ([xf]
   (select xf handle-api-error))
  ([xf on-error]
   (api/get "/api/entities"
            {}
            {:transform xf
             :on-error on-error})))
