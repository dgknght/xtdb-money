(defproject xtdb-money "0.1.0-SNAPSHOT"
        :description "Double-entry accounting system build on XTDB in Clojure"
        :url "http://example.com/FIXME"
        :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
                  :url "https://www.eclipse.org/legal/epl-2.0/"}
        :dependencies [[org.clojure/clojure "1.11.1"]
                       [clj-time "0.15.2"]
                       [com.xtdb/xtdb-core "1.23.1"]
                       [com.datomic/dev-local "1.0.243"]
                       [com.datomic/client-pro "1.0.76"]
                       [com.github.dgknght/app-lib "0.3.2"]
                       [yogthos/config "1.2.0"]]
        :main ^:skip-aot xtdb-money.core
        :target-path "target/%s"
        :plugins [[lein-environ "1.2.0"]]
        :profiles {:uberjar {:aot :all
                             :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
                   :test [:project/test :profiles/test] ; this performs a deep merge, so it's only necessary to override the specific attributes that need to change in profiles.clj
                   :project/test {:env {:db {:active "xtdb"
                                             :strategies {"datomic"
                                                          {:xtdb-money.core/provider :datomic
                                                           :server-type :dev-local
                                                           :system "money-test"
                                                           :storage-dir "/Users/dknight/.datomic-storage"}

                                                          #_"xtdb"
                                                          #_{:xtdb-money.core/provider :xtdb}}}}}
                   :dev [:project/dev :profiles/dev]
                   :project/dev {:env {:db {:active "xtdb"
                                            :strategies {"datomic"
                                                         {:xtdb-money.core/provider :datomic
                                                          :server-type :dev-local
                                                          :system "money-dev"
                                                          :storage-dir "/Users/dknight/.datomic-storage"}

                                                         #_"xtdb"
                                                         #_{:xtdb-money.core/provider :xtdb}}}}}}
        :repl-options {:init-ns xtdb-money.repl
                       :wilcome (println "Welcome to money management with persistent data!")})
