(ns limabean.harvest.core.sort
  (:require [limabean.harvest.core.transient :refer [update!]]))

(defn append-to-txns!
  "Simply append in its base date."
  [tm txn]
  (let [j-base (:date txn)] (update! tm j-base #(conj (or % []) txn))))

(defn by-date-xf
  "Return a (stateful) transducer to sort by date"
  [insertion-fn!]
  (fn [rf]
    (let [state (volatile! (transient {}))] ;; keyed by date, of transient
      ;; vec of txn
      (fn
        ;; init
        ([] (rf))
        ;; completion
        ([result]
         (let [m (persistent! @state)
               result' (reduce (fn [result k] (reduce rf result (get m k)))
                         result
                         (sort (keys m)))]
           (rf result')))
        ;; step
        ([result txn]
         (let [tm @state] (vreset! state (insertion-fn! tm txn)))
         result)))))
