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
  ([{:keys [amount action account]}]
   (polarize amount action account))
  ([amount action account]
   {:pre [amount
          (#{:debit :credit} action)
          (:type account)]}
   (* amount (polarizer action account))))
