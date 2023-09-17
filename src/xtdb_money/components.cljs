(ns xtdb-money.components
  (:require [clojure.string :as string]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [xtdb-money.icons :refer [icon]]
            [xtdb-money.state :refer [current-entity
                                      entities
                                      db-strategy
                                      busy?]]))

(defmulti ^:private expand-dropdown-item
  (fn [i]
    (cond
      (string? i) :caption
      (keyword? i) :id)))

(defmethod expand-dropdown-item :caption
  [caption]
  (expand-dropdown-item {:id (->kebab-case-keyword caption)
                          :caption caption}))

(defn- id->caption
  [id]
  (-> id name string/capitalize))

(defmethod expand-dropdown-item :id
  [id]
  (expand-dropdown-item {:id id
                         :caption (id->caption id)}))

(defmethod expand-dropdown-item :default
  [item]
  (update-in item [:href] (fnil identity "#")))

(defn- dropdown-item
  [{:keys [id path on-click caption]}]
  ^{:key (str "dropdown-item-" id)}
  [:li
   [:a.dropdown-item {:href path
                      :on-click on-click}
    caption]])

(defn- dropdown-ul
  [items]
  [:ul.dropdown-menu.text-small
   (->> items
        (map (comp dropdown-item
                   expand-dropdown-item))
        doall)])

(defn title-bar []
  (fn []
    [:header.p-3.mb-3.border-bottom
     [:div.container
      [:div.d-flex.flex-wrap.align-items-center.justify-content-center.justify-content-lg-start
       [:a.d-flex.align-items-center.mb-2.mb-lg-0.link-body-emphasis.text-decoration-none.me-2
        {:href "/"}
        (icon :cash-coin :size :large)]
       (when-let [e @current-entity]
         [:a.text-decoration-none.link-body-emphasis.fs-3.mx-3
          {:data-bs-toggle "offcanvas"
           :href "#entity-drawer"
           :role :button
           :aria-controls "entity-drawer"}
          (:name e)])
       [:ul.nav.col-12.col-lg-auto.me-lg-auto.mb-2.mb-lg-0.justify-content-center
        [:li [:a.nav-link.px-2.link-secondary {:href "/about"} "About The App"]]]
       [:div.dropdown.text-end.me-lg-3
        [:a.d-block.link-body-emphasis.text-decoration-none.dropdown-toggle
         {:data-bs-toggle :dropdown
          :aria-expanded false}
         (icon :database :size :medium)
         [:span.ms-2 (when-let [s @db-strategy]
                         (name s))]]
        (dropdown-ul (->> [:xtdb :datomic :sql :mongodb]
                          (map (fn [id]
                                 {:id id
                                  :caption (id->caption id)
                                  :on-click #(reset! db-strategy id)}))))]
       [:form.col-12.col-lg-auto.mb-2.mb-lg-0.me-lg-3
        [:input.form-control {:type :text
                              :placeholder "Search..."
                              :aria-label "Search"}]]
       [:div.dropdown.text-end
        [:a.d-block.link-body-emphasis.text-decoration-none.dropdown-toggle
         {:data-bs-toggle :dropdown
          :aria-expanded false}
         (icon :person-circle :size :medium)]
        (dropdown-ul ["Sign In"])]]]]))

(defn entity-drawer []
  (fn []
    [:div#entity-drawer.offcanvas.offcanvas-top
     {:tab-index "-1"
      :aria-labelledby "entity-drawer-label"}
     [:div.offcanvas-header
      [:h5#entity-drawer-label.offcanvas-title "Entity Selection"]
      [:button.btn-close {:data-bs-dismiss "offcanvas"}]]
     [:div.offcanvas-body
      [:div.row
       [:div.col-md-6
        [:div.list-group
         (when-let [es (seq @entities)]
           (->> es
                (map (fn [e]
                       ^{:key (str "entity-selector-" (:id e))}
                       [:a.list-group-item.list-group-item-action
                        {:href "#"
                         :class (when (= @current-entity e) "active")
                         :on-click #(reset! current-entity e)
                         :data-bs-dismiss "offcanvas"}
                        (:name e)]))
                doall))]]
       [:div.col-md-6
        [:ul.nav.flex-column
         [:li.nav-link [:a.nav-link
                        {:href "/entities"
                         :data-bs-dismiss "offcanvas"}
                        "Manage Entities"]]]]]]]))

(def ^:private spinner-size-css
  {:small "spinner-border-sm"})

(defn spinner
  [& {:keys [size]
      :or {size :medium}}]
  [:span.spinner-border
   (cond-> {:role :status}
     size (assoc :class (spinner-size-css size)))
   [:span.visually-hidden "Please wait"]])

(defn icon-button
  [icon-id {:as opts :keys [html caption]}]
  (fn []
    [:button.btn (merge {:type :button}
                        html)
     (if @busy?
       (spinner :size :small)
       (apply icon icon-id opts))
     (when caption
       [:span.ms-2 caption])]))
