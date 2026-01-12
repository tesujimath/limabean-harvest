(ns limabean.harvest.adapter.config
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [limabean.harvest.core.spec-support :refer [conform-or-fail]]
            [limabean.harvest.spec.config :as spec]
            [failjure.core :as f]))

(defn resolve-qualified-symbol
  [sym]
  (let [[ns-name sym-name] (str/split (str sym) #"/")
        ns (symbol ns-name)]
    (require ns)
    (ns-resolve ns (symbol sym-name))))

(defn resolve-qualified-symbols
  "Resolve qualified symbols for maps with the given (optional) key,
  whose value is a vector of symbols which must all resolve, otherwise failure."
  [m k]
  (if-let [fns (get m k)]
    (assoc m
      k (mapv #(or (resolve-qualified-symbol %)
                   ;; TODO use f/fail instead of throw
                   (throw (Exception. (format "failed to resolve %s" %))))
          fns))
    m))

(defn resolve-fns-symbols
  [cfg]
  (-> cfg
      (update :realizers
              (fn [realizers]
                (mapv #(-> %
                           (resolve-qualified-symbols :txn-fns)
                           (resolve-qualified-symbols :bal-fns))
                  realizers)))))

(defn read-from-file
  "Read harvest config from EDN file, and resolve any function symbols"
  [config-path]
  (f/attempt-all [raw-string (slurp config-path)
                  raw-config (assoc (edn/read-string raw-string)
                               :path config-path)
                  validated-config (conform-or-fail
                                     ::spec/raw-config
                                     raw-config
                                     (format "Failed reading config from %s"
                                             config-path))
                  resolved-config (resolve-fns-symbols raw-config)]
    resolved-config))
