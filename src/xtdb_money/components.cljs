(ns xtdb-money.components
  (:require [clojure.string :as string]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [xtdb-money.icons :refer [icon]]
            [xtdb-money.state :refer [current-entity
                                      entities
                                      db-strategy
                                      busy?]]))

(defmulti ^:private expand-menu-item
  (fn [x]
    (cljs.pprint/pprint {::expand-menu-item x})
    (type x)))

(defmethod expand-menu-item :caption
  [caption]
  (expand-menu-item {:id (->kebab-case-keyword caption)
                     :caption caption}))

(defn- id->caption
  [id]
  (-> id name string/capitalize))

(defmethod expand-menu-item :id
  [id]
  (expand-menu-item {:id id
                         :caption (id->caption id)}))

(defmethod expand-menu-item PersistentHashMap
  [item]
  (update-in item [:href] (fnil identity "#")))

(defn- dropdown-item
  [{:keys [id path on-click caption]}]
  ^{:key (str "dropdown-item-" id)}
  [:li
   [:a.dropdown-item {:href path
                      :on-click on-click}
    caption]])

(defn- dropdown-menu
  [items]
  [:ul.dropdown-menu.text-small
   (->> items
        (map (comp dropdown-item
                   expand-menu-item))
        doall)])

(defn- navbar-item
  [{:keys [caption id href on-click children active?]}]
  ^{:key (str "navbar-item-" id)}
  [:li.nav-item {:class (when (seq children) "dropdown")}
   [:a.nav-link
    {:class [(when active? "active")
             (when (seq children) "dropdown")]
     :href href
     :on-click on-click}
    caption]
   (when (seq children)
     (dropdown-menu children))])

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
          :on-click (reset! db-strategy id)})
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
         #_(navbar [{:href "/about"
                   :caption "About the App"}
                  {:caption [:<>
                               (icon :database :size :small)
                               [:span.ms-1 "DB Strategy"]]
                     :children (db-strategy-items @db-strategy)}
                  {:caption (icon :person-circle)
                     :children ["/sign-out"]}])
         #_[:ul.navbar-nav.me-auto.mb-2.mb-lg-0
            [:li.nav-item [:a.nav-link {:href "/about"} "About The App"]]
            [:li.nav-item.dropdown
             [:a.nav-link.dropdown-toggle
              {:href "#"
               :data-bs-toggle :dropdown
               :aria-expanded false}
              (icon :database :size :small)
              [:span.ms-1 "DB Strategy"]]
             (dropdown-menu [:xtdb :datomic :sql :monbodb])]
            [:li.nav-item.dropdown
             [:a.nav-link.dropdown-toggle
              {:href "#"
               :data-bs-toggle :dropdown
               :aira-expanded false}
              (icon :person-circle :size :medium)]
             [dropdown-menu ["Sign In"]]]]
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
