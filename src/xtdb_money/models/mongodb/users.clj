(ns xtdb-money.models.mongodb.users
  (:require [dgknght.app-lib.core :refer [update-in-if]]
            [xtdb-money.util :refer [update-in-criteria]]
            [xtdb-money.mongodb :as m]))

(defmethod m/before-save :user
  [user]
  (update-in-if
    user
    [:identities]
    (fn [i]
      (mapv (fn [[p id]]
              {:oauth-provider p
               :oauth-id id})
            i))))

(defmethod m/after-read :user
  [user]
  (update-in-if
    user
    [:identities]
    (fn [i]
      (->> i
           (map (juxt (comp keyword :oauth-provider)
                      :oauth-id))
           (into {})))))

(defmethod m/prepare-criteria :user
  [criteria]
  (update-in-criteria criteria
                      [:identities]
                      (fn [[oper [provider id]]]
                        [oper {:oauth-provider provider
                               :oauth-id id}])))
