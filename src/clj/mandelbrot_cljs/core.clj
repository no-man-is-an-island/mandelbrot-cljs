(ns mandelbrot-cljs.core

  (:require [clojure.java.io :as io]

            [compojure
             [core :refer :all]
             [handler :as handler]
             [route :as route]]

            [clojure.java.io :as io]

            [ring.adapter.jetty :refer [run-jetty]]

            [ring.middleware
             [defaults :refer [site-defaults wrap-defaults]]])

  (:gen-class))


(defroutes site-routes
  (GET "/" []
    (io/resource "public/index.html"))

  (GET "/health" []
    "ok")

  (route/resources "/"))

(def handler (-> site-routes
             (wrap-defaults site-defaults)))

(defn -main [& args]
  (run-jetty handler {:port 3000}))
