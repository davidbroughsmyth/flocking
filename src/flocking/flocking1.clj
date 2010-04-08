(ns flocking.flocking1
  (:use [vecmath.vec2 :only [vec2 zero sum]]
        clojure.contrib.pprint)
  (:require [rosado.processing :as p]
            [rosado.processing.applet :as applet]
            [vecmath.core :as vm]))

(defprotocol StoreDist
  (set-dist [this dist]))

(declare dist-boid)

(deftype boid
  [loc vel acc r max-speed max-force]
  StoreDist
  (set-dist [_ dist] (dist-boid loc vel acc r max-speed max-force dist)))

(deftype dist-boid
  [loc vel acc r max-speed max-force dist])

(def #^java.util.Random *rnd* (new java.util.Random))
(def *width* 640)
(def *height* 360)
(def *boid-count* 150)
(def aflock (atom []))

(defn limit [v n]
  (vm/mul (vm/unit v) n))

(defn make-boid [loc ms mf]
  {:loc       loc
   :vel       (vec2 (+ (* (.nextFloat *rnd*) 2) -1)
                    (+ (* (.nextFloat *rnd*) 2) -1))
   :acc       (vec2 0 0)
   :r         2.0
   :max-speed ms
   :max-force mf})
 
(defn bound [n ox dx]
  (let [n  (int n)
        ox (int ox)
        dx (int dx)]
   (cond 
    (< n (int (- ox)))    (+ dx ox)
    (> n (int (+ ox dx))) (- ox)
    true n)))

(defn borders [{loc :loc, r :r, :as boid}]
  (assoc boid :loc (vec2 (bound (:x loc) r *width*) (bound (:y loc) r *height*))))
 
(defn render [{{dx :x dy :y} :vel, {x :x y :y} :loc, r :r, :as boid}]
  (let [dx (float dx)
        dy (float dy)
        r  (float r)
        theta (float (+ (float (p/atan2 dy dx))
                        (float (/ (float Math/PI) (float 2.0)))))]
    (p/fill-float 200 100)
    (p/stroke-int 255)
    (p/push-matrix)
    (p/translate x y)
    (p/rotate theta)
    (p/begin-shape :triangles)
    (p/vertex 0 (* (- r) 2.0))
    (p/vertex (- r) (* r 2.0))
    (p/vertex r (* r 2.0))
    (p/end-shape)
    (p/pop-matrix)))

(defn boids [x y]
  (repeatedly #(make-boid (vec2 x y) 2.0 0.05)))
 
(defn make-flock []
  (let [x (/ *width* 2.0)
        y (/ *height* 2.0)]
   (reset! aflock (into [] (take *boid-count* (boids x y))))))

(declare flock-run)

(defn setup []
  (p/smooth)
  (make-flock))

(defn draw []
  (p/background-int 50)
  (flock-run))
 
(applet/defapplet flocking1 :title "Flocking 1"
  :setup setup :draw draw :size [*width* *height*])
 
(defn steer [{ms :max-speed, mf :max-force, vel :vel, loc :loc, :as boid} target slowdown]
  (let [{x :x y :y :as desired} (vm/sub target loc)
        d                       (float (p/dist (float 0.0) (float 0.0) (float x) (float y)))]
    (cond 
     (> d (float 0.0)) (if (and slowdown (< d (float 100.0)))
                         (-> desired
                             vm/unit
                             (vm/mul (* ms (/ d (float 100.0))))
                             (vm/sub vel)
                             (limit mf))
                         (-> desired
                             vm/unit
                             (vm/mul ms)
                             (vm/sub vel)
                             (limit mf)))
     true zero)))

(defn distance-map
  [boid boids]
  (let [loc (:loc boid)]
    (map (fn [other] (assoc other :dist (vm/dist (:loc other) loc))) boids)))

(defn distance-filter
  [boids l u]
  (let [l (float l)
        u (float u)]
   (filter (fn [other] (let [d (float (:dist other))] (and (> d l) (< d u)))) boids)))

(defn separation-map [{loc :loc :as boid} boids]
  (map (fn [other]
         (let [d (:dist other)
               oloc (:loc other)]
          (-> loc (vm/sub oloc) vm/unit (vm/div d))))
       boids))
 
(defn separation
  [boid boids]
  (let [dsep     25.0
        filtered (separation-map boid (distance-filter boids 0.0 dsep))]
    (if-let [sum (reduce sum filtered)]
      (vm/div sum (count filtered))
      zero)))

(defn alignment
  [{mf :max-force :as boid} boids]
  (let [nhood    50.0
        filtered (map :vel (distance-filter boids 0 nhood))]
    (if-let [sum (reduce sum filtered)]
      (limit (vm/div sum (count filtered)) mf)
      zero)))
 
(defn cohesion
  [boid boids]
  (let [nhood    50.0
        filtered (map :loc (distance-filter boids 0 nhood))]
    (if-let [sum (reduce sum filtered)]
      (steer boid (vm/div sum (count filtered)) false)
      zero)))
 
(defn flock [{acc :acc, :as boid} boids]
  (let [mboids (distance-map boid boids)
        sep    (-> (separation boid mboids) (vm/mul 2.0))
        ali    (-> (alignment boid mboids) (vm/mul 1.0))
        coh    (-> (cohesion boid mboids) (vm/mul 1.0))]
    (assoc boid :acc (-> acc (vm/add sep) (vm/add ali) (vm/add coh)))))
 
(defn update [{vel :vel, acc :acc, loc :loc, ms :max-speed, :as boid}]
  (assoc boid 
    :vel (limit (vm/add vel acc) ms)
    :loc (vm/add loc vel)
    :acc (vm/mul acc 0.0)))

(defn boid-run [boid boids]
  (-> (flock boid boids) update borders))
 
(defn flock-run-all [flock]
  (map #(boid-run % flock) flock))

(defn flock-run []
  (swap! aflock flock-run-all)
  (doseq [boid @aflock]
    (render boid)))

(comment
  (make-flock)

  ; 11-13ms
  (dotimes [_ 100]
    (time
     (reset! aflock (doall (flock-run-all @aflock)))))

  ; 1.7ms
  (dotimes [_ 100]
    (let [b  (nth @aflock 0)
          bs (distance-map b @aflock)]
     (time
      (doseq [b bs]
        (separation b bs)))))

  ;; test
  (distance-filter (distance-map (nth @aflock 0) @aflock) 0 50.0)

  ; 2.1ms
  (dotimes [_ 100]
    (let [b  (nth @aflock 0)
          bs (distance-map b @aflock)]
     (time
      (doseq [b bs]
        (alignment b bs)))))

  ; 2.2ms
  (dotimes [_ 100]
    (let [b  (nth @aflock 0)
          bs (distance-map b @aflock)]
     (time
      (doseq [b bs]
        (cohesion b bs)))))

  ; 6ms
  (dotimes [_ 100]
    (let [bs @aflock]
     (time
      (doseq [b bs]
        (doall (distance-map b bs))))))

  ; < 1ms
  (dotimes [_ 100]
    (let [b {:loc (vec2 5.25 9.2)}
          v (vec2 3.3 1.1)]
     (time
      (dotimes [_ (* 150 150)]
        (vm/dist (:loc b) v)))))

  (dotimes [_ 100]
    (let [b (vec2 5.25 9.2)
          v (vec2 3.3 1.1)]
     (time
      (dotimes [_ (* 150 150)]
        (vm/dist b v)))))

  (dotimes [_ 100]
    (let [v (into [] (range 10))]
     (time
      (dotimes [_ (* 150 150)]
        (conj v 'x)))))

  ; 2x as fast as below
  (dotimes [_ 10]
    (let [v1 (vec2 0 0)
          v2 (vec2 0 0)
          v3 (vec2 0 0)]
     (time
      (dotimes [_ 1000000]
        (dist-boid v1 v2 v3 2.0 2.0 0.05 20.5)))))

  ; map with 6 entries + assoc
  (dotimes [_ 10]
    (let [m {:a 'a :b 'b :c 'c :d 'd :e 'e :f 'f}]
     (time
      (dotimes [_ 1000000]
        (assoc m :foo "bar")))))
  )