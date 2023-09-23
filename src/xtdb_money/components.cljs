(ns xtdb-money.components
  (:require [clojure.string :as string]
            [xtdb-money.icons :refer [icon]]
            [xtdb-money.state :refer [current-entity
                                      entities
                                      db-strategy
                                      process-count
                                      busy?]]))

(defmulti ^:private expand-menu-item
  (fn [x]
    (cond
      (string? x)  :path
      (keyword? x) :id
      (map? x)     :map)))

(defmethod expand-menu-item :path
  [path]
  (expand-menu-item {:id (keyword path)
                     :href path
                     :caption (->> (string/split path #"[/-]+")
                                   (remove empty)
                                   (map string/capitalize)
                                   (string/join " "))}))

(defn- id->caption
  [id]
  (when id
    (-> id name string/capitalize)))

(defmethod expand-menu-item :id
  [id]
  (expand-menu-item {:id id
                     :caption (id->caption id)}))

(defmethod expand-menu-item :map
  [item]
  (-> item
      (update-in [:id] (fnil identity (random-uuid)))
      (update-in [:href] (fnil identity "#"))))

(defn- dropdown-item
  [{:keys [id caption href on-click]}]
  ^{:key (str "drop-down-item-" id)}
  [:li
   [:a.dropdown-item {:href href
                      :on-click on-click}
    caption]])

(defn- dropdown-menu
  [items]
  [:ul.dropdown-menu.text-small
   (->> items
        (map (comp dropdown-item
                   expand-menu-item))
        doall)])

(defmulti navbar-item
  (fn [{:keys [children]}]
    (when (seq children)
      :dropdown)))

(defmethod navbar-item :default
  [{:keys [caption id href on-click active?]}]
  ^{:key (str "navbar-item-" id)}
  [:li.nav-item
   [:a.nav-link
    {:class (when active? "active")
     :href href
     :on-click on-click}
    caption]])

(defmethod navbar-item :dropdown
  [{:keys [caption id children active?]}]
  ^{:key (str "navbar-item-" id)}
  [:li.nav-item {:class "dropdown"}
   [:a.nav-link.dropdown-toggle
    {:class (when active? "active")
     :href "#"
     :role :button
     :data-bs-toggle :dropdown
     :aria-expanded false}
    caption]
   (dropdown-menu children)])

(defn- navbar
  [items]
  [:ul.navbar-nav.me-auto.mb-2.mb-lg-0
   (->> items
        (map (comp navbar-item
                   expand-menu-item))
        doall)])

(defn- db-strategy-items
  [current]
  (map (fn [id]
         {:id id
          :caption (id->caption id)
          :active? (= current id)
          :on-click #(reset! db-strategy id)})
       [:xtdb :datomic :mongodb :sql]))

(defn title-bar []
  (fn []
    [:header.p-1.mb-3.border-bottom
     [:div.container
      [:nav.navbar.navbar-expand-lg
       [:div.container-fluid
        [:a.d-flex.align-items-center.mb-2.mb-lg-0.link-body-emphasis.text-decoration-none.me-2
         {:href "/"}
         (icon :cash-coin :size :large)]
        [:button.navbar-toggler.collapsed {:type :button
                                           :data-bs-toggle :collapse
                                           :data-bs-target "#nav-list"
                                           :aria-controls "nav-list"
                                           :aria-expanded false
                                           :aria-label "Toggle Navigation"}
         [:span.navbar-toggler-icon]]
        [:div#nav-list.navbar-collapse.collapse
         (navbar [{:href "/about"
                   :id :about
                   :caption "About the App"}
                  {:caption [:<>
                             (icon :database :size :small)
                             [:span.ms-1 (or (id->caption @db-strategy) "unknown db strategy")]]
                   :id :db-strategy
                   :children (db-strategy-items @db-strategy)}
                  {:caption (icon :person-circle)
                   :id :sign-out
                   :children ["/sign-out"]}])
         [:a.text-body {:href "#"} (:name @current-entity)]
         [:form.col-12.col-lg-auto.mb-2.mb-lg-0.me-lg-3
          [:div.input-group
           [:input.form-control {:type :text
                                 :placeholder "Search..."
                                 :aria-label "Search"}]
           [:button.btn.btn-outline-secondary {:type :submit}
            (icon :search :size :small)]]]]]]]]))

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
