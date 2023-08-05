(ns xtdb-money.core
  (:require [goog.dom :as gdom]
            [accountant.core :as act]
            [secretary.core :as sct :refer-macros [defroute]]
            [reagent.dom :as rdom]
            [xtdb-money.state :refer [page]]
            [xtdb-money.components :refer [title-bar]]
            [xtdb-money.views.pages]))

(defn get-app-element []
  (gdom/getElement "app"))

(defn- full-page []
  (fn []
    [:div
     [title-bar]
     [@page]]))

(defn mount [el]
  (rdom/render [full-page] el))

(defn mount-app-element
  "Mounts the app in the DEV with id=\"app\", if it is found"
  []
  (when-let [el (get-app-element)]
    (mount el)))

(defn init! []
  (act/configure-navigation!
    {:nav-handler #(sct/dispatch! %)
     :path-exists? #(sct/locate-route-value %)})
  (act/dispatch-current!)
  (mount-app-element))

(init!)


;; specify reload hook with ^:after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
