(ns xtdb-money.views.pages
  (:require [secretary.core :refer-macros [defroute]]
            [dgknght.app-lib.html :as html]
            [xtdb-money.state :refer [page]]))

(defn- sign-in-options []
  [:div.list-group {:style {:max-width "264px"}}
   [:a.list-group-item.list-group-item-action.d-flex.align-items-center.p-0
    {:href "/oauth/google"
     :style {:background-color "#4285F4"
             :font-weight :bold}}
    [:div.bg-light.p-2.rounded
     (html/google-g)]
    [:div.text-center.w-100 "Sign In With Google"]]])

(defn- welcome []
  (fn []
    [:div.container
     [:h1 "Welcome!"]
     [:p "There's lots of cool stuff coming soon."]
     (sign-in-options)]))

(defn- about []
  (fn []
    [:div.container
     [:h1 "About The App"]
     [:p "Some addition information about the app might be helpful here."]]))

(defn- not-found []
  [:div.container
   [:h1 "Not found"]
   [:p "The page you requested could not be found."]])

(defn- sign-in []
  [:div.container
   [:h1 "Sign In"]
   (sign-in-options)])

(defroute welcome-path "/" []
  (reset! page welcome))

(defroute about-path "/about" []
  (reset! page about))

(defroute not-found-path "/#not-found" []
  (reset! page not-found))

(defroute sign-in-path "/sign-in" []
  (reset! page sign-in))
