(ns fractals-clojurescript.core)

(enable-console-print!)

; TODO: allow changing of this dynamically..
(def app-state (atom {:max-iterations 99
                      :escape-radius  10
                      :dimensions{:x0 -1.5 :x1 0.5
                                  :y0 -1 :y1 1}}))

(def canvas (.getElementById js/document "app"))

(def context (.getContext canvas "2d"))

(def idata (.createImageData context 1 1))

(defn complex-square
  "Squares a complex number (ordered [r, i] pair)"
  [[real imaginary]]
  [(- (* real real) (* imaginary imaginary))
   (* 2 real imaginary)])

(defn complex-+
  "Add two complex numbers"
  [[r1 i1] [r2 i2]]
  [(+ r1 r2)
   (+ i1 i2)])

(defn modulus
  "Modulus of a complex number ([r, i])"
  [[real imaginary]]
  (Math/sqrt (+ (* real real) (* imaginary imaginary))))


(defn iteration-count
  "Gets us a continous analogue of the classic discrete iteration count"
  [{:keys [escape-radius max-iterations]}
   z c]
  (->> (iterate (comp (partial complex-+ z) complex-square) z)
       (take max-iterations )
       (map modulus)
       (take-while #(< % escape-radius))
       ((fn [moduli]
          (- (count moduli)
             (/
              (Math/log (Math/log (last moduli)))
              (Math/log 2.0)))))))

(defn dot!
  "Colours the pixel at co-ordinates [x,y] in the color [r,g,b]"
  [x y [r g b]]
  (let [data (.-data idata)]

    (doseq [i (range (/ (.-length data) 4))]
      (aset data i r)
      (aset data (+ i 1) g)
      (aset data (+ i 2) b)
      (aset data (+ i 3) 255))

    (.putImageData context idata x y)))

(def width (.-width canvas))
(def height (.-height canvas))

(println "WIDTH: " width)
(println "HEIGHT: " height)

(let [x->real (fn [x]
                (+ (* (- (get-in @app-state [:dimensions :x1])
                             (get-in @app-state [:dimensions :x0]))
                      (/ x width)) (get-in @app-state [:dimensions :x0])))

      y->imaginary (fn [y]
                     (+ (* (- (get-in @app-state [:dimensions :y1])
                               (get-in @app-state [:dimensions :y0]))
                            (/ y height)) (get-in @app-state [:dimensions :y0])))]
  (doseq [x (range width)]
    (doseq [y (range height)]
      (let [real      (x->real x)

            imaginary (y->imaginary y)

            iterations (iteration-count @app-state [real imaginary] [-1 0.2])

            intensity (* 255 (/ iterations (get-in @app-state [:max-iterations])))]

        (dot! x y [intensity intensity intensity])))))


(.log js/console "Done")

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
