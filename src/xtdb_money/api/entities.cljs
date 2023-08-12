(ns xtdb-money.api.entities
  (:require [dgknght.app-lib.api :as api]))

(defn- handle-api-error
  [error]
  (.error js/console "Unable to get a list of entities from the server.")
  (.dir js/console error))

(defn select
  ([on-success]
   (select on-success handle-api-error))
  ([on-success on-error]
   (api/get "/api/entities"
            on-success
            on-error)))
