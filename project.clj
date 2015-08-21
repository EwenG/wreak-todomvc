(defproject ewen.wreak/todo-mvc "0.1.0"
            :description "Todo mvc demo using wreak"
            :url "https://github.com/EwenG/wreak"
            :license {:name "Eclipse Public License"
                      :url "http://www.eclipse.org/legal/epl-v10.html"}
            :min-lein-version "2.0.0"
            :source-paths ["src-cljs"]
            :resource-paths ["resources/main"]
            :dependencies [[org.clojure/clojure "1.7.0"]
                           [org.clojure/clojurescript "1.7.48"]
                           [datascript "0.11.5"]
                           [sablono "0.3.4" :exclusions
                            [cljsjs/react]]
                           [domina "1.0.3"]
                           [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                           [org.clojure/core.match "0.3.0-alpha4"]
                           [ewen/wreak "0.2.0-SNAPSHOT"]
                           [cljsjs/react "0.13.3-0"]
                           [org.clojure/tools.reader "0.10.0-alpha1"]])
