(ns mandelbrot-cljs.core
  (:require [mandelbrot-cljs.canvas :refer [add-rectangle! open-as-png!]]
            [mandelbrot-cljs.rendering :refer [re-render!
                                               initial-rendering-data
                                               reset-rendering-data!]]

            [clojure.string :as string]))

(enable-console-print!)


(defonce app-state (atom {:canvas         (.getElementById js/document "canvas")
                          :overlay-canvas (.getElementById js/document "overlay-canvas")
                          :escape-radius  10000
                          :rendering-data initial-rendering-data
                          :show-stats?    false
                          :stats          {}}))

(defonce rendering-data-history (atom [initial-rendering-data]))

(defn format-comma
  [x]
  (letfn [(format-comma' [x]
            (->> x
                 str
                 reverse
                 (partition-all 3)
                 (map reverse)
                 reverse
                 (map string/join)
                 (string/join ",")))]
    (if (neg? x)
      (str "-" (format-comma' (* -1 x)))
      (format-comma' x))))

(defn render-stats!
  [app-state]
  (let [{:keys [render-speed scale current-x current-y current-iterations max-iterations]
         :as stats}
        (get @app-state :stats)]
    (aset (.getElementById js/document "renderSpeed") "innerHTML"
          (str "Render Speed: " (format-comma render-speed) " px/s"))

    (aset (.getElementById js/document "scale") "innerHTML"
          (str "Scale: " (format-comma scale) ))

    (aset (.getElementById js/document "maxIterations") "innerHTML"
          (str "Max Iterations: " (format-comma max-iterations) ))

    (aset (.getElementById js/document "currentX") "innerHTML"
          (str "Real: " current-x))

    (aset (.getElementById js/document "currentY") "innerHTML"
          (str "Imaginary: " current-y ))

    (aset (.getElementById js/document "currentIterations") "innerHTML"
          (str "Iterations to Escape: " (if (= "infinity" current-iterations)
                                          "infinity"
                                          (format-comma current-iterations)) ))))

(defn zoom-to!
  [startx starty width height]
  (swap!
   app-state
   (fn [{:as old-state :keys [mousedown-event overlay-canvas rendered-rectangle]}]
     (let [{:keys [x0 y0 scale]} rendered-rectangle]
       (-> old-state
           (dissoc :mousedown-event)
           (assoc :rendering-data {:x0 (+ x0 (/ startx scale))
                                   :y0 (- y0 (/ starty scale))
                                   :width (/ width scale)
                                   :height (/ height scale)}))))))

(defn modulus
  [x]
  (Math/sqrt (* x x)))

(defn handle-mouseup
  "On mouseup, we zoom the mandelbrot to the specified box and remove the
   :mousedown-event key from the app-state"
  [e]

  (let [startx  (aget (:mousedown-event @app-state) "pageX")
        starty  (aget (:mousedown-event @app-state) "pageY")
        finishx (aget e "pageX")
        finishy (aget e "pageY")]

    (if (or (< (modulus (- startx finishx)) 10)
            (< (modulus (- starty finishy)) 10)) ; Does this look like a click?

      (do
        (add-rectangle! (:overlay-canvas @app-state)
                        (Math/max 0 (- startx 100))
                        (Math/max 0 (- starty 100))
                        (+ startx 100)
                        (+ starty 100)
                        :clear? true
                        :color "red"
                        :type :stroke)
        (zoom-to! (Math/max 0 (- startx 100)) (Math/max 0 (- starty 100)) 200 200))

      (zoom-to! (Math/min startx finishx) (Math/min starty finishy) (modulus (- finishx startx)) (modulus (- finishy starty))))))

(defn handle-mousemove
  "Draw a rectangle when we're zooming"
  [e]

  (let [{:keys [x0 y0 scale]} (:rendered-rectangle @app-state)
        x                     (+ x0 (/ (aget e "pageX") scale))
        y                     (- y0 (/ (aget e "pageY") scale))
        max-iterations        (int (* 10 (Math/log scale)))
        iterations            (js/mandelbrot_smoothed_iteration_count
                               (* (:escape-radius @app-state) (:escape-radius @app-state))
                               max-iterations
                               x y)]
    (swap! app-state update-in [:stats] assoc
           :current-x x
           :current-y y
           :current-iterations (if (>= iterations max-iterations) "infinity" (long iterations))))

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
     :color "red")))

(defn handle-state-change!
  [_ app-state old-state new-state]

  (when-not (= (:rendering-data old-state)
               (:rendering-data new-state))

    (when-not (= (:rendering-data new-state) (last @rendering-data-history))
      (swap! rendering-data-history conj (:rendering-data new-state)))

    (re-render! app-state))

  (when-not (= (:stats old-state)
               (:stats new-state))

    (render-stats! app-state)))

(defn undo!
  "Revert app-state to the previous value"
  []
  (when (> (count @rendering-data-history) 1)
    (swap! rendering-data-history pop)
    (swap! app-state assoc :rendering-data (last @rendering-data-history))))

(defn add-overlay-handlers!
  "Adds handlers for zooming to the overlay canvas"
  [overlay-canvas]

  (set! (.-onmousedown overlay-canvas) (fn [e] (swap! app-state assoc :mousedown-event e)))

  (set! (.-onmousemove overlay-canvas) handle-mousemove)

  (set! (.-onmouseup overlay-canvas) handle-mouseup))

(defn handle-keydown!
  "Bind ctrl-z to undo and ctrl-i to open-image"
  [e]
  (when (and (= (aget e "keyCode") 90)
             (aget e "ctrlKey"))
    (undo!))

  (when (and (= (aget e "keyCode") 73)
             (aget e "ctrlKey"))
    (open-as-png! (:canvas @app-state))))

(defn toggle-stats!
  [e]
  (if (:show-stats? @app-state)
    (do
      (swap! app-state assoc :show-stats? false)
      (aset (aget (.getElementById js/document "stats") "style") "display" "none"))
    (do
      (swap! app-state assoc :show-stats? true)
      (aset (aget (.getElementById js/document "stats") "style") "display" "block"))))

(defn init!
  "Initialise event handlers, add atom watches, do the first rendering"
  []
  (set! (.-onresize js/window) (fn [] (re-render! app-state)))

  (add-watch app-state :state-changed handle-state-change!)

  (add-overlay-handlers! (get @app-state :overlay-canvas))

  (set! (.-onkeydown js/window) handle-keydown!)

  (set! (.-onclick (.getElementById js/document "undo")) undo!)

  (set! (.-onclick (.getElementById js/document "reset")) #(reset-rendering-data! app-state))

  (set! (.-onclick (.getElementById js/document "png")) #(open-as-png! (:canvas @app-state)))

  (set! (.-onclick (.getElementById js/document "toggleStats")) toggle-stats!)


  (re-render! app-state))


(defn on-js-reload
  "A figwheel thing"
  [])

(set! (.-onload js/window) init!)
