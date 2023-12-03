(ns xtdb-money.api
  (:refer-clojure :exclude [get])
  (:require [goog.string :refer [format]]
            [dgknght.app-lib.api-3 :as api]
            [xtdb-money.state :as state]))

(defn handle-error
  [error]
  (.error js/console "Unable to get a list of entities from the server.")
  (.dir js/console error))

(defn- apply-defaults
  [{:as opts
    :keys [on-error]
    :or {on-error handle-error}}]
  (let [{:keys [db-strategy auth-token]} @state/app-state]
    (cond-> (assoc opts :on-error on-error)
      db-strategy (assoc-in [:headers "db-strategy"] db-strategy)
      auth-token  (assoc-in [:headers "Authorization"] (format "Bearer %s" auth-token)))))

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
