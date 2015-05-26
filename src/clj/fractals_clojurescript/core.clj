(ns fractals-clojurescript.core

  (:require [clojure.java.io :as io]

            [compojure
             [core :refer :all]
             [handler :as handler]
             [route :as route]]

            [clojure.java.io :as io]

            [ring.server.standalone :refer [serve]]

            [ring.middleware
             [defaults :refer [site-defaults wrap-defaults]]]))


(defroutes site-routes
  (GET "/" []
    (io/resource "public/index.html"))

  (GET "/health" []
    "ok")

  (route/resources "/"))

(def handler (-> site-routes
             (wrap-defaults site-defaults)))
