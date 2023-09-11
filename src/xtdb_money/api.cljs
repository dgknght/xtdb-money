(ns xtdb-money.api
  (:refer-clojure :exclude [get])
  (:require [dgknght.app-lib.api-3 :as api]
            [xtdb-money.state :refer [db-strategy]]))

(defn handle-error
  [error]
  (.error js/console "Unable to get a list of entities from the server.")
  (.dir js/console error))

(defn- apply-defaults
  [{:as opts :keys [on-error]}]
  (-> opts
      (assoc :on-error (or on-error handle-error))
      (assoc-in [:headers "db-strategy"] @db-strategy)))

(defn get
  [url opts]
  (api/get url (apply-defaults opts)))

(defn post
  [url resource opts]
  (api/post url resource (apply-defaults opts)))

(defn patch
  [url resource opts]
  (api/patch url resource (apply-defaults opts)))

(defn delete
  [url opts]
  (api/delete url (apply-defaults opts)))
