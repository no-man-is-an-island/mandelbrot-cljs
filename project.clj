(defproject mandelbrot-cljs "DEV"


  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.3.1"]
                 [ring/ring-defaults "0.1.2"]
                 [ring/ring-jetty-adapter "1.3.2"]

                 [org.clojure/clojurescript "0.0-3211"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]

  :plugins [[lein-figwheel "0.2.9"]]

  :source-paths ["src/clj"]

  :main  mandelbrot-cljs.core

  :ring {:handler mandelbrot-cljs.core/handler}

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :profiles {:dev     {:plugins [[lein-ring "0.8.13"]
                                 [lein-cljsbuild "1.0.5"]]}
             :uberjar {:aot :all
                       :hooks [leiningen.cljsbuild]}}

  :cljsbuild {:builds [{:id "min"
                        :source-paths ["src/cljs"]
                        :compiler {:output-to "resources/public/js/compiled/fractals.js"
                                   :main mandelbrot-cljs.core
                                   :optimizations :advanced
                                   :pretty-print false}
                        :jar true}

                       {:id "dev"
                        :source-paths ["src/cljs"]

                        :figwheel {:on-jsload "mandelbrot-cljs.core/on-js-reload"}

                        :compiler {:main mandelbrot-cljs.core
                                   :asset-path "js/compiled/out"
                                   :output-to "resources/public/js/compiled/fractals.js"
                                   :output-dir "resources/public/js/compiled/out"
                                   :optimizations :none


                                   :source-map true
                                   :source-map-timestamp true
                                   :cache-analysis true}}]}

  :figwheel {})
