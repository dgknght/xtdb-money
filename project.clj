(defproject xtdb-money "0.1.0-SNAPSHOT"
        :description "Double-entry accounting system build on XTDB in Clojure"
        :url "http://example.com/FIXME"
        :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
                  :url "https://www.eclipse.org/legal/epl-2.0/"}
        :dependencies [[org.clojure/clojure "1.11.1"]
                       [clj-time "0.15.2"]
                       [com.xtdb/xtdb-core "1.23.1"]
                       [com.datomic/dev-local "1.0.243" :exclusions [com.cognitect/transit-clj
                                                                     com.cognitect/transit-java]]
                       [com.datomic/client-pro "1.0.76" :exclusions [com.cognitect/transit-clj
                                                                     com.cognitect/transit-java
                                                                     com.datomic/client
                                                                     com.datomic/client-api
                                                                     com.datomic/client-impl-shared
                                                                     org.eclipse.jetty/jetty-http
                                                                     org.eclipse.jetty/jetty-io
                                                                     org.eclipse.jetty/jetty-util]]
                       [com.github.seancorfield/next.jdbc "1.3.874"]
                       [org.postgresql/postgresql "42.6.0" :exclusions [org.checkerframework/checker-qual]]
                       [dev.weavejester/ragtime "0.9.3"]
                       [com.github.seancorfield/honeysql "2.4.1033"]
                       [com.github.dgknght/app-lib "0.3.2" :exclusions [args4j]]
                       [yogthos/config "1.2.0"]]
        :main ^:skip-aot xtdb-money.core
        :target-path "target/%s"
        :plugins [[lein-environ "1.2.0"]]
        :jvm-opts ["-Duser.timezone=UTC"]
        :profiles {:uberjar {:aot :all
                             :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
                   :test [:project/test :profiles/test] ; this performs a deep merge, so it's only necessary to override the specific attributes that need to change in profiles.clj
                   :project/test {:env {:db {:active "xtdb"
                                             :strategies {"datomic"
                                                          {:system "money-test"}

                                                          "sql"
                                                          {:dbname"xtdb_money_test"}}}}}
                   :dev [:project/dev :profiles/dev]
                   :project/dev {:env {:db {:active "xtdb"
                                            :strategies {"datomic"
                                                         {:xtdb-money.core/provider :datomic
                                                          :server-type :dev-local
                                                          :system "money-dev"
                                                          :storage-dir "/Users/dknight/.datomic-storage"}

                                                         "xtdb"
                                                         {:xtdb-money.core/provider :xtdb}

                                                         "sql"
                                                         {:xtdb-money.core/provider :sql
                                                          :dbtype "postgresql"
                                                          :dbname "xtdb_money_development"
                                                          :user "app_user"
                                                          :password "please01"}}}}}}
        :repl-options {:init-ns xtdb-money.repl
                       :wilcome (println "Welcome to money management with persistent data!")}
        :aliases {"migrate" ["run" "-m" "xtdb-money.models.sql.migrations/migrate"]
                  "rollback" ["run" "-m" "xtdb-money.models.sql.migrations/rollback"]
                  "remigrate" ["run" "-m" "xtdb-money.models.sql.migrations/remigrate"]})
