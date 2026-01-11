(ns limabean.harvest.core.xf
  (:require [failjure.core :as f]))

(defn mapcat-or-fail
  "Transducer for mapcat, which propagates failure immediately."
  [f]
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (if (f/failed? result) result (rf result)))
      ([result x]
       (reduce (fn [result y] (if (f/failed? y) (reduced y) (rf result y)))
         result
         (f x))))))
