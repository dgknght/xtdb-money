(ns xtdb-money.accounts)

(defn left-side?
  [{:keys [type]}]
  (#{:asset :expense} type))

(defn- polarizer
  [action account]
  (if (left-side? account)
    (if (= :credit action) -1M 1M)
    (if (= :credit action) 1M -1M)))

(defn polarize
  ([{:keys [quantity action account]}]
   (polarize quantity action account))
  ([quantity action account]
   {:pre [quantity
          (#{:debit :credit} action)
          (:type account)]}
   (* quantity (polarizer action account))))
