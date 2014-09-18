(defproject ewen.wreak/todo-mvc "0.1.0"
            :description "Todo mvc demo using wreak"
            :url "https://github.com/EwenG/wreak"
            :license {:name "Eclipse Public License"
                      :url "http://www.eclipse.org/legal/epl-v10.html"}
            :min-lein-version "2.0.0"
            :source-paths ["src-cljs"]
            :resource-paths ["resources/main"]
            :dependencies [[org.clojure/clojure "1.6.0"]
                           [org.clojure/clojurescript "0.0-2311"]
                           [ewen/wreak "0.1.0"]
                           [sablono "0.2.6"]
                           [domina "1.0.2"]
                           [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                           [org.clojure/core.match "0.2.1"]]
            :dev-dependencies [[lein-cljsbuild "1.0.3"]]
            :profiles {:dev {:plugins [[lein-cljsbuild "1.0.3"]]}}
            :cljsbuild {:builds [{:id "dev"
                                  :source-paths ["src-cljs" "/home/ewen/clojure/datascript/src"]
                                  :compiler {
                                              :output-to "resources/dev/cljs/todo-mvc.js"
                                              :output-dir "resources/dev/cljs/"
                                              :optimizations :none
                                              :source-map true}}]})
