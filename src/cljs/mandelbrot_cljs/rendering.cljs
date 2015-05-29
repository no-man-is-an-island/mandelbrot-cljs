(ns mandelbrot-cljs.rendering
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
  [canvas {:keys [width height x0 y0] :as render-state}]

  (let [screen-width             (.-width canvas)
        screen-height            (.-height canvas)
        {:keys [x0 y0 scale]
         :as rendered-rectangle} (enclosing-rectangle x0 y0 width height screen-width screen-height)

        max-iterations           (int (* 10 (Math/log scale))) ; Sensible?
        start                    (.getTime (js/Date.))

        context                  (.getContext canvas "2d")
        idata                    (.createImageData context screen-width screen-height)
        data                     (.-data idata)

        escape-radius-squared    1000000000]

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

      {:rendered-rectangle rendered-rectangle
       :stats
       {:render-speed render-speed
        :scale (long scale)
        :max-iterations max-iterations}})))
