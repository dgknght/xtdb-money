(ns xtdb-money.models.datomic.users
  (:require [clojure.set :refer [rename-keys]]
            [xtdb-money.core :as mny]
            [xtdb-money.util :refer [+id]]
            [xtdb-money.datomic :as d]))

; Turn a user from
; {:id 101
;  :given-name "John"
;  :identities [{:id 201
;                :identity [:google "abc123"]}
;               {:id 202
;                :identity [:github "def456"]}]}
; into
; [{:db/id 201
;   :oauth/identity [:google "abc123"]}
;  {:db/id 202
;   :oauth/identity [:github "def456"]}
;  {:db/id 101
;   :user/given-name "John"}
;  [:db/add 1 :user/identities 201]
;  [:db/add 1 :user/identities 202]]
(defmethod d/deconstruct :user
  [{:keys [id identities] :as user}]
  (let [idents (map #(+id % (comp str random-uuid)) identities)]
    (concat (map #(-> %
                      (rename-keys {:id :db/id
                                    :identity :oauth/identity})
                      (mny/model-type :identity))
                 idents)
            [(vary-meta (dissoc user :identities)
                        assoc :principle? true)] ; indicate that this is the principle record being saved
            (->> idents
                 (map :id)
                 (map #(vector :db/add id :user/identities %))))))
