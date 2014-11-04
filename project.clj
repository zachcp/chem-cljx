(defproject chem-cljx "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [prismatic/schema   "0.2.6"]
                 [prismatic/plumbing "0.3.3"]
                 [instaparse "1.3.4"]
                 [com.lucasbradstreet/instaparse-cljs "1.3.4.2" :exclusions [org.clojure/clojure]]
                 [com.keminglabs/cljx "0.4.0" :exclusions [org.clojure/clojure]]]
  :main ^:skip-aot chem-cljx.core
  :target-path "target/%s"
  :profiles
    {:dev {:dependencies
         [[org.clojure/clojurescript "0.0-2342"]]

         :plugins
         [[lein-cljsbuild "1.0.3"]
          [com.keminglabs/cljx "0.4.0"]]}

     :uberjar {:aot :all}}

  :cljx
    {:builds [{:source-paths ["src/cljx"]
               :output-path "target/generated-src/clj"
               :rules :clj}
              {:source-paths ["src/cljx"]
               :output-path "target/generated-src/cljs"
               :rules :cljs}
              {:source-paths ["test"]
               :output-path "target/generated-test"
               :rules :clj}
              {:source-paths ["test"]
               :output-path "target/generated-test"
               :rules :cljs}]}
  :cljsbuild
    {:builds
       [{:source-paths ["target/generated-src/clj"
                        "target/generated-src/cljs"
                        "target/generated-test"]
       ;; Running `cljsbuild <once|auto>` will trigger this test.
       :notify-command ["phantomjs" :cljs.test/runner "target/cljs/testable.js"]
       :compiler {:output-to "target/cljs/testable.js"
                  :optimizations :whitespace
                  :pretty-print true}}]})