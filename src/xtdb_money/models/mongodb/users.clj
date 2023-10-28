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

(defn- assoc-identities
  [query identities]
  (if (seq identities)
    (assoc query :identities {:$elemMatch {:oauth-provider (first identities)
                                           :oauth-id (second identities)}})
    query))

(defmethod m/apply-criteria :user
  [query {:as criteria :keys [identities]}]
  (-> query
      (m/apply-criteria (-> criteria
                            (dissoc :identities)
                            (with-meta {::mny/model-type :generic})))
      (assoc-identities identities)))
