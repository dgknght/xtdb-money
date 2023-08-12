(ns xtdb-money.views.entities
  (:require [secretary.core :refer-macros [defroute]]
            [reagent.core :as r]
            [dgknght.app-lib.html :as html]
            [dgknght.app-lib.forms :as forms]
            [xtdb-money.state :as state :refer [page
                                                current-entity
                                                entities]]
            [xtdb-money.api.entities :as ents]))

(defn- entity-row
  [entity page-state]
  ^{:key (str "entity-row-" (:id entity))}
  [:tr
   [:td (:name entity)]
   [:td
    [:div.btn-group {:role :group
                     :aria-label "Entity actions"}
     [:button.btn.btn-sm.btn-secondary {:type :button
                                        :on-click #(swap! page-state assoc :selected entity)}]]]])

(defn- entities-table
  [page-state]
  (fn []
    [:table.table.table-striped
     [:thead
      [:tr
       [:td "Name"]
       [:td (html/space)]]]
     [:tbody
      (->> @entities
           (map #(entity-row % page-state))
           doall)]]))

(defn- unselect-entity
  [xf page-state]
  (completing
    (fn [ch x]
      (swap! page-state dissoc :selected)
      (xf ch x))))

(defn- load-entities
  [xf]
  (completing
    (fn [ch x]
      (ents/select (map #(swap! state/app-state assoc
                                :entities %
                                :current-entity (first %))))
      (xf ch x))))

(defn- save-entity
  [page-state]
  (ents/put (get-in @page-state [:selected])
            (comp #(unselect-entity % page-state)
                  load-entities)))

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
