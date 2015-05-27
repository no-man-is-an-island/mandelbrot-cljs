(ns mandelbrot-cljs.core
  (:use-macros [mandelbrot-cljs.macros :only [forloop]]))

(enable-console-print!)

(def canvas (.getElementById js/document "app"))
(def context (.getContext canvas "2d"))

(def overlay-canvas (.getElementById js/document "overlay"))
(def overlay-context (.getContext overlay-canvas "2d"))

(def initial-rendering-data
  {:escape-radius  10000
   :scale          350 ; How many pixels a distance of 1 in the complex plane takes up
   :x0             -3
   :y0             1.2})

(defonce app-state (atom {:rendering-data initial-rendering-data}))



(defn reset-rendering-data!
  "Resets the rendering data to the original (i.e. reset the zoom)
   to something which should look ok on most screens"
  []
  (swap! app-state :assoc :rendering-data initial-rendering-data))

(defn render-mandelbrot!
  [{:keys [scale x0 y0 escape-radius] :as render-state}]

  (println render-state)

  (aset canvas "width"(.-innerWidth js/window))
  (aset canvas "height"(.-innerHeight js/window))

  (aset overlay-canvas "width"(.-innerWidth js/window))
  (aset overlay-canvas "height"(.-innerHeight js/window))

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

            i          (* 4 (+ x (* y width)))]

        (aset data i intensity)
        (aset data (+ i 1) intensity)
        (aset data (+ i 2) intensity)
        (aset data (+ i 3) 255))))

    (.putImageData context idata 0 0)

    (.log js/console "Done in: " (int (* 1000 (/ (* height width)  (- (.getTime (js/Date.)) start)))) "px/s")))

(set! (.-onresize js/window) (fn [] (render-mandelbrot! (:rendering-data @app-state))))

(add-watch app-state :state-changed
           (fn [_ _ old-state new-state]
             (when-not (= (:rendering-data old-state)
                          (:rendering-data new-state))
               (render-mandelbrot! (:rendering-data new-state)))))

(defn add-overlay-rectangle!
  "Adds a rectangle to the overlay canvas (and optionally clears all other rectangles)"
  [x0 y0 x1 y1 & {:keys [clear? color] :or {clear? true color "green"}}]
  (aset overlay-context "lineWidth" 1)
  (aset overlay-context "strokeStyle" color)

  (when clear?
    (.clearRect overlay-context 0 0
                (aget overlay-canvas "width")
                (aget overlay-canvas "height")))

  (.strokeRect overlay-context x0 y0 (- x1 x0) (- y1 y0)))

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



(set! (.-onmouseup overlay-canvas) (fn [e]
                                     (swap! app-state
                                            (fn [{:keys [mousedown-event rendering-data] :as old-state}]
                                              (let [scale            (:scale rendering-data)

                                                    old-x0           (:x0 rendering-data)
                                                    old-y0           (:y0 rendering-data)

                                                    start-x          (aget mousedown-event "pageX")
                                                    start-y          (aget mousedown-event "pageY")
                                                    finish-x         (aget e "pageX")
                                                    finish-y         (aget e "pageY")

                                                    width            (aget canvas "width")
                                                    height           (aget canvas "height")

                                                    portrait?        (< (- finish-x start-x) (- finish-y start-y))

                                                    new-scale        (if portrait?
                                                                       (/ height (/ (- finish-y start-y) scale))
                                                                       (/ width (/ (- finish-x start-x) scale)))

                                                    new-aspect-ratio (/ (- finish-x start-x)
                                                                        (- finish-y start-y))

                                                    padding          (* 0.5
                                                                        (if portrait?
                                                                          (- width (* height new-aspect-ratio))
                                                                          (- height (/ width new-aspect-ratio))))

                                                    new-x0           (if portrait?
                                                                       (- (+ old-x0 (/ start-x scale))
                                                                          (/ padding new-scale))

                                                                       (+ old-x0 (/ start-x scale)))

                                                    new-y0           (if portrait?
                                                                       (- old-y0 (/ start-y scale))

                                                                       (+ (- old-y0 (/ start-y scale))
                                                                          (/ padding new-scale)))]
                                                (-> old-state
                                                    (assoc-in [:rendering-data :x0] new-x0)
                                                    (assoc-in [:rendering-data :y0] new-y0)
                                                    (assoc-in [:rendering-data :scale] new-scale)

                                                    (dissoc :mousedown-event)))))))


(render-mandelbrot! (:rendering-data @app-state))

(defn on-js-reload
  "A figwheel thing"
  [])
