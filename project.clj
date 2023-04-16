(defproject xtdb-money "0.1.0-SNAPSHOT"
  :description "Double-entry accounting system build on XTDB in Clojure"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [clj-time "0.15.2"]
                 [com.xtdb/xtdb-core "1.23.1"]
                 [com.github.dgknght/app-lib "0.3.2"]]
  :main ^:skip-aot xtdb-money.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
