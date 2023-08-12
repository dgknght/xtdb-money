(ns xtdb-money.views.pages
  (:require [secretary.core :refer-macros [defroute]]
            [xtdb-money.state :refer [page
                                      entities]]))

(defn- welcome []
  (fn []
    [:div.container
     [:h1 "Welcome!"]
     (when (empty? @entities)
       [:p "It looks like this is your first time here. "
        [:a {:href "/entities"} "Click here "]
        "to create your first entity and get started recording your finances."])]))

(defn- about []
  (fn []
    [:div.container
     [:h1 "About The App"]
     [:p "Some addition information about the app might be helpful here."]]))

(defroute welcome-path "/" []
  (reset! page welcome))

(defroute about-path "/about" []
  (reset! page about))
