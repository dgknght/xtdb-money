(ns xtdb-money.notifications
  (:require [reagent.core :as r]
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

(defn alerts
  []
  (fn []
    (let [alerts (r/cursor app-state [:alerts])]
      (when (seq @alerts)

        (cljs.pprint/pprint {::alerts @alerts})

        [:div.container
         (->> @alerts
              (map (comp alert-elem
                         expand-alert))
              doall)]))))

(defn alert
  [msg & {:keys [severity]
          :or [severity :alert-danger]}]
  (swap! app-state update-in [:alerts] (fnil conj []) {:id (random-uuid)
                                                       :message msg
                                                       :severity severity}))
