(ns lima.harvest.adapter.config
  (:require [clojure.edn :as edn]
            [lima.harvest.core.spec-support :refer [conform-or-fail]]
            [lima.harvest.spec.config :as spec]
            [failjure.core :as f]))

(defn fn-resolver
  "Return a function resolver for maps with the given (optional) key."
  [k]
  (fn [m]
    (if-let [f (get m k)]
      (assoc m k (resolve f))
      m)))

(defn resolve-fn-symbols
  [cfg]
  (-> cfg
      (update :classifiers #(mapv (fn-resolver :hdr-fn) %))
      (update :realizers #(mapv (fn-resolver :txn-fn) %))))

(defn read-from-file
  "Read harvest config from EDN file, and resolve any function symbols"
  [config-path]
  (f/attempt-all [raw-string (slurp config-path)
                  raw-config (assoc (edn/read-string raw-string)
                               :path config-path)
                  validated-config (conform-or-fail
                                     ::spec/config
                                     raw-config
                                     (format "Failed reading config from %s"
                                             config-path))
                  resolved-config (resolve-fn-symbols raw-config)]
    resolved-config))
