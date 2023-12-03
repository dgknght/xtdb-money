(ns xtdb-money.tokens
  (:require [buddy.sign.jwt :as jwt]
            [config.core :refer [env]]))

(defn encode
  [data]
  (assert (:app-secret env) "Missing :app-secret configuration")
  (jwt/sign data (:app-secret env)))

(defn decode
  [token]
  (assert (:app-secret env) "Missing :app-secret configuration")
  (when token
    (jwt/unsign token (:app-secret env))))
