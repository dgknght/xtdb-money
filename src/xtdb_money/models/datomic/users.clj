(ns xtdb-money.models.datomic.users
  (:require [xtdb-money.datomic :as d]))

; Turn a user from
; {:id 101
;  :given-name "John"
;  :identities {:google "abc123"
;               :github "def456"}}
; into
; [{:db/id 101
;   :user/given-name "John"}
;  [:db/add 101 :user/identities [:google "abc123"]]
;  [:db/add 101 :user/identities [:github "def456"]]]
(defmethod d/deconstruct :user
  [{:keys [id identities] :as user}]
  (cons (dissoc user :identities)
        (map (fn [tuple]
               [:db/add id :user/identities tuple])
             identities)))

(defmethod d/after-read :user
  [user]
  (update-in user [:identities] #(into {} %)))

(defmethod d/bounding-where-clause :user
  [_]
  '[?x :user/email ?email])
