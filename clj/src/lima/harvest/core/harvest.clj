(ns lima.harvest.core.harvest
  (:require [lima.harvest.core.digest :as digest]
            [lima.harvest.core.realize :as realize]
            [failjure.core :as f]))

(defn txns-from-prepared-ef
  "Eduction to harvest from prepared"
  [digest prepared]
  (let [{:keys [hdr txns realizer]} prepared]
    (eduction (comp (realize/xf realizer hdr)
                    (digest/resolve-accid-xf digest)
                    (digest/dedupe-xf digest)
                    (digest/infer-secondary-accounts-xf digest))
              txns)))

;; TODO extract this to somewhere more general
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

(defn txns-from-prepared-xf
  "Transducer to harvest from prepared"
  [config digest]
  (mapcat-or-fail #(txns-from-prepared-ef digest %)))
