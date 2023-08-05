(ns xtdb-money.views.entities
  (:require [secretary.core :refer-macros [defroute]]
            [reagent.core :as r]
            [xtdb-money.state :refer [page]]))

(defn- index []
  (let [page-state (r/atom {})]
    (fn []
      [:div.container
       [:h1 "Entities"]])))

(defroute entities-path "/entities" []
  (reset! page index))
