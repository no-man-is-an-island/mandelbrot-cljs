(ns mandelbrot-cljs.core
  (:require [mandelbrot-cljs.canvas :refer [add-zoom-box! add-semi-opaque-overlay!
                                            open-as-png! clear-canvas! maximize-canvas-dimensions!]]
            [mandelbrot-cljs.rendering :refer [render-mandelbrot!
                                               initial-rendering-data]]

            [mandelbrot-cljs.stats :refer [render-stats!]]

            [cemerick.url :refer [url]]

            [cljs.core.async :refer [chan <! put!]])

  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(enable-console-print!)

(def ^:dynamic *log-level* 1)


(defonce app-state (atom {:canvas         (.getElementById js/document "canvas")
                          :overlay-canvas (.getElementById js/document "overlay-canvas")
                          :escape-radius  10000
                          :stats          {}}))

(defonce rendering-data-history (atom []))

(defn undo!
  "Revert app-state to the previous value and queue up a re-render
   (when there is a previous state to undo into)"
  [messages]
  (when (> (count @rendering-data-history) 1)
    (swap! rendering-data-history pop)
    (swap! app-state assoc :rendering-data (last @rendering-data-history))
    (put! messages [:render])))

(defn zoom-to!
  "Calculate what the given rectangle corresponds to in the complex
   plane and trigger a re-rendering to enclose it."
  [messages startx starty finishx finishy]
  (swap!
   app-state
   (fn [{:as old-state :keys [point-information-fn]}]
     (let [{start-real :real start-imaginary :imaginary} (point-information-fn startx starty)
           {finish-real :real finish-imaginary :imaginary} (point-information-fn finishx finishy)]
       (-> old-state
           (dissoc :mousedown-event)
           (assoc :rendering-data {:x0 start-real
                                   :y0 start-imaginary
                                   :width (- finish-real start-real)
                                   :height (- start-imaginary finish-imaginary)})))))
  (put! messages [:render]))

(defn handle-mouseup
  "On mouseup, we zoom the mandelbrot to the specified box and remove the
   :mousedown-event key from the app-state"
  [messages e]

  (let [modulus (fn [x] (Math/sqrt (* x x)))
        startx  (aget (:mousedown-event @app-state) "pageX")
        starty  (aget (:mousedown-event @app-state) "pageY")
        finishx (aget e "pageX")
        finishy (aget e "pageY")]

    (if (or (< (modulus (- startx finishx)) 10)
            (< (modulus (- starty finishy)) 10)) ; Does this look like a click?

      (put! messages [:zoom-to {:x0 (Math/max 0 (- startx 100))
                                :y0 (Math/max 0 (- starty 100))
                                :x1 (+ startx 100)
                                :y1 (+ starty 100)}])

      (put! messages [:zoom-to {:x0 (Math/min startx finishx)
                                :y0 (Math/min starty finishy)
                                :x1 (Math/max startx finishx)
                                :y1 (Math/max starty finishy)}]))))

(defn handle-mousemove
  "Draw a rectangle when we're tracing a box to zoom to, and update the
  stats to show information about the point in the plane the mouse cursor
  is at."
  [messages e]

  (let [{:keys [real imaginary iterations]} ((:point-information-fn @app-state)
                                             (aget e "pageX") (aget e "pageY"))]

    (put! messages [:update-stats {:current-x real
                                   :current-y imaginary
                                   :current-iterations iterations}]))

  (when-let [mousedown (get @app-state :mousedown-event)]

    (add-zoom-box!
     (get @app-state :overlay-canvas)
     (aget mousedown "pageX")
     (aget mousedown "pageY")
     (aget e "pageX")
     (aget e "pageY"))))

(defn handle-keydown!
  "Bind ctrl-z to undo and ctrl-i to open-image"
  [messages e]
  (when (and (= (aget e "keyCode") 90)
             (aget e "ctrlKey"))
    (put! messages [:undo]))

  (when (and (= (aget e "keyCode") 73)
             (aget e "ctrlKey"))
    (put! messages [:open-as-png (:canvas @app-state)])))

(defn get-rendering-data-from-url
  "Gets a rendering-level from the url parameters, or returns nil"
  []
  (let [{:keys [query] :as full-url} (url (aget (aget js/window "location")"href"))
        query                        (into {} (map (fn [[k v]] [(keyword k) v]) query))]
    (when (and (:x0 query) (:y0 query) (:width query) (:height query))
      query)))

(defn save-rendering-data-in-url!
  [{:as rendering-data}]
  (let [current-url (url (aget (aget js/window "location") "href"))]
    (.pushState
     (aget js/window "history")
     ""
     ""
     (.toString (assoc current-url :query rendering-data)))))


(defn start-event-handler!
  "Main handler for messages on our core.async channel"
  [messages]
  (go-loop []
    (when-let [[event body] (<! messages)]
      (case event

        :log (when (<= *log-level* (:level body)) (.log js/console (:message body)))

        :keydown (handle-keydown! messages body)

        :mousedown (swap! app-state assoc :mousedown-event body)

        :mouseup (handle-mouseup messages body)

        :mousemove (handle-mousemove messages body)

        :undo (undo! messages)

        :open-as-png (open-as-png! body)

        :clear-canvases (doseq [canvas body] (clear-canvas! canvas))

        :reset-rendering-data (when-not (= initial-rendering-data (:rendering-data @app-state))
                                (swap! app-state assoc :rendering-data initial-rendering-data)
                                (put! messages [:render]))

        :render

        (do
          (add-semi-opaque-overlay! (:overlay-canvas @app-state))

          ; We have to yield back to the browser so it can render the overlay
          (.setTimeout
           js/window
           (fn []
             (put! messages [:render-mandelbrot (select-keys @app-state [:rendering-data :canvas :overlay-canvas])])
             (put! messages [:clear-canvases [(:overlay-canvas @app-state)]]))
           100))

        :render-mandelbrot

        (do
          (maximize-canvas-dimensions! (:overlay-canvas body))
          (maximize-canvas-dimensions! (:canvas body))

          (let [{:keys [point-information-fn stats]}
                (render-mandelbrot! (:canvas body) (:rendering-data body))]

            (swap! app-state (fn [old-state]
                               (-> old-state
                                   (assoc :point-information-fn point-information-fn))))

            (save-rendering-data-in-url! (:rendering-data body))
            (put! messages [:add-to-rendering-data-history (:rendering-data body)])
            (put! messages [:update-stats stats])))

        :add-to-rendering-data-history (when-not (= body (last @rendering-data-history))
                                         (swap! rendering-data-history conj body))

        :zoom-to (let [{:keys [x0 x1 y0 y1]} body]
                   (add-zoom-box! (:overlay-canvas @app-state) x0 y0 x1 y1)
                   (zoom-to! messages x0 y0 x1 y1))

        :update-stats (do
                        (swap! app-state update-in [:stats] merge body)
                        (render-stats! (:stats @app-state)))

        :toggle-stats (let [stats-box (aget (.getElementById js/document "stats") "style")]
                        (if (#{"" "none"} (aget stats-box "display"))
                          (aset stats-box "display" "block")
                          (aset stats-box "display" "none")))))
    (recur)))

(defn init!
  "Initialise event handlers, add atom watches, do the first rendering"
  []
  (let [messages (chan)]

    (let [rendering-data (or (get-rendering-data-from-url) initial-rendering-data)]
      (swap! app-state assoc :rendering-data rendering-data))

    (start-event-handler! messages)

    (maximize-canvas-dimensions! (:overlay-canvas @app-state))
    (maximize-canvas-dimensions! (:canvas @app-state))

    (put! messages [:log {:level 1 :message "Initialisation function called..."}])

    (set! (.-onresize js/window) (fn [] (put! messages [:render])))

    (set! (.-onmousedown (:overlay-canvas @app-state)) #(put! messages [:mousedown %]))

    (set! (.-onmousemove (:overlay-canvas @app-state)) #(put! messages [:mousemove %]))

    (set! (.-onmouseup (:overlay-canvas @app-state)) #(put! messages [:mouseup %]))

    (set! (.-onkeydown js/window) #(put! messages [:keydown %]))

    (set! (.-onclick (.getElementById js/document "undo")) #(put! messages [:undo]))

    (set! (.-onclick (.getElementById js/document "reset")) #(put! messages [:reset-rendering-data]))

    (set! (.-onclick (.getElementById js/document "png"))
          #(put! messages [:open-as-png (:canvas @app-state)]))

    (set! (.-onclick (.getElementById js/document "toggleStats")) (fn [e] (put! messages [:toggle-stats])))


    (put! messages [:render])))


(defn on-js-reload
  "A figwheel thing"
  [])

(set! (.-onload js/window) init!)
