(ns fractals-clojurescript.core
  (:use-macros [fractals-clojurescript.macros :only [forloop << >> local]]))

(enable-console-print!)

; TODO: allow changing of this dynamically..
(def app-state (atom {:render-data
                      {
                       ;:max-iterations 100
                       :escape-radius  10000
                       :scale          350 ; How many pixels a distance of 1 in the complex plane takes up
                       :x0             -3
                       :y0             1.2}}))

(def canvas (.getElementById js/document "app"))
(def context (.getContext canvas "2d"))

(def overlay-canvas (.getElementById js/document "overlay"))
(def overlay-context (.getContext overlay-canvas "2d"))



(defn render-mandelbrot!
  [{:keys [scale x0 y0 escape-radius] :as render-state}]

  (println render-state)


  (aset canvas "width"(.-innerWidth js/window))
  (aset canvas "height"(.-innerHeight js/window))

  (aset overlay-canvas "width"(.-innerWidth js/window))
  (aset overlay-canvas "height"(.-innerHeight js/window))

  (let [max-iterations        (int (* 10 (Math/log scale)))
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
            iterations (js/iteration_count escape-radius-squared max-iterations real imaginary)
            intensity  (* 255 (/ iterations max-iterations))

            i (* 4 (+ x (* y width)))]

        (aset data i intensity)
        (aset data (+ i 1) intensity)
        (aset data (+ i 2) intensity)
        (aset data (+ i 3) 255))))

    (.putImageData context idata 0 0)

    (.log js/console "Done in: " (int (* 1000 (/ (* height width)  (- (.getTime (js/Date.)) start)))) "px/s")))

(render-mandelbrot! (:render-data @app-state))

(set! (.-onresize js/window) (fn [] (render-mandelbrot! (:render-data @app-state))))

(add-watch app-state :state-changed
           (fn [_ _ old-state new-state]
             (when-not (= (:render-data old-state)
                          (:render-data new-state))
               (render-mandelbrot! (:render-data new-state)))))

(set! (.-onmousedown overlay-canvas) (fn [e] (swap! app-state assoc :mousedown e)))

(set! (.-onmousemove overlay-canvas) (fn [e] (when-let [mousedown (get @app-state :mousedown)]

                                               (aset overlay-context "lineWidth" 1)
                                               (aset overlay-context "strokeStyle" "green")

                                               (.clearRect overlay-context 0 0
                                                           (aget overlay-canvas "width")
                                                           (aget overlay-canvas "height"))

                                               (.strokeRect overlay-context
                                                            (aget mousedown "pageX")
                                                            (aget mousedown "pageY")
                                                            (- (aget e "pageX") (aget mousedown "pageX"))
                                                            (- (aget e "pageY") (aget mousedown "pageY"))))))

(set! (.-onmouseup overlay-canvas) (fn [e]
                                     (when-let [mousedown (get @app-state :mousedown)]
                                       ; TODO: Make this much less confusing
                                       (let [scale          (get-in @app-state [:render-data :scale])

                                             old-x0         (get-in @app-state [:render-data :x0])
                                             old-y0         (get-in @app-state [:render-data :y0])
                                             start-x        (aget mousedown "pageX")
                                             start-y        (aget mousedown "pageY")
                                             finish-x       (aget e "pageX")
                                             finish-y       (aget e "pageY")

                                             width          (aget canvas "width")
                                             height         (aget canvas "height")
                                             aspect-ratio   (/ width height)

                                             portrait?      (< (- finish-x start-x) (- finish-y start-y))

                                             new-scale      (if portrait?
                                                              (/ height (/ (- finish-y start-y) scale))
                                                              (/ width (/ (- finish-x start-x) scale)))

                                             desired-aspect (/ (- finish-x start-x)
                                                               (- finish-y start-y))

                                             padding        (* 0.5
                                                               (if portrait?
                                                                 (- width (* height desired-aspect))
                                                                 (- height (/ width desired-aspect))))

                                             new-x0         (if portrait?
                                                              (- (+ old-x0 (/ start-x scale))
                                                                 (/ padding new-scale))

                                                              (+ old-x0 (/ start-x scale)))

                                             new-y0         (if portrait?
                                                              (- old-y0 (/ start-y scale))

                                                              (+ (- old-y0 (/ start-y scale))
                                                                 (/ padding new-scale)))]

                                         (swap! app-state
                                                (fn [x]
                                                  (-> x
                                                      (assoc-in [:render-data :x0] new-x0)
                                                      (assoc-in [:render-data :y0] new-y0)
                                                      (assoc-in [:render-data :scale] new-scale)
                                                      (dissoc :mousedown))))))))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
