(ns xtdb-money.components
  (:require [xtdb-money.icons :refer [icon]]
            [xtdb-money.state :refer [current-entity
                                      entities]]))

(defn title-bar []
  [:header.p-3.mb-3.border-bottom
   [:div.container
    [:div.d-flex.flex-wrap.align-items-center.justify-content-center.justify-content-lg-start
     [:a.d-flex.align-items-center.mb-2.mb-lg-0.link-body-emphasis.text-decoration-none.me-2
      {:href "/"}
      (icon :cash-coin :size :medium)]
     (when-let [e @current-entity]
       [:a.text-decoration-none.link-body-emphasis.fs-3.mx-3
        {:data-bs-toggle "offcanvas"
         :href "#entity-drawer"
         :role :button
         :aria-controls "entity-drawer"}
        (:name e)])
     [:ul.nav.col-12.col-lg-auto.me-lg-auto.mb-2.mb-lg-0.justify-content-center
      [:li [:a.nav-link.px-2.link-secondary {:href "/about"} "About The App"]]]
     [:form.col-12.col-lg-auto.mb-2.mb-lg-0.me-lg-3
      [:input.form-control {:type :text
                            :placeholder "Search..."
                            :aria-label "Search"}]]
     [:div.dropdown.text-end
      [:a.d-block.link-body-emphasis.text-decoration-none.dropdown-toggle
       {:data-bs-toggle :dropdown
        :aria-expanded false}
       (icon :person-circle :size :medium)]
      [:ul.dropdown-menu.text-small
       [:li [:a.dropdown-item {:href "#"} "Sign In"]]]]]]])

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
