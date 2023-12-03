(ns xtdb-money.core
  (:require [cljs.pprint :refer [pprint]]
            [goog.dom :as gdom]
            [accountant.core :as act]
            [secretary.core :as sct]
            [reagent.dom :as rdom]
            [dgknght.app-lib.forms :as forms]
            [dgknght.app-lib.bootstrap-5 :as bs]
            [dgknght.app-lib.dom :refer [debounce]]
            [xtdb-money.state :as state :refer [page
                                                current-user
                                                db-strategy
                                                +busy
                                                -busy]]
            [xtdb-money.components :refer [title-bar
                                           entity-drawer]]
            [xtdb-money.notifications :refer [alerts
                                              toasts]]
            [xtdb-money.api.entities :as ents]
            [xtdb-money.api.users :as usrs]
            [xtdb-money.views.pages]
            [xtdb-money.views.entities]))

(swap! forms/defaults assoc-in [::forms/decoration ::forms/framework] ::bs/bootstrap-5)

(defn get-app-element []
  (gdom/getElement "app"))

(defn- full-page []
  (fn []
    [:div
     [title-bar]
     [alerts]
     [toasts]
     [@page]
     [entity-drawer]]))

(defn mount [el]
  (rdom/render [full-page] el))

(defn mount-app-element
  "Mounts the app in the DEV with id=\"app\", if it is found"
  []
  (when-let [el (get-app-element)]
    (mount el)))

(defn- load-entities* []
  (+busy)
  (ents/select
    :callback -busy
    :on-success (fn [entities]
                  (if (coll? entities)
                    (do
                      (swap! state/app-state
                             assoc
                             :entities entities
                             :current-entity (first entities))
                      (when (empty? entities)
                        (sct/dispatch! "/entities")))
                    (pprint {::invalid-entities entities})))))

(def ^:private load-entities
  (debounce load-entities*))

(defn- fetch-user []
  (when @state/auth-token
    (+busy)
    (usrs/me :on-success #(reset! current-user %)
             :callback -busy)))

(defn init! []
  (act/configure-navigation!
    {:nav-handler #(sct/dispatch! %)
     :path-exists? #(sct/locate-route-value %)})
  (act/dispatch-current!)
  (mount-app-element)
  (add-watch db-strategy
             ::init
             (fn [& args]
               (when-let [s (last args)]
                 (when-not (= s @db-strategy)
                   (.log js/console (str "db strategy changed to " (last args)))
                   (load-entities)))))
  (add-watch current-user
             ::init
             (fn [& args]
               (when (last args)
                 (load-entities))))
  (fetch-user))

(init!)

;; specify reload hook with ^:after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
