(ns xtdb-money.models.xtdb.users
  (:require [dgknght.app-lib.core :refer [update-in-if]]
            [xtdb-money.xtdb :as x]))

; Turn a user from
; {:id 101
;  :given-name "John"
;  :identities {:google "abc123"
;               :github "def456"}}
; into
; {:id 101
;  :given-name "John"
;  :identities [{:google "abc123"}
;               {:github "def456"}]}
(defmethod x/before-save :user
  [user]
  (update-in-if user
                [:identities]
                (fn [i]
                  (map #(apply hash-map %)
                       i))))

(defmethod x/after-read :user
  [user]
  (update-in-if user
                [:identities]
                (fn [i]
                  (->> i
                       (mapcat identity)
                       (reduce (fn [r [p id]]
                                 (assoc r p id))
                               {})))))

(defmethod x/before-query :user
  [criteria]
  (update-in-if criteria [:identities 1] (fn [[k v]]
                                         {k v})))
