(ns xtdb-money.state
  (:require [reagent.core :as r]))

(defonce app-state (r/atom {}))

(def page (r/cursor app-state [:page]))
(def entities (r/cursor app-state [:entities]))
(def current-entity (r/cursor app-state [:current-entity]))
(def db-strategy (r/cursor app-state [:db-strategy]))
