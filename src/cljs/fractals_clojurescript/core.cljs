(ns fractals-clojurescript.core
  (:use-macros [fractals-clojurescript.macros :only [forloop << >> local]]))

(enable-console-print!)

; TODO: allow changing of this dynamically..
(def app-state (atom {:max-iterations 100
                      :escape-radius  10000
                      :dimensions {:x0 -3   :x1 1
                                   :y0 -1.5 :y1 1.5}}))

(def canvas (.getElementById js/document "app"))

(aset canvas "width"(.-innerWidth js/window))
(aset canvas "height"(.-innerHeight js/window))


(def width (.-width canvas))
(def height (.-height canvas))

(def context (.getContext canvas "2d"))

(def idata (.createImageData context width height))
(def data (.-data idata))


(defn smoothed-count
  "Smooth the iterations to a continuous value
  (a la http://linas.org/art-gallery/escape/escape.html)"
  [iterations final-modulus]
  (- iterations
     (/
      (Math/log (Math/log (Math/sqrt final-modulus)))
      (Math/log 2.0))))

(defn iteration-count
  "Gets us a continous analogue of the classic discrete iteration count"
  [escape-radius-squared max-iterations initial-z]
  (let [mod-z             (local)
        real              (local)
        imaginary         (local)
        iterations        (local)

        initial-real      (first initial-z)
        initial-imaginary (second initial-z)]

    (>> iterations 0)
    (>> real initial-real)
    (>> imaginary initial-imaginary)
    (>> mod-z (+ (* (<< real) (<< real)) (* (<< imaginary) (<< imaginary))))

    (while (not (or (< escape-radius-squared (<< mod-z))
                    (= (<< iterations) max-iterations)))

      (let [r (<< real) i (<< imaginary)]
        (>> real (+ (* r r) (- (* i i)) initial-real))
        (>> imaginary (+ (* 2 r i) initial-imaginary)))

      (>> mod-z (+ (* (<< real) (<< real)) (* (<< imaginary) (<< imaginary))))
      (>> iterations (inc (<< iterations))))

    (smoothed-count (<< iterations) (<< mod-z))))

(defn dot!
  "Colours the pixel at co-ordinates [x,y] in the color [r,g,b]"
  [x y r g b]
  (let [i (* 4 (+ x (* y width)))]
    (aset data i r)
    (aset data (+ i 1) g)
    (aset data (+ i 2) b)
    (aset data (+ i 3) 255)))

(println "WIDTH: " width)
(println "HEIGHT: " height)

(def start (.getTime (js/Date.)))

(let [x0                    (get-in @app-state [:dimensions :x0])
      x1                    (get-in @app-state [:dimensions :x1])
      x-size                (- x1 x0)

      y0                    (get-in @app-state [:dimensions :y0])
      y1                    (get-in @app-state [:dimensions :y1])
      y-size                (- y1 y0)

      escape-radius         (:escape-radius @app-state)
      escape-radius-squared (* escape-radius escape-radius)
      max-iterations        (:max-iterations @app-state)]

  (doseq [x (range width)]
    (doseq [y (range height)]
      (let [real       (+ (* x-size (/ x width)) x0)

            imaginary  (+ (* y-size (/ y height)) y0)

            iterations (iteration-count escape-radius-squared max-iterations [real imaginary])

            intensity  (* 255 (/ iterations max-iterations))]

        (dot! x y intensity intensity intensity)))))

(.putImageData context idata 0 0)

(.log js/console "Done in: " (int (* 1000 (/ (* height width)  (- (.getTime (js/Date.)) start)))) "px/s")

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
