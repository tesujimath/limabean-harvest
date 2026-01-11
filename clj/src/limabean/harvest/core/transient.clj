(ns limabean.harvest.core.transient)

(defn update!
  "The missing update for transient maps"
  [tm k f & args]
  (assoc! tm k (apply f (get tm k) args)))
