(ns xtdb-money.core
  (:require [goog.dom :as gdom]
            [accountant.core :as act]
            [secretary.core :as sct]
            [reagent.dom :as rdom]
            [dgknght.app-lib.forms :as forms]
            [dgknght.app-lib.bootstrap-5 :as bs]
            [dgknght.app-lib.api :as api]
            [xtdb-money.state :as state :refer [page
                                                db-strategy]]
            [xtdb-money.api :refer [handle-error]]
            [xtdb-money.components :refer [title-bar
                                           entity-drawer]]
            [xtdb-money.notifications :refer [alerts]]
            [xtdb-money.api.entities :as ents]
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
    :transform (fn [xf]
                 (completing
                   (fn [ch entities]
                     (cljs.pprint/pprint {::transform entities})
                     (xf ch entities))))
    :callback (fn [entities]
                (if (coll? entities)
                  (do
                    (swap! state/app-state assoc
                           :entities entities
                           :current-entity (first entities))
                    (when (empty? entities)
                      (sct/dispatch! "/entities")))
                  (cljs.pprint/pprint {::invalid-entities entities})))))

(defn init! []
  (act/configure-navigation!
    {:nav-handler #(sct/dispatch! %)
     :path-exists? #(sct/locate-route-value %)})
  (act/dispatch-current!)
  (mount-app-element)
  (add-watch db-strategy
             ::init
             (fn [_ _ _before after]
               (swap! api/defaults assoc-in [:headers "DB-Strategy"] after)
               (load-entities)))
  (reset! db-strategy :xtdb)) ; TODO: get this from config

(init!)

;; specify reload hook with ^:after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
