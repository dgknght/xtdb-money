;; This test runner is intended to be run from the command line
(ns xtdb-money.test-runner
  (:require
    ;; require all the namespaces that you want to test
    [xtdb-money.core-test]
    [figwheel.main.testing :refer [run-tests-async]]))

(defn -main [& _args]
  (run-tests-async 5000))
