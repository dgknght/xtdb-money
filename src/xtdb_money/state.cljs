(ns xtdb-money.state
  (:require [reagent.core :as r]))

(defonce app-state (r/atom {}))

(def page (r/cursor app-state [:page]))
