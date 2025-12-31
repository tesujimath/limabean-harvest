(ns lima.harvest.adapter.harvest
  (:require [cheshire.core :as cheshire]
            [clojure.edn :as edn]
            [clojure.java.shell :as shell]
            [lima.harvest.core.glob :as glob]
            [clojure.string :as str]
            [java-time.api :as jt]
            [lima.harvest.core.infer :as infer]
            [lima.harvest.core.realize :as realize]
            [failjure.core :as f]))

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
  "Classify an import.

  And augment the header according to hdr-fn, if any."
  [config digest import-path]
  (if-let [classifiers (:classifiers config)]
    (f/attempt-all [classified
                      (or (some #(select-by-path % import-path) classifiers)
                          (f/fail
                            "failed to classify %s matching path-globs in %s"
                            import-path
                            (:path config)))
                    hdr-fn (:hdr-fn classified)]
      (if hdr-fn (hdr-fn digest classified) classified))
    (f/fail "no classifiers specified in %s" (:path config))))

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

(defn prepare-one
  "Classify and ingest a single import file, and resolve its realizer"
  [config digest import-path]
  (f/attempt-all [classified (classify config digest import-path)
                  ingested (ingest classified)
                  realizer (get-realizer config ingested)]
    (merge ingested
           {:meta (merge (:meta ingested) {:realizer (:name realizer)}),
            :realizer realizer})))

(defn dedupe-xf
  "Transducer to dedupe with respect to txnids"
  [txnids]
  (filter #(not (if-let [txnid (:txnid %)] (contains? txnids txnid)))))

(defn infer-secondary-accounts-xf
  "Transducer to infer secondary accounrs from payees and narrations"
  [payees narrations]
  (map (infer/secondary-accounts payees narrations)))

(defn harvest-one-txns
  "Eduction to harvest from prepared"
  [config digest prepared]
  (let [{:keys [hdr txns realizer]} prepared]
    (eduction (comp (realize/xf digest realizer hdr)
                    (dedupe-xf (:txnids digest))
                    (infer-secondary-accounts-xf (:payees digest)
                                                 (:narrations digest)))
              txns)))

(defn harvest-txns
  "Harvest transaction from import paths"
  [config digest import-paths]
  (f/attempt-all [prepareds (mapv #(prepare-one config digest %) import-paths)
                  failures (filterv f/failed? prepareds)]
    (if (seq failures)
      (f/fail (str/join "\n" (map f/message failures)))
      (into [] cat (map #(harvest-one-txns config digest %) prepareds)))))
