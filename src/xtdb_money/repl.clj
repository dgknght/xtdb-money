(ns xtdb-money.repl
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [xtdb-money.handler :refer [app]]))

(def server (atom nil))

(defn start-server
  [& {:as opts}]
  (let [options (merge {:port 3000
                        :join? false}
                       opts)]
    (if @server
      (println "A server is already running. Stop it with (stop-server)")
      (do
        (reset! server
                (run-jetty app options))
        (println (format "The server is listening on port %s" (:port options)))))))

(defn stop-server []
  (if-let [srv @server]
    (do
      (.stop srv)
      (reset! server nil)
      (println "The service has been stopped."))
    (println "No server is running.")))
