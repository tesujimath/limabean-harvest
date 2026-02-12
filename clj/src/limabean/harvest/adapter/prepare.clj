(ns limabean.harvest.adapter.prepare
  (:require [cheshire.core :as cheshire]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [limabean.harvest.core.glob :as glob]
            [taoensso.telemere :as tel]))

(defn select-by-path
  "Return the classifier if selected, augmented with path and meta data"
  [classifier import-path]
  (if-let [path-glob (get-in classifier [:selector :path-glob])]
    (and (glob/match? path-glob import-path)
         (merge classifier
                {:path import-path,
                 :meta {:path import-path, :classifier (:id classifier)}}))
    nil))

(defn classify
  "Classify an import."
  [import-path config]
  (if-let [classifiers (:classifiers config)]
    (or (some #(select-by-path % import-path) classifiers)
        (throw (ex-info "Failed to classify import by path-glob"
                        {:type :limabean.harvest/error-import-path,
                         :import-path import-path,
                         :config-path (:path config)})))
    (throw (ex-info "No classifiers found"
                    {:type :limabean.harvest/error-config,
                     :config-path (:path config)}))))

(defn infer-accid-from-path
  "Infer the accid from the path of the import file, by matching against accids in the digest"
  [hdr digest path]
  (let [accids (or (and digest (:accids digest)) {})
        matching (filterv #(str/includes? path %) (keys accids))]
    (case (count matching)
      0 (do (tel/log! {:id ::infer-accid-from-path,
                       :msg (format
                              "infer-from-path failed - no accid matches %s"
                              path)})
            hdr)
      1 (let [matched (first matching)]
          (tel/log!
            {:id ::infer-accid-from-path,
             :msg (format "infer-from-path for %s matched %s" path matched)})
          (assoc hdr :inferred-accid matched))
      (do (tel/log!
            {:id ::infer-accid-from-path,
             :msg (format "infer-from-path for %s ignoring multiple matches %s"
                          path
                          matching)})
          hdr))))

(defn infer-header-fields
  "Augment the header of a classified import with any inferred fields."
  [classified digest]
  (update classified :hdr infer-accid-from-path digest (:path classified)))

(defn substitute
  "Substitute k for v among items"
  [items k v]
  (mapv #(if (= % k) v %) items))

(defn ingest
  "Ingest an import file once it has been classified"
  [classified]
  (let [{:keys [ingester path]} classified
        cmd (substitute ingester :path path)
        ingested (apply shell/sh cmd)]
    (if (= (:exit ingested) 0)
      (-> (:out ingested)
          (cheshire/parse-string true)
          (assoc :meta (:meta classified))
          (update :hdr #(merge % (:hdr classified))))
      (throw (ex-info (format "Failed to ingest %s" path)
                      {:type :limabean.harvest/error-external-command,
                       :command cmd,
                       :details (:err ingested)})))))

(defn resolve-base-realizer
  "If the realizer has :base, resolve it among those defined earlier"
  [r realizers config-path]
  (if-let [base-id (:base r)]
    (let [earlier (take-while #(not= (:id r) (:id %)) realizers)
          earlier-by-id (into {} (map #(vector (:id %) %) earlier))
          base (get earlier-by-id base-id)]
      (if base
        (merge (resolve-base-realizer base earlier config-path) r)
        (throw (ex-info "Failed to find base for realizer"
                        {:type :limabean.harvest/error-no-base-realizer,
                         :realizer (:id r),
                         :config-path config-path}))))
    r))

(defn get-realizer
  "Find the first realizer whose selector matches the ingested header"
  [ingested config]
  (if-let [realizers (:realizers config)]
    (let [hdr (:hdr ingested)
          r0 (or (some #(let [sel (:selector %)]
                          (and (= sel (select-keys hdr (keys sel))) %))
                       realizers)
                 (throw (ex-info "Failed to find realizer"
                                 {:type
                                    :limabean.harvest/error-unmatched-realizer,
                                  :hdr hdr,
                                  :import-path (get-in ingested [:meta :path]),
                                  :config-path (:path config)})))
          r1 (resolve-base-realizer r0 realizers (:path config))]
      (if (:txn r1)
        r1
        (throw (ex-info "Realizer missing :txn definition after base resolution"
                        {:type :limabean.harvest/error-no-txn-realizer,
                         :realizer (:id r1),
                         :config-path (:path config)}))))
    (throw (ex-info "No realizers"
                    {:type :limabean.harvest/error-config,
                     :config-path (:path config)}))))

(defn prepare
  "Classify, infer header fields, and ingest a single import file, and resolve its realizer"
  [import-path config digest]
  (let [classified (classify import-path config)
        _ (tel/log! {:id ::classify, :data classified})
        inferred (infer-header-fields classified digest)
        _ (tel/log! {:id ::infer-hdr, :data inferred})
        ingested (ingest inferred)
        realizer (get-realizer ingested config)
        _ (tel/log! {:id ::get-realizer, :data realizer})]
    (merge ingested
           {:meta (merge (:meta ingested) {:realizer (:id realizer)}),
            :realizer realizer})))

(defn xf
  "Transducer to prepare import files."
  [config digest]
  (map #(prepare % config digest)))
