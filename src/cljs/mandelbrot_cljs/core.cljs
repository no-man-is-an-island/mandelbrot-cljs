(ns mandelbrot-cljs.core
  (:use-macros [mandelbrot-cljs.macros :only [forloop]]))

(enable-console-print!)

(def initial-rendering-data
  "Defines a rectangle (not necessarily of the same aspect ratio as the screen)
  in the complex plane to be displayed. This will then be centred on the screen
  using advanced maths."
  {:x0             -3
   :y0             1
   :width          4
   :height         2
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

(defn enclosing-rectangle
  "Calculates an enclosing rectangle which contains the given
  one centered on the screen (would you believe that working
  this out was the hardest thing in this project?)"
  [x0 y0 width height screen-width screen-height]
  (let [portrait?    (>= height width)

        aspect-ratio (/ width height)

        padding      (if portrait?
                       (- screen-width (* screen-height aspect-ratio))
                       (- screen-height (/ screen-width aspect-ratio)))

        scale        (if portrait?
                       (/ screen-height height)
                       (/ screen-width width))]

    (if portrait?
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
  [{:keys [canvas overlay-canvas escape-radius]
    {:keys [width height x0 y0] :as render-state} :rendering-data}]

  (println render-state)

  (set-canvas-dimensions! canvas)
  (set-canvas-dimensions! overlay-canvas)
  (clear-canvas! overlay-canvas)

  (let [screen-width             (.-width canvas)
        screen-height            (.-height canvas)
        {:keys [x0 y0 scale]
         :as rendered-rectangle} (enclosing-rectangle x0 y0 width height screen-width screen-height)

        context                  (.getContext canvas "2d")
        max-iterations           (int (* 10 (Math/log scale))) ; Sensible?
        start                    (.getTime (js/Date.))


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

    (.log js/console "Done in: " (int (* 1000 (/ (* screen-height screen-width)
                                                 (- (.getTime (js/Date.)) start)))) "px/s")))

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

  (set! (.-onmousemove overlay-canvas) handle-mousemove)

  (set! (.-onmouseup overlay-canvas) handle-mouseup))

(defn open-as-png!
  "Open a new tab or window containing a png version of the canvas"
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
            (undo!))

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
