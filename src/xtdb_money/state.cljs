(ns xtdb-money.state
  (:require [reagent.core :as r]
            [reagent.ratom :refer [make-reaction]]))

(defonce app-state (r/atom {}))

(def page (r/cursor app-state [:page]))
(def entities (r/cursor app-state [:entities]))
(def current-entity (r/cursor app-state [:current-entity]))
(def db-strategy (r/cursor app-state [:db-strategy]))
(def process-count (r/cursor app-state [:process-count]))
(def busy? (make-reaction #(zero? @process-count)))

(defn +busy []
  (swap! process-count (fnil inc 0)))

(defn -busy []
  (swap! process-count (fnil dec 1)))

(def -busy-xf
  (map (fn [x] (-busy) x)))

