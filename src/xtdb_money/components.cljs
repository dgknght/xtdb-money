(ns xtdb-money.components
  (:require [xtdb-money.icons :refer [icon]]))

(defn title-bar []
  [:header.p-3.mb-3.border-bottom
   [:div.container
    [:div.d-flex.flex-wrap.align-items-center.justify-content-center.justify-content-lg-start
     [:a.d-flex.align-items-center.mb-2.mb-lg-0.link-body-emphasis.text-decoration-none.me-2
      {:href "/"}
      (icon :cash-coin :size :medium)]
     [:ul.nav.col-12.col-lg-auto.me-lg-auto.mb-2.mb-lg-0.justify-content-center
      [:li [:a.nav-link.px-2.link-secondary {:href "/"} "Home"]]
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
