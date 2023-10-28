(ns xtdb-money.models.sql.users
  (:require [honey.sql.helpers :as h]
            [xtdb-money.core :as mny]
            [xtdb-money.sql :as sql])
  (:import java.util.UUID))

(defmethod sql/attributes :user [_]
  [:id :email :given-name :surname])

(defn- inflate-identity
  [user-id [p id]]
  (mny/model-type {:user-id user-id
                   :provider p
                   :provider-id id}
                  :identity))

(defmethod sql/deconstruct :user
  [{:as user :keys [identities]}]
  (let [id (or (:id user) (UUID/randomUUID))]
    (-> user
        (assoc :id id)
        (dissoc :identities)
        (cons (map (partial inflate-identity id)
                   identities)))))

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

(defmethod sql/resolve-temp-ids :identity
  [ident id-map]
  (update-in ident [:user-id] id-map))
