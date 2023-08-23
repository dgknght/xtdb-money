(ns xtdb-money.views.pages
  (:require [secretary.core :refer-macros [defroute]]
            [xtdb-money.state :refer [page]]))

(defn- welcome []
  (fn []
    [:div.container
     [:h1 "Welcome!"]
     [:p "There's lots of cool stuff coming soon."]]))

(defn- about []
  (fn []
    [:div.container
     [:h1 "About The App"]
     [:p "Some addition information about the app might be helpful here."]]))

(defroute welcome-path "/" []
  (reset! page welcome))

(defroute about-path "/about" []
  (reset! page about))
