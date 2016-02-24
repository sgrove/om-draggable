(defproject draggable "0.1.0-SNAPSHOT"
  :description "Draggable component for Om"
  :url "http://github.com/sgrove/om-draggable"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [org.clojure/core.async "0.2.374"]
                 [org.omcljs/om "0.9.0"]]

  :plugins [[lein-cljsbuild "1.1.2"]
            [lein-figwheel "0.5.0-3"]]

  :source-paths ["src"]

  :cljsbuild { 
    :builds [{:id "visualize"
              :source-paths ["src" "examples/visualize/src"]
              :figwheel true
              :compiler {
                :output-to "resources/public/examples/visualize/main.js"
                :output-dir "resources/public/examples/visualize/out"
                :source-map true
                :optimizations :none}}
             {:id "draggable"
              :source-paths ["src"]
              :compiler {
                :output-to "resources/public/draggable.js"
                :output-dir "resources/public/out"
                :optimizations :none
                :source-map true}}
             ]})
