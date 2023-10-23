(ns xtdb-money.models.sql.users
  (:require [clojure.tools.logging :as log]
            [next.jdbc :as jdbc]
            [next.jdbc.sql.builder :refer [for-insert]]
            [honey.sql.helpers :as h]
            [xtdb-money.core :as mny]
            [xtdb-money.sql :as sql]))

(defmethod sql/attributes :user [_]
  [:id :email :given-name :surname])

(defn- inflate-identity
  [[p id]]
  ; TODO: This is going to need the user-id value also
  {:provider p
   :provider-id id})

(defmethod sql/insert :user
  [db {:keys [identities] :as user}]
  (let [s (for-insert :users
                      (dissoc user :identities)
                      jdbc/snake-kebab-opts)
        result (jdbc/execute-one! db s {:return-keys [:id]})
        id (get-in result [:users/id])]
    (->> identities
         (mapv (comp (mny/+model-type :identity)
                     #(assoc % :user-id id)
                     inflate-identity))
         (sql/put* db))
    (log/debugf "insert user %s -> %s" s result)
    id))

(defn- apply-identities-criterion
  [s {[_ [provider provider-id] :as identities] :identities}]
  (if identities
    (-> s
        (h/join :identities [:= :users.id :identities.user_id])
        (h/where [:and
                  [:= :identities.provider (name provider)]
                  [:= :identities.provider_id provider-id]]))
    s))

(defmethod sql/apply-criteria :user
  [s criteria]
  (if (empty? criteria)
    s
    (reduce-kv sql/apply-criterion
               (apply-identities-criterion s criteria)
               (dissoc criteria :identities))))

(defmethod sql/after-read :user
  [user {:keys [db]}]
  ; TODO: I either need the db or the config here
  ; maybe after-read should have an options map
  (assoc user :identities (->> (-> {:user-id (:id user)}
                                   (mny/model-type :identity)
                                   (select db))
                               (map (juxt :provider :provider-id))
                               (into {}))))
