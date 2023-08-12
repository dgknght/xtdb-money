(defproject xtdb-money "0.1.0-SNAPSHOT"
        :description "Double-entry accounting system build on XTDB in Clojure"
        :url "http://example.com/FIXME"
        :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
                  :url "https://www.eclipse.org/legal/epl-2.0/"}
        :dependencies [[org.clojure/clojure "1.11.1"]
                       [org.clojure/clojurescript "1.11.4"]
                       [hiccup "2.0.0-RC1"]
                       [com.google.guava/guava "31.0.1-jre"] ; This is here because if I let com.datomic/dev-local resolve its dependencies naturally, I end up with the *-android build instead
                       [cljsjs/react "17.0.2-0"]
                       [cljsjs/react-dom "17.0.2-0"]
                       [reagent "1.1.1" ]
                       [ring/ring-core "1.8.2"]
                       [ring/ring-jetty-adapter "1.8.2"]
                       [ring/ring-defaults "0.3.4" :exclusions [ring/ring-core ring/ring-codec crypto-equality commons-io]]
                       [ring/ring-json "0.5.1" :exclusions [ring/ring-core ring/ring-codec]]
                       [co.deps/ring-etag-middleware "0.2.1"]
                       [metosin/reitit "0.7.0-alpha5" :exclusions [com.bhauman/spell-spec
                                                                   com.cognitect/transit-clj
                                                                   com.cognitect/transit-java
                                                                   commons-fileupload
                                                                   commons-io
                                                                   crypto-equality
                                                                   expound
                                                                   org.clojure/core.rrb-vector
                                                                   org.clojure/tools.reader
                                                                   ring/ring-codec
                                                                   ring/ring-core]]
                       [venantius/accountant "0.2.5"]
                       [clj-commons/secretary "1.2.4"]
                       [clj-time "0.15.2"]
                       [ch.qos.logback/logback-classic "1.2.3"]
                       [com.xtdb/xtdb-core "1.23.1" :exclusions [org.clojure/data.json
                                                                 org.clojure/tools.reader
                                                                 org.slf4j/slf4j-api]]
                       [com.datomic/dev-local "1.0.243" :exclusions [com.cognitect/transit-clj
                                                                     com.cognitect/transit-java
                                                                     com.google.guava/guava
                                                                     org.clojure/tools.reader]]
                       [com.datomic/client-pro "1.0.76" :exclusions [org.clojure/tools.reader
                                                                     com.cognitect/transit-clj
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
                       [congomongo "2.6.0" :exclusions [org.clojure/data.json]]
                       [com.github.dgknght/app-lib "0.3.2" :exclusions [args4j
                                                                        commons-logging
                                                                        commons-io
                                                                        ring/ring-core
                                                                        ring/ring-jetty-adapter
                                                                        ring/ring-servlet]]
                       [yogthos/config "1.2.0"]]
        :main ^:skip-aot xtdb-money.core
        :target-path "target/%s"
        :resource-paths ["resources"]
        :clean-targets ^{:protect false} ["target"]
        :plugins [[lein-environ "1.2.0"]]
        :jvm-opts ["-Duser.timezone=UTC"]
        :profiles {:uberjar {:aot :all
                             :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
                   :test [:project/test :profiles/test] ; this performs a deep merge, so it's only necessary to override the specific attributes that need to change in profiles.clj
                   :project/test {:env {:db {:active "xtdb"
                                             :strategies {"datomic"
                                                          {:system "money-test"}

                                                          "sql"
                                                          {:dbname "xtdb_money_test"}

                                                          "mongodb"
                                                          {:database "money_test"}}}}}
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
                                                          :host "localhost"
                                                          :port 5432
                                                          :user "app_user"
                                                          :password "please01"}

                                                         "mongodb"
                                                         {:xtdb-money.core/provider :mongodb
                                                          :database "money_development"}}}}
                                 :dependencies [[org.clojure/data.zip "1.0.0"]
                                                [org.eclipse.jetty/jetty-server "9.4.36.v20210114"]
                                                [org.eclipse.jetty.websocket/websocket-servlet "9.4.36.v20210114"]
                                                [org.eclipse.jetty.websocket/websocket-server "9.4.36.v20210114"]
                                                [ring/ring-mock "0.4.0"]
                                                [com.bhauman/figwheel-main "0.2.18" :exclusions [org.clojure/data.json
                                                                                                 org.eclipse.jetty/jetty-http
                                                                                                 org.eclipse.jetty/jetty-io
                                                                                                 org.eclipse.jetty/jetty-server
                                                                                                 org.eclipse.jetty/jetty-util
                                                                                                 org.slf4j/slf4j-api
                                                                                                 ring
                                                                                                 ring/ring-codec
                                                                                                 ring/ring-core
                                                                                                 ring/ring-devel]]
                                                [org.slf4j/slf4j-nop "1.7.30" :exclusions [org.slf4j/slf4j-api]]
                                                [com.bhauman/rebel-readline-cljs "0.1.4"]]}}
        :repl-options {:init-ns xtdb-money.repl
                       :welcome (println "Welcome to money management with persistent data!")}
        :aliases {"migrate"       ["run" "-m" "xtdb-money.models.sql.migrations/migrate"]
                  "rollback"      ["run" "-m" "xtdb-money.models.sql.migrations/rollback"]
                  "remigrate"     ["run" "-m" "xtdb-money.models.sql.migrations/remigrate"]
                  "routes"        ["run" "-m" "xtdb-money.handler/print-routes"]
                  "index-mongodb" ["run" "-m" "xtdb-money.models.mongodb.indexes/ensure"]
                  "fig:build"     ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
                  "fig:min"       ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]
                  "fig:test"      ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" "xtdb-money.test-runner"]})
