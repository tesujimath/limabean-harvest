(ns limabean.harvest.adapter.exception)

(defn print-causes
  "Prints the message of e and all its causes"
  [^Throwable e]
  (loop [ex e] (when ex (println (.getMessage ex)) (recur (.getCause ex)))))
