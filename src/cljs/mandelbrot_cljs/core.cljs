(ns mandelbrot-cljs.core
  (:use-macros [mandelbrot-cljs.macros :only [forloop]]))

(enable-console-print!)

(def canvas (.getElementById js/document "app"))
(def context (.getContext canvas "2d"))

(def overlay-canvas (.getElementById js/document "overlay"))
(def overlay-context (.getContext overlay-canvas "2d"))

(def initial-rendering-data
  {:scale          350 ; How many pixels a distance of 1 in the complex plane takes up
   :x0             -3 ; the x co-ordinate of the top-left pixel in the canvase
   :y0             1.2 ; the y co-ordinate of the top-left pixel in the canvase
   })

(defonce app-state (atom {:escape-radius 10000
                          :rendering-data initial-rendering-data}))

(defn reset-rendering-data!
  "Resets the rendering data to the original (i.e. reset the zoom)
   to something which should look ok on most screens"
  []
  (swap! app-state :assoc :rendering-data initial-rendering-data))

(defn make-canvases-fill-window!
  "Sets the rendering canvas and the overlay canvas to the same
   size as the enclosing window"
  []
  (aset canvas "width"(.-innerWidth js/window))
  (aset canvas "height"(.-innerHeight js/window))

  (aset overlay-canvas "width"(.-innerWidth js/window))
  (aset overlay-canvas "height"(.-innerHeight js/window)))

(defn clear-overlay!
  "Clears the whole overlay canvas"
  []
  (.clearRect overlay-context 0 0
              (aget overlay-canvas "width")
              (aget overlay-canvas "height")))

(defn add-overlay-rectangle!
  "Adds a rectangle to the overlay canvas (and optionally clears all other rectangles)"
  [x0 y0 x1 y1 & {:keys [clear? color opacity type] :or {clear? true color "green" opacity 1 type :stroke}}]
  (aset overlay-context "globalAlpha" opacity)
  (aset overlay-context "lineWidth" 1)


  (when clear? (clear-overlay!))

  (case type

    :stroke (do (aset overlay-context "strokeStyle" color)
                (.strokeRect overlay-context x0 y0 (- x1 x0) (- y1 y0)))

    :fill (do (aset overlay-context "fillStyle" color)
              (.fillRect overlay-context x0 y0 (- x1 x0) (- y1 y0)))))

(defn render-mandelbrot!
  "Iterate over every pixel in the canvas, rendering the mandelbrot
  set at the specified scale. Uses a javascript function (in resources/public/js/fractal-maths.js)
  for the hard maths, because clojurescript just wasn't cutting it speed wise (you really want local
  variables for this kind of thing - I got it to be only ~10 times slower using the >> and << macros)"
  [{escape-radius                          :escape-radius
    {:keys [scale x0 y0] :as render-state} :rendering-data}]

  (println render-state)

  (make-canvases-fill-window!)

  (let [max-iterations        (int (* 10 (Math/log scale))) ; Sensible?
        start                 (.getTime (js/Date.))

        width                 (.-width canvas)
        height                (.-height canvas)
        idata                 (.createImageData context width height)
        data                  (.-data idata)

        escape-radius-squared (* escape-radius escape-radius)]

    (forloop
     [(x 0) (< x width)  (inc x)]
     (forloop
      [(y 0) (< y height) (inc y)]

      (let [real       (+ (/ x scale) x0)
            imaginary  (- (/ y scale) y0)
            iterations (js/mandelbrot_smoothed_iteration_count escape-radius-squared max-iterations real imaginary)
            intensity  (* 255 (/ iterations max-iterations))
            data-index (* 4 (+ x (* y width)))]

        (aset data data-index intensity)
        (aset data (+ data-index 1) intensity)
        (aset data (+ data-index 2) intensity)
        (aset data (+ data-index 3) 255))))

    (.putImageData context idata 0 0)

    (clear-overlay!)

    (.log js/console "Done in: " (int (* 1000 (/ (* height width)  (- (.getTime (js/Date.)) start)))) "px/s")))

(set! (.-onmousedown overlay-canvas) (fn [e]
                                       (swap! app-state assoc :mousedown-event e)))

(set! (.-onmousemove overlay-canvas) (fn [e]
                                       (when-let [mousedown (get @app-state :mousedown-event)]

                                         (add-overlay-rectangle!
                                          (aget mousedown "pageX")
                                          (aget mousedown "pageY")
                                          (aget e "pageX")
                                          (aget e "pageY")
                                          :clear? true
                                          :color "green"))))


(defn zoom-to-enclose-rectangle!
  "Returns a new rendering-data map to zoom the view of the mandelbrot to enclose a given
  rectangle in the current screen. If the aspect ratio of the given rectangle
  is not the same as that of the screen, it centers the given rectangle in the
  screen (hard to explain.. imagine I've drawn a really nice picture of this here)"
  [{:keys [scale] old-x0 :x0 old-y0 :y0} rect-x0 rect-y0 rect-x1 rect-y1]
  (let [width            (aget canvas "width")
        height           (aget canvas "height")

        portrait?        (< (- rect-x1 rect-x0) (- rect-y1 rect-y0))

        new-scale        (if portrait?
                           (/ height (/ (- rect-y1 rect-y0) scale))
                           (/ width (/ (- rect-x1 rect-x0) scale)))

        new-aspect-ratio (/ (- rect-x1 rect-x0) (- rect-y1 rect-y0))

        padding          (* 0.5
                            (if portrait?
                              (- width (* height new-aspect-ratio))
                              (- height (/ width new-aspect-ratio))))

        new-x0           (if portrait?
                           (- (+ old-x0 (/ rect-x0 scale))
                              (/ padding new-scale))

                           (+ old-x0 (/ rect-x0 scale)))

        new-y0           (if portrait?
                           (- old-y0 (/ rect-y0 scale))

                           (+ (- old-y0 (/ rect-y0 scale))
                              (/ padding new-scale)))]

    {:x0 new-x0
     :y0 new-y0
     :scale new-scale}))


(defn handle-mouseup
  "On mouseup, we zoom the mandelbrot to the specified box and remove the
   :mousedown-event key from the app-state"
  [e]
  (swap! app-state
         (fn [{:as old-state :keys [mousedown-event]}]

           (let [x1 (aget e "pageX")
                 y1 (aget e "pageY")
                 x0 (aget mousedown-event "pageX")
                 y0 (aget mousedown-event "pageY")]

             (-> old-state
                 (dissoc :mousedown-event)
                 (update-in [:rendering-data] zoom-to-enclose-rectangle! x0 y0 x1 y1))))))

(set! (.-onmouseup overlay-canvas) handle-mouseup)

(set! (.-onresize js/window) (fn [] (render-mandelbrot! @app-state)))

(add-watch app-state :state-changed
           (fn [_ _ old-state new-state]
             (when-not (= (:rendering-data old-state)
                          (:rendering-data new-state))

               (add-overlay-rectangle!
                0 0
                (aget overlay-canvas "width") (aget overlay-canvas "height")
                :opacity 0.2 :color "green" :clear? true :type :fill)

               (.setTimeout js/window #(render-mandelbrot! new-state) 10))))

(render-mandelbrot! @app-state)

(defn on-js-reload
  "A figwheel thing"
  [])
