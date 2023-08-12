(ns xtdb-money.views.entities
  (:require [secretary.core :refer-macros [defroute]]
            [reagent.core :as r]
            [dgknght.app-lib.html :as html]
            [dgknght.app-lib.forms :as forms]
            [xtdb-money.state :refer [page
                                      current-entity
                                      entities]]))

(defn- entity-row
  [entity page-state]
  ^{:key (str "entity-row-" (:id entity))}
  [:tr
   [:td (:name entity)]])

(defn- entities-table
  [page-state]
  (fn []
    [:table.table.table-striped
     [:thead
      [:tr
       [:td "Name"]
       [:td (html/special-char :nbsp)]]]
     [:tbody
      (->> @entities
           (map #(entity-row % page-state))
           doall)]]))

(defn- save-entity
  [page-state]
  (cljs.pprint/pprint {::save-entity (get-in @page-state [:selected])}))

(defn- entity-form
  [page-state]
  (let [selected (r/cursor page-state [:selected])]
    (fn []
      [:form {:no-validate true
              :on-submit (fn [e]
                           (.preventDefault e)
                           (save-entity page-state))}
       [forms/text-field selected [:name]]
       [:div
        [:button.btn.btn-primary {:type :submit}
         "Save"]
        [:button.btn.btn-secondary.ms-2 {:type :button
                                         :on-click #(swap! page-state dissoc :selected)}
         "Cancel"]]])))

(defn- index []
  (let [page-state (r/atom {})
        selected (r/cursor page-state [:selected])]
    (fn []
      [:div.container
       [:h1 "Entities"]
       [:div.row
        [:div.col-md-6
         [entities-table page-state]
         [:div
          [:button.btn.btn-primary {:type :button
                                    :on-click #(swap! page-state assoc :selected {})}
           "Add"]]]
        [:div.col-md-6
         (when @selected
           [entity-form page-state])]]])))

(defroute entities-path "/entities" []
  (reset! page index))
