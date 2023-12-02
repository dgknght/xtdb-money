(ns xtdb-money.api.users
  (:require [cljs.core.async :as a]))

(defn find-by-auth-token
  [_auth-token & {:as _opts}]
  (.warn js/console "find-by-auth-token is not implemented")
  (let [c (a/promise-chan)]
    (a/put! c {})
    c))
