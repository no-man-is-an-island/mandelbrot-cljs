(ns mandelbrot-cljs.stats
  (:require [clojure.string :as string]))

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
  [{:keys [render-speed scale current-x current-y current-iterations max-iterations] :as stats}]
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
                                        (format-comma current-iterations)) )))
