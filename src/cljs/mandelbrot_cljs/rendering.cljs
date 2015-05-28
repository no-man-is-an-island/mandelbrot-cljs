(ns mandelbrot-cljs.rendering
  (:require [mandelbrot-cljs.canvas :refer [maximize-canvas-dimensions! clear-canvas!
                                            add-rectangle!]])
  (:use-macros [mandelbrot-cljs.macros :only [forloop]]))

(def initial-rendering-data
  "Defines a rectangle (not necessarily of the same aspect ratio as the screen)
  in the complex plane to be displayed. This will then be centred on the screen
  using advanced maths."
  {:x0             -1.6
   :y0             1.2
   :width          2
   :height         2.4
   })

(defn reset-rendering-data!
  "Resets the rendering data to the original (i.e. reset the zoom)
   to something which should look ok on most screens"
  [app-state]
  (swap! app-state assoc :rendering-data initial-rendering-data))

(defn enclosing-rectangle
  "Calculates an enclosing rectangle which contains the given
  one centered on the screen (would you believe that working
  this out was the hardest thing in this project?)

  The metric to use is whether the aspect ratio of the desired
  rectangle is greater than the aspect ratio of the screen or not.
  This lets us determine whether to pad the rectangle out in
  the x-dimension or the y-dimension"
  [x0 y0 width height screen-width screen-height]
  (let [aspect-ratio        (/ width height)
        screen-aspect-ratio (/ screen-width screen-height)

        pad-x?              (<= aspect-ratio screen-aspect-ratio)

        padding             (if pad-x?
                              (- screen-width (* screen-height aspect-ratio))
                              (- screen-height (/ screen-width aspect-ratio)))

        scale               (if pad-x?
                              (/ screen-height height)
                              (/ screen-width width))]

    (if pad-x?
      {:x0     (- x0 (* 0.5 (/ padding scale)))
       :y0     y0
       :width  (+ width (/ padding scale))
       :height height
       :scale  scale}

      {:x0     x0
       :y0     (+ y0 (* 0.5 (/ padding scale)))
       :width  width
       :height (+ height (/ padding scale))
       :scale  scale})))

(defn render-mandelbrot!
  "Iterate over every pixel in the canvas, rendering the mandelbrot
  set at the specified scale. Uses a javascript function (in resources/public/js/fractal-maths.js)
  for the hard maths, because clojurescript just wasn't cutting it speed wise (you really want local
  variables for this kind of thing - I got it to be only ~10 times slower using the >> and << macros)"
  [app-state]
  (let [{:keys [canvas overlay-canvas escape-radius]
         {:keys [width height x0 y0] :as render-state} :rendering-data}
        @app-state]

    (println render-state)

    (maximize-canvas-dimensions! canvas)
    (maximize-canvas-dimensions! overlay-canvas)
    (clear-canvas! overlay-canvas)

    (let [screen-width             (.-width canvas)
          screen-height            (.-height canvas)
          {:keys [x0 y0 scale]
           :as rendered-rectangle} (enclosing-rectangle x0 y0 width height screen-width screen-height)

          max-iterations           (int (* 10 (Math/log scale))) ; Sensible?
          start                    (.getTime (js/Date.))

          context                  (.getContext canvas "2d")
          idata                    (.createImageData context screen-width screen-height)
          data                     (.-data idata)

          escape-radius-squared    (* escape-radius escape-radius)]

      (swap! app-state assoc :rendered-rectangle rendered-rectangle)

      (forloop
       [(x 0) (< x screen-width)  (inc x)]
       (forloop
        [(y 0) (< y screen-height) (inc y)]

        (let [real       (+ (/ x scale) x0)
              imaginary  (- (/ y scale) y0)
              iterations (js/mandelbrot_smoothed_iteration_count escape-radius-squared max-iterations real imaginary)

              intensity  (if (>= iterations max-iterations) 0 (* 255 (/ iterations max-iterations)))
              data-index (* 4 (+ x (* y screen-width)))]

          (aset data data-index intensity)
          (aset data (+ data-index 1) intensity)
          (aset data (+ data-index 2) intensity)
          (aset data (+ data-index 3) 255))))

      (.putImageData context idata 0 0)

      (let [render-speed (int (* 1000 (/ (* screen-height screen-width)
                                         (- (.getTime (js/Date.)) start))))]

        (swap! app-state update-in [:stats] assoc
               :render-speed render-speed
               :scale (int scale))

        (.log js/console "Done in: " render-speed "px/s")))))

(defn re-render!
  "Adds a blue semi-transparent overlay to the screen, then
  lines up a re-rendering (we need to yield control back to
  the browser so that the overlay actually get rendered)"
  [app-state]
  (let [overlay-canvas (:overlay-canvas @app-state)]
    (add-rectangle!
     overlay-canvas
     0 0
     (aget overlay-canvas "width") (aget overlay-canvas "height")
     :opacity 0.1 :color "blue" :clear? false :type :fill))

  (.setTimeout js/window #(render-mandelbrot! app-state) 50))
