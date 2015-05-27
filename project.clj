(defproject fractals-clojurescript "DEV"


  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.3.1"]
                 [ring/ring-defaults "0.1.2"]
                 [ring/ring-jetty-adapter "1.3.2"]

                 [org.clojure/clojurescript "0.0-3211"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]

  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-figwheel "0.2.9"]
            [lein-ring "0.8.13"]]

  :source-paths ["src/clj"]

  :main  fractals-clojurescript.core

  :ring {:handler fractals-clojurescript.core/handler}

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :profiles {:uberjar {:aot :all
                        :hooks [leiningen.cljsbuild]}}

  :cljsbuild {:builds [{:id "min"
                        :source-paths ["src/cljs"]
                        :compiler {:output-to "resources/public/js/compiled/fractals.js"
                                   :main fractals-clojurescript.core
                                   :optimizations :advanced
                                   :pretty-print false}
                        :jar true}

                       {:id "dev"
                        :source-paths ["src/cljs"]

                        :figwheel {:on-jsload "fractals-clojurescript.core/on-js-reload"
                                   ;:websocket-host "192.168.3.54"
                                   }

                        :compiler {:main fractals-clojurescript.core
                                   :asset-path "js/compiled/out"
                                   :output-to "resources/public/js/compiled/fractals.js"
                                   :output-dir "resources/public/js/compiled/out"
                                   :optimizations :none


                                   :source-map true
                                   :source-map-timestamp true
                                   :cache-analysis true}}]}

  :figwheel {:css-dirs ["resources/public/css"]})
