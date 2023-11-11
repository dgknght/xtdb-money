(ns xtdb-money.models.mongodb.users
  (:require [dgknght.app-lib.core :refer [update-in-if]]
            [xtdb-money.core :as mny]
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
  (update-in-if criteria [:identities] (fn [[provider id]]
                                         [:= {:oauth-provider provider
                                              :oauth-id id}])))
