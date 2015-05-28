(ns mandelbrot-cljs.canvas
  "Some helper functions for using HTML5 Canvas")

(defn maximize-canvas-dimensions!
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

(defn open-as-png!
  "Open a new tab or window containing a png version of the canvas"
  [canvas]
  (.open js/window (.toDataURL canvas "image/png")))
