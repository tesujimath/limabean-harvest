(ns limabean.harvest.core.correlation)

(defn with-id
  "Add a new correlation-id to x"
  [x]
  (merge x {:correlation-id (random-uuid)}))

(defn with-id-from
  "Return target with a correlation-id from source"
  [x source]
  (if-let [id (:correlation-id source)]
    (assoc x :correlation-id id)
    x))

(defn with-provenance
  "Merge correlation-ids from sources with any existing provenance"
  [x sources]
  (let [source-ids (mapv :correlation-id sources)]
    (update-in x [:provenance :correlation-ids] #(into source-ids %))))

(defn new-with-provenance
  "Return target with a new correlation-id and provenance from sources"
  [x sources]
  (-> x
      (with-provenance sources)
      (with-id)))

(defn xf "Return a transducer to add a correlation-id" [] (map with-id))
