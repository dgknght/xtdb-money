(ns xtdb-money.icons)

(defn- apply-size
  [attr {:keys [size]}]
  (merge attr (case size
                :medium {:width 32 :height 32}
                {:width 16 :height 16})))

(defn icon
  [icon-id & {:as opts}]
  [:svg.bi (apply-size {:fill "currentColor"} opts)
   [:use {:href (str "/images/bootstrap-icons.svg#"
                     (name icon-id))}]])
