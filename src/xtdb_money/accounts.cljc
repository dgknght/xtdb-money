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
  [amount action account]
  (* amount (polarizer action account)))
