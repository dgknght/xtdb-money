(ns xtdb-money.views.entities
  (:require [secretary.core :refer-macros [defroute]]
            [reagent.core :as r]
            [goog.string :refer [format]]
            [dgknght.app-lib.dom :as dom]
            [dgknght.app-lib.html :as html]
            [dgknght.app-lib.forms :as forms]
            [xtdb-money.state :as state :refer [page
                                                app-state
                                                current-entity
                                                entities
                                                +busy
                                                -busy-xf
                                                busy?]]
            [xtdb-money.components :refer [icon-button]]
            [xtdb-money.icons :refer [icon
                                      icon-with-text]]
            [xtdb-money.notifications :refer [alert]]
            [xtdb-money.api.entities :as ents]))

(defn- receive-entities
  [entities]
  (let [current-id (:id @current-entity)
        new-current (->> entities
                         (filter #(= (:id %)
                                    current-id))
                         first)]
    (swap! app-state assoc
           :entities entities
           :current-entity new-current)))

(def load-entities
  (map (fn [x]
         (+busy)
         (ents/select :post-xf -busy-xf
                      :callback receive-entities)
         x)))

(defn- confirm?
  [msg-fmt & args]
  (js/confirm (apply format msg-fmt args)))

(defn- delete-entity
  [entity _page-state]
  (when (confirm? "Are you sure you want to delete the entity \"%s\"?"
                  (:name entity))
    (+busy)
    (ents/delete entity
                 :post-xf (comp load-entities
                                -busy-xf)
                 :callback #(cljs.pprint/pprint {::deleted entity}))))

(defn- entity-row
  [entity page-state]
  (let [css-class (when (= @current-entity entity) "bg-primary-subtle")]
    ^{:key (str "entity-row-" (:id entity))}
    [:tr
     [:td {:class css-class}
      (:name entity)]
     [:td.text-end {:class css-class}
      [:div.btn-group {:role :group
                       :aria-label "Entity actions"}
       [:button.btn.btn-sm.btn-secondary
        {:type :button
         :title "Click here to edit the entity."
         :on-click #(swap! page-state assoc :selected entity)}
        (icon :pencil :size :small)]
       [:button.btn.btn-sm.btn-info
        {:type :button
         :disabled (= @current-entity entity)
         :title "Click here to make this the active entity."
         :on-click #(reset! current-entity entity)}
        (icon :box-arrow-in-right :size :small)]
       [:button.btn.btn-sm.btn-danger
        {:type :button
         :title "Click here to remove this entity."
         :on-click #(delete-entity entity page-state)}
        (icon :trash :size :small)]]]]))

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
  [page-state]
  (map (fn [x]
         (swap! page-state dissoc :selected)
         x)))

(defn- save-entity
  [page-state]
  (+busy)
  (ents/put (get-in @page-state [:selected])
            :post-xf [(unselect-entity page-state)
                      -busy-xf
                      load-entities]
            :callback #(cljs.pprint/pprint {::save-entity %}))) ; TODO: Change this to a toast

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
          [icon-button :plus
           {:caption "Add"
            :html {:class "btn-primary"
                   :on-click (fn [_]
                               (swap! page-state assoc :selected {})
                               (dom/set-focus "name"))}}]]]
        [:div.col-md-6
         (when @selected
           [entity-form page-state])]]])))

(defroute entities-path "/entities" []
  (reset! page index))
