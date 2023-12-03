(ns xtdb-money.api.users
  (:require [dgknght.app-lib.api :as api]))

(defn- me
  [{:keys [authenticated]}]
  (api/response authenticated))

(def routes
  ["/me" {:get {:handler me}}])
