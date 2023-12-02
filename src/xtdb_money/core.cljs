(ns xtdb-money.core
  (:require [cljs.pprint :refer [pprint]]
            [goog.dom :as gdom]
            [accountant.core :as act]
            [secretary.core :as sct]
            [reagent.dom :as rdom]
            [reagent.cookies :as cookies]
            [dgknght.app-lib.forms :as forms]
            [dgknght.app-lib.bootstrap-5 :as bs]
            [dgknght.app-lib.api :as api]
            [dgknght.app-lib.dom :refer [debounce]]
            [xtdb-money.state :as state :refer [page
                                                current-user
                                                db-strategy
                                                +busy
                                                -busy]]
            [xtdb-money.api :refer [handle-error]]
            [xtdb-money.components :refer [title-bar
                                           entity-drawer]]
            [xtdb-money.notifications :refer [alerts
                                              toasts]]
            [xtdb-money.api.entities :as ents]
            [xtdb-money.api.users :as usrs]
            [xtdb-money.views.pages]
            [xtdb-money.views.entities]))

(swap! forms/defaults assoc-in [::forms/decoration ::forms/framework] ::bs/bootstrap-5)
(swap! api/defaults assoc
       :accept :json
       :extract-body :before
       :handle-ex handle-error)

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
  (when (and @db-strategy @current-user)
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
                      (pprint {::invalid-entities entities}))))))

(def ^:private load-entities
  (debounce load-entities*))

(defn- fetch-user []
  (when-let [auth-token (cookies/get :auth-token)]
    (reset! state/auth-token auth-token)
    (usrs/find-by-auth-token auth-token
                             :on-success #(reset! current-user %))))

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
  (when-not @db-strategy
    (.log js/console (str "set initial db strategy to xtdb"))
    (reset! db-strategy :xtdb))
  (fetch-user)) ; TODO: get this from config

(init!)

;; specify reload hook with ^:after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
