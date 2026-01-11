(ns limabean.harvest.core.correlation)

(defn with-id-from
  "Return target with a correlation-id from source"
  [target source]
  (if-let [id (:correlation-id source)]
    (assoc target :correlation-id id)
    target))

(defn xf
  "Return a transducer to add a correlation-id"
  []
  (map (fn [x] (merge x {:correlation-id (random-uuid)}))))
