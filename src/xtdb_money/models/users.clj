(ns xtdb-money.models.users
  (:refer-clojure :exclude [find])
  (:require [clojure.spec.alpha :as s]
            [clojure.pprint :refer [pprint]]
            [clj-time.core :as t]
            [clj-time.coerce :refer [to-long
                                     from-long]]
            [dgknght.app-lib.validation :as v]
            [xtdb-money.models :as mdls]
            [xtdb-money.util :refer [->id]]
            [xtdb-money.core :as mny]))

(s/def ::email v/email?)
(s/def ::given-name string?)
(s/def ::surname string?)
(s/def ::identities (s/map-of keyword? string?))
(s/def ::user (s/keys :req-un [::email ::given-name ::surname]
                      :opt-un [::identities]))

(s/def ::criteria (s/keys :opt-un [::email
                                   ::mdls/id]))

(defn- select-identities
  [user]
  (mny/select (mny/storage)
              (-> {:user-id (:id user)}
                  (mny/model-type :identity))
              {}))

(defn- assoc-identities
  [user]
  ; Some of the data storage strategies embed the identities
  ; in the user record
  (if (:identities user)
    user
    (assoc user :identities (->> (select-identities user)
                                 (map (juxt :provider :provider-id))
                                 (into {})))))
(defn- after-read
  [user]
  (-> user
      assoc-identities
      (mny/set-meta :user)))

(defn select
  ([criteria] (select criteria {}))
  ([criteria options]
   {:pre [(s/valid? ::criteria criteria)
          (s/valid? ::mny/options options)]}
   (map after-read
        (mny/select (mny/storage)
                    (mny/model-type criteria :user)
                    options))))

(defn find-by
  ([criteria] (find-by criteria {}))
  ([criteria opts]
   (first (select criteria (assoc opts :limit 1)))))

(defn find
  [id]
  (find-by {:id (->id id)}))

(defn find-by-oauth
  [tuple] ; tuple contains provider in 1st pos., id in 2nd pos.
  (find-by {:identities [:= tuple]}))

(defn- resolve-put-result
  [[x :as records]]
  (if (map? x)
    (mny/model-type x :user)
    (some find records))) ; This is because when adding a user, identities are inserted first, so the primary record isn't the first one returned

(defn put
  [user]
  {:pre [user (s/valid? ::user user)]}
  (let [records-or-ids (mny/put (mny/storage)
                                [(mny/model-type user :user)])]
    (resolve-put-result records-or-ids))) ; TODO: return all of the saved models instead of the first?

(defn delete
  [user]
  {:pre [user (map? user)]}
  (mny/delete (mny/storage) [user]))

(defn- +expiration
  [m]
  (assoc m :expires-at (to-long
                         (t/plus (t/now)
                                 (t/hours 6)))))

(defn- expired?
  [{:keys [expires-at]}]
  (t/before? (from-long expires-at)
             (t/now)))

(defn tokenize
  [user]
  (+expiration {:user-id (:id user)}))

(defn detokenize
  [{:keys [user-id] :as token}]
  (when token
    (when-not (expired? token)
      (find user-id))))
