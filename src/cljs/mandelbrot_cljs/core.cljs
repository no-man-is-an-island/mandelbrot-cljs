(ns mandelbrot-cljs.core
  (:use-macros [mandelbrot-cljs.macros :only [forloop]]))

(enable-console-print!)

(def initial-rendering-data
  {:scale          (/ (.-innerWidth js/window) 4) ; How many pixels a distance of 1 in the complex plane takes up
   :x0             -3 ; the x co-ordinate of the top-left pixel in the canvase
   :y0             1.2 ; the y co-ordinate of the top-left pixel in the canvase
   })

(defonce app-state (atom {:canvas         (.getElementById js/document "canvas")
                          :overlay-canvas (.getElementById js/document "overlay-canvas")
                          :escape-radius  10000
                          :rendering-data initial-rendering-data}))

(defonce rendering-data-history (atom [initial-rendering-data]))

(defn reset-rendering-data!
  "Resets the rendering data to the original (i.e. reset the zoom)
   to something which should look ok on most screens"
  []
  (swap! app-state assoc :rendering-data initial-rendering-data))

(defn set-canvas-dimensions!
  "Sets the canas to the same size as the enclosing window"
  [canvas]
  (aset canvas "width" (.-innerWidth js/window))
  (aset canvas "height" (.-innerHeight js/window)))

(defn clear-canvas!
  "Clears a whole canvas"
  [canvas]
  (let [context (.getContext canvas "2d")]
    (.clearRect context 0 0 (aget canvas "width") (aget canvas "height"))))

(defn add-rectangle!
  "Adds a (fill or stroke) rectangle to the canvas (and optionally clears all other rectangles)"
  [canvas
   x0 y0 x1 y1 & {:keys [clear? color opacity type] :or {clear? true color "green" opacity 1 type :stroke}}]
  (let [context (.getContext canvas "2d")]
    (aset context "globalAlpha" opacity)
    (aset context "lineWidth" 1)


    (when clear? (clear-canvas! canvas))

    (case type

      :stroke (do (aset context "strokeStyle" color)
                  (.strokeRect context x0 y0 (- x1 x0) (- y1 y0)))

      :fill (do (aset context "fillStyle" color)
                (.fillRect context x0 y0 (- x1 x0) (- y1 y0))))))

(defn render-mandelbrot!
  "Iterate over every pixel in the canvas, rendering the mandelbrot
  set at the specified scale. Uses a javascript function (in resources/public/js/fractal-maths.js)
  for the hard maths, because clojurescript just wasn't cutting it speed wise (you really want local
  variables for this kind of thing - I got it to be only ~10 times slower using the >> and << macros)"
  [{:keys [canvas overlay-canvas escape-radius]
    {:keys [scale x0 y0] :as render-state} :rendering-data}]

  (set-canvas-dimensions! canvas)
  (set-canvas-dimensions! overlay-canvas)
  (clear-canvas! overlay-canvas)

  (let [context               (.getContext canvas "2d")
        max-iterations        (int (* 10 (Math/log scale))) ; Sensible?
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

    (.log js/console "Done in: " (int (* 1000 (/ (* height width)  (- (.getTime (js/Date.)) start)))) "px/s")))

(defn re-render!
  "Adds a green semi-transparent overlay to the screen, then
  lines up a re-rendering (we need to yield control back to
  the browser so that the overlay actually get rendered)"
  [{:as new-state :keys [overlay-canvas]}]
  (add-rectangle!
   overlay-canvas
   0 0
   (aget overlay-canvas "width") (aget overlay-canvas "height")
   :opacity 0.1 :color "blue" :clear? false :type :fill)

  (.setTimeout js/window #(render-mandelbrot! new-state) 50))


(defn zoom-to-enclose-rectangle!
  "Returns a new rendering-data map to zoom the view of the mandelbrot to enclose a given
  rectangle in the current screen. If the aspect ratio of the given rectangle
  is not the same as that of the screen, it centers the given rectangle in the
  screen (hard to explain.. imagine I've drawn a really nice picture of this here)"
  [{:keys [scale] old-x0 :x0 old-y0 :y0} canvas rect-x0 rect-y0 rect-x1 rect-y1]
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
         (fn [{:as old-state :keys [mousedown-event canvas]}]

           (let [x1 (aget e "pageX")
                 y1 (aget e "pageY")
                 x0 (aget mousedown-event "pageX")
                 y0 (aget mousedown-event "pageY")]

             (-> old-state
                 (dissoc :mousedown-event)
                 (update-in [:rendering-data] zoom-to-enclose-rectangle! canvas x0 y0 x1 y1))))))

(defn handle-state-change!
  [_ _ old-state new-state]

  (when-not (= (:rendering-data old-state)
               (:rendering-data new-state))

    (when-not (= (:rendering-data new-state) (last @rendering-data-history))
      (swap! rendering-data-history conj (:rendering-data new-state)))

    (re-render! new-state)))

(defn undo!
  "Revert app-state to the previous value"
  []
  (when (> (count @rendering-data-history) 1)
    (swap! rendering-data-history pop)
    (swap! app-state assoc :rendering-data (last @rendering-data-history))))

(defn add-overlay-handlers!
  "Adds handlers for zooming to the overlay canvas"
  [overlay-canvas]

  (set! (.-onmousedown overlay-canvas) (fn [e]
                                         (swap! app-state assoc :mousedown-event e)))

  (set! (.-onmousemove overlay-canvas) (fn [e]
                                         (when-let [mousedown (get @app-state :mousedown-event)]

                                           (add-rectangle!
                                            (get @app-state :overlay-canvas)
                                            (aget mousedown "pageX")
                                            (aget mousedown "pageY")
                                            (aget e "pageX")
                                            (aget e "pageY")
                                            :clear? true
                                            :type :stroke
                                            :opacity 0.9
                                            :color "red"))))

  (set! (.-onmouseup overlay-canvas) handle-mouseup))

(defn open-as-png!
  "Trigger a page change to "
  [canvas]
  (.open js/window (.toDataURL canvas "image/png")))

(defn init!
  "Initialise event handlers, add atom watches, do the first rendering"
  []
  (set! (.-onresize js/window) (fn [] (re-render! @app-state)))

  (add-watch app-state :state-changed handle-state-change!)

  (add-overlay-handlers! (get @app-state :overlay-canvas))

  (set! (.-onkeydown js/window)
        (fn [e]
          (when (and (= (aget e "keyCode") 90)
                   (aget e "ctrlKey"))
            (undo!))))

  (set! (.-onkeydown js/window)
        (fn [e ]
          (when (and (= (aget e "keyCode") 73)
                   (aget e "ctrlKey"))
            (open-as-png! (:canvas @app-state)))))

  (set! (.-onclick (.getElementById js/document "undo")) undo!)

  (set! (.-onclick (.getElementById js/document "reset")) reset-rendering-data!)

  (set! (.-onclick (.getElementById js/document "png"))
        #(open-as-png! (:canvas @app-state)))


  (render-mandelbrot! @app-state))


(defn on-js-reload
  "A figwheel thing"
  [])

(set! (.-onload js/window) init!)
