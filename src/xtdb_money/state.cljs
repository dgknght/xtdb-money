(ns xtdb-money.state
  (:require [cljs.pprint :refer [pprint]]
            [secretary.core :as sct]
            [reagent.core :as r]
            [reagent.cookies :as cookies]
            [reagent.ratom :refer [make-reaction]]))

; TODO: Get the default db strategy from the config
(defonce app-state (r/atom {:process-count 0
                            :db-strategy (keyword (cookies/get :db-strategy "xtdb"))
                            :auth-token (cookies/get :auth-token)}))

(def page (r/cursor app-state [:page]))
(def auth-token (r/cursor app-state [:auth-token]))
(def current-user (r/cursor app-state [:current-user]))
(def entities (r/cursor app-state [:entities]))
(def current-entity (r/cursor app-state [:current-entity]))
(def db-strategy (r/cursor app-state [:db-strategy]))
(def process-count (r/cursor app-state [:process-count]))
(def busy? (make-reaction #(not (zero? @process-count))))
(def alerts (r/cursor app-state [:alerts]))

(defn +busy []
  (swap! process-count (fnil inc 0)))

(defn -busy []
  (swap! process-count (fnil dec 1)))

(def -busy-xf
  (map (fn [x] (-busy) x)))

(add-watch process-count
           ::state
           (fn [& args]
             (.debug js/console (str "process count changed: " (last args)))))

(add-watch db-strategy
           ::state
           (fn [_ _ _ strategy]
             (cookies/set! :db-strategy (name strategy))))

(defn sign-out []
  (cookies/remove! :auth-token)
  (cookies/remove! :ring-session)
  (swap! app-state
         dissoc
         :auth-token
         :current-user
         :entities
         :current-entity
         :alerts)
  (sct/dispatch! "/"))
