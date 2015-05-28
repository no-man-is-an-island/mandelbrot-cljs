(ns mandelbrot-cljs.core
  (:require [mandelbrot-cljs.canvas :refer [add-rectangle! open-as-png!]]
            [mandelbrot-cljs.rendering :refer [re-render!
                                               initial-rendering-data
                                               reset-rendering-data!]]))

(enable-console-print!)


(defonce app-state (atom {:canvas         (.getElementById js/document "canvas")
                          :overlay-canvas (.getElementById js/document "overlay-canvas")
                          :escape-radius  10000
                          :rendering-data initial-rendering-data}))

(defonce rendering-data-history (atom [initial-rendering-data]))


(defn handle-mouseup
  "On mouseup, we zoom the mandelbrot to the specified box and remove the
   :mousedown-event key from the app-state"
  [e]
  (swap!
   app-state
   (fn [{:as old-state :keys [mousedown-event canvas rendered-rectangle]}]

     (let [{:keys [x0 y0 scale]}        rendered-rectangle

           x*1                           (+ x0 (/ (aget e "pageX") scale))
           y*1                           (- y0 (/ (aget e "pageY") scale))

           x*0                           (+ x0 (/ (aget mousedown-event "pageX") scale))
           y*0                           (- y0 (/ (aget mousedown-event "pageY") scale))]

       (-> old-state
           (dissoc :mousedown-event)
           (assoc :rendering-data {:x0     x*0
                                   :y0     y*0
                                   :width  (- x*1 x*0)
                                   :height (- y*0 y*1)}))))))

(defn handle-mousemove
  "Draw a rectangle when we're zooming"
  [e]

  #_(let [{:keys [x0 y0 scale]} (:rendered-rectangle @app-state)]
    (.log js/console (str "X: " (+ x0 (/ (aget e "pageX") scale)) "Y: " (- y0 (/ (aget e "pageY") scale)))))

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

    (re-render! app-state)))

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


  (re-render! app-state))


(defn on-js-reload
  "A figwheel thing"
  [])

(set! (.-onload js/window) init!)
