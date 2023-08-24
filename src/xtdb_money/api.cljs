(ns xtdb-money.api)

(defn handle-error
  [error]
  (.error js/console "Unable to get a list of entities from the server.")
  (.dir js/console error))
