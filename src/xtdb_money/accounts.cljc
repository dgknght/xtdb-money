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
   (* amount (polarizer action account))))

(defn debit
  [account amount]
  (update-in account [:balance] + (polarize amount :debit account)))

(defn credit
  [account amount]
  (update-in account [:balance] + (polarize amount :credit account)))
