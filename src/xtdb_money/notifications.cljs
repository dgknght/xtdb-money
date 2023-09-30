(ns xtdb-money.notifications
  (:require [cljs.core.async :as a]
            [reagent.core :as r]
            [dgknght.app-lib.dom :refer [debounce]]
            [xtdb-money.state :refer [app-state]]))

(defmulti ^:private expand-alert
  (fn [a]
    (when (string? a)
      :string)))

(defmethod expand-alert :string
  [a]
  (expand-alert {:message a}))

(defmethod expand-alert :default
  [a]
  (-> a
      (update-in [:severity] (fnil identity :alert-danger))))

(defn- alert-elem
  [{:keys [severity message id]}]
  ^{:key (str "alert-" id)}
  [:div.alert.alert-dismissible
   {:role :alert
    :class severity}
   message
   [:button.btn-close
    {:type :button
     :data-bs-dismiss "alert"
     :aria-label "Close"
     :on-click #(swap! app-state
                       update-in
                       [:alerts]
                       (filter (fn [a]
                                 (= id (:id a)))
                               %))}]])

(defn alerts []
  (let [alerts (r/cursor app-state [:alerts])]
    (fn []
      (when-let [alrts (seq @alerts)]
        [:div.container
         (->> alrts
              (map (comp alert-elem
                         expand-alert))
              doall)]))))

(defn alert
  [msg & {:keys [severity]
          :or [severity :alert-danger]}]
  (swap! app-state update-in [:alerts] (fnil conj []) {:id (random-uuid)
                                                       :message msg
                                                       :severity severity}))

(defn- toast-elem
  [{:keys [id message header]}]
  ^{:key (str "toast-" id)}
  [:div.toast {:role :alert
               :aria-live :assertive
               :data-bs-config {:delay 3000}
               :aria-atomic true
               :id id}
   [:div.toast-header
    [:strong.me-auto header]
    [:buton.btn-close {:type :button
                       :data-bs-dismiss :toast
                       :aria-label "Close"}]]
   [:div.toast-body
    message]])

(defn toasts []
  (let [toasts (r/cursor app-state [:toasts])]
    (fn []
      (when-let [tsts (seq @toasts)]
        [:div.toast-container.position-fixed.bottom-0.end-0.p-3
         (->> tsts
              (map toast-elem)
              doall)]))))

(defn- show-toasts* []
  (let [bs (.-bootstrap js/window)
        Toast (.-Toast bs)
        elems (.querySelectorAll js/document ".toast")]
    (.forEach elems (fn [e]
                      (.show (.getOrCreateInstance Toast e))))))

(def ^:private show-toasts
  (debounce show-toasts*))

(defn- untoast
  [{:keys [id]}]
  (a/go (a/<! (a/timeout 4000))
        (swap! app-state
               update-in
               [:toasts]
               (fn [toasts]
                 (remove #(= id (:id %))
                         toasts)))))

(defn toast
  [msg & {:keys [header]}]
  (show-toasts)
  (let [t {:id (random-uuid)
           :message msg
           :header (or header "Notice")}]
    (untoast t)
    (swap! app-state update-in [:toasts] (fnil conj []) t)))
