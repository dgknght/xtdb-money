(ns xtdb-money.core
  (:require [goog.dom :as gdom]
            [accountant.core :as act]
            [secretary.core :as sct]
            [reagent.dom :as rdom]
            [dgknght.app-lib.forms :as forms]
            [dgknght.app-lib.bootstrap-5 :as bs]
            [dgknght.app-lib.api :as api]
            [xtdb-money.state :as state :refer [page]]
            [xtdb-money.components :refer [title-bar
                                           entity-drawer]]
            [xtdb-money.api.entities :as ents]
            [xtdb-money.views.pages]
            [xtdb-money.views.entities]))

(swap! forms/defaults assoc-in [::forms/decoration ::forms/framework] ::bs/bootstrap-5)
(swap! api/defaults assoc :extract-body :before)

(defn get-app-element []
  (gdom/getElement "app"))

(defn- full-page []
  (fn []
    [:div
     [title-bar]
     [@page]
     [entity-drawer]]))

(defn mount [el]
  (rdom/render [full-page] el))

(defn mount-app-element
  "Mounts the app in the DEV with id=\"app\", if it is found"
  []
  (when-let [el (get-app-element)]
    (mount el)))

(defn- load-entities []
  (ents/select
    (map (fn [entities]
           (swap! state/app-state assoc
                  :entities entities
                  :current-entity (first entities))
           entities))))

(defn init! []
  (act/configure-navigation!
    {:nav-handler #(sct/dispatch! %)
     :path-exists? #(sct/locate-route-value %)})
  (act/dispatch-current!)
  (mount-app-element)
  (load-entities))

(init!)


;; specify reload hook with ^:after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
