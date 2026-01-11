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
  [config import-path]
  (if-let [classifiers (:classifiers config)]
    (or (some #(select-by-path % import-path) classifiers)
        (f/fail "failed to classify %s matching path-globs in %s"
                import-path
                (:path config)))
    (f/fail "no classifiers specified in %s" (:path config))))

(defn augment
  "Augment the header of a classified import according to hdr-fn, if any."
  [digest classified]
  (let [hdr-fn (:hdr-fn classified)]
    (if hdr-fn
      (update classified :hdr hdr-fn digest (select-keys classified [:path]))
      classified)))

(defn substitute
  "Substitute k for v among items"
  [k v items]
  (mapv #(if (= % k) v %) items))

(defn ingest
  "Ingest an import file once it has been classified"
  [classified]
  (let [{:keys [ingester path]} classified
        cmd (substitute :path path ingester)
        ingested (apply shell/sh cmd)]
    (if (= (:exit ingested) 0)
      (-> (:out ingested)
          (cheshire/parse-string true)
          (assoc :meta (:meta classified))
          (update :hdr #(merge % (:hdr classified))))
      (f/fail "%s failed: %s" (str/join " " cmd) (:err ingested)))))

(defn get-realizer
  "Find the first realizer whose selector matches the ingested header"
  [config ingested]
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
  "Classify, augment, and ingest a single import file, and resolve its realizer"
  [config digest import-path]
  (f/attempt-all [classified (classify config import-path)
                  _ (tel/log! {:id ::classify, :data classified})
                  augmented (augment digest classified)
                  ingested (ingest augmented)
                  realizer (get-realizer config ingested)
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
       (let [prepared (prepare config digest x)]
         ;; fast fail
         (if (f/failed? prepared) (reduced prepared) (rf result prepared)))))))
