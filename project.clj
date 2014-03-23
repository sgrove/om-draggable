(defproject draggable "0.1.0-SNAPSHOT"
  :description "Draggable component for Om"
  :url "http://github.com/sgrove/om-draggable"

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2156"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [om "0.5.0"]]

  :plugins [[lein-cljsbuild "1.0.2"]
            [lein-simpleton "1.2.0"]]

  :source-paths ["src"]

  :cljsbuild {
    :builds [{:id "draggable"
              :source-paths ["src"]
              :compiler {
                :output-to "draggable.js"
                :output-dir "out"
                :optimizations :none
                :source-map true}}
             {:id "visualize"
              :source-paths ["src" "examples/visualize/src"]
              :compiler {
                :output-to "examples/visualize/main.js"
                :output-dir "examples/visualize/out"
                :source-map true
                :optimizations :none}}]})
