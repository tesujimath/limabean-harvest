(ns limabean.harvest.adapter.prepare
  (:require [cheshire.core :as cheshire]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [failjure.core :as f]
            [limabean.harvest.core.glob :as glob]
            [taoensso.telemere :as tel]))

(defn select-by-path
  "Return the classifier if selected, augmented with path and meta data"
  [classifier import-path]
  (if-let [path-glob (get-in classifier [:selector :path-glob])]
    (and (glob/match? path-glob import-path)
         (merge classifier
                {:path import-path,
                 :meta {:path import-path, :classifier (:name classifier)}}))
    nil))

(defn classify
  "Classify an import."
  [import-path config]
  (if-let [classifiers (:classifiers config)]
    (or (some #(select-by-path % import-path) classifiers)
        (f/fail "failed to classify %s matching path-globs in %s"
                import-path
                (:path config)))
    (f/fail "no classifiers specified in %s" (:path config))))

(defn infer-accid-from-path
  "Infer the accid from the path of the import file, by matching against accids in the digest"
  [hdr digest path]
  (let [accids (or (and digest (:accids digest)) {})
        matching (filterv #(str/includes? path %) (keys accids))]
    (case (count matching)
      0 (f/fail "infer-from-path failed - no accid matches %s" path)
      1 (assoc hdr :inferred-accid (first matching))
      (f/fail "multiple accids match %s: " path (str/join " " matching)))))

(defn infer-header-fields
  "Augment the header of a classified import with any inferred fields."
  [classified digest]
  (let [hdr-fn (:hdr-fn classified)]
    (if hdr-fn
      (update classified :hdr infer-accid-from-path digest (:path classified))
      classified)))

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
      (f/fail "%s failed: %s" (str/join " " cmd) (:err ingested)))))

(defn get-realizer
  "Find the first realizer whose selector matches the ingested header"
  [ingested config]
  (if-let [realizers (:realizers config)]
    (let [hdr (:hdr ingested)
          realizer (some #(let [sel (:selector %)]
                            (and (= sel (select-keys hdr (keys sel))) %))
                         realizers)]
      (or realizer
          (f/fail "failed to find realizer for ingested %s with hdr %s in %s"
                  (:meta ingested)
                  (str hdr)
                  (:path config))))
    (f/fail "no realizers specified in %s" (:path config))))

(defn prepare
  "Classify, infer header fields, and ingest a single import file, and resolve its realizer"
  [import-path config digest]
  (f/attempt-all [classified (classify import-path config)
                  _ (tel/log! {:id ::classify, :data classified})
                  inferred (infer-header-fields classified digest)
                  ingested (ingest inferred)
                  realizer (get-realizer ingested config)
                  _ (tel/log! {:id ::get-realizer, :data realizer})]
    (merge ingested
           {:meta (merge (:meta ingested) {:realizer (:name realizer)}),
            :realizer realizer})))

(defn xf
  "Transducer to prepare import files, with fast fail."
  [config digest]
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result x]
       (let [prepared (prepare x config digest)]
         ;; fast fail
         (if (f/failed? prepared) (reduced prepared) (rf result prepared)))))))
