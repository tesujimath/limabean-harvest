(ns limabean.harvest.adapter.config
  (:require [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [expound.alpha :as expound]
            [limabean.harvest.spec.config :as spec]
            [limabean.harvest.core.error :as error]))

(defn resolve-qualified-symbol
  [sym]
  (let [[ns-name sym-name] (str/split (str sym) #"/")
        ns (symbol ns-name)]
    (require ns)
    (ns-resolve ns (symbol sym-name))))

(defn resolve-qualified-symbols
  "Resolve qualified symbols for maps with the given (optional) key,
  whose value is a vector of symbols which must all resolve, otherwise failure."
  [m k ctx]
  (if-let [fns (get m k)]
    (assoc m
      k (mapv #(or (resolve-qualified-symbol %)
                   (throw (ex-info (format "Unknown symbol %s" %)
                                   (merge ctx
                                          {:type
                                             :limabean.harvest/error-config}))))
          fns))
    m))

(defn resolve-fns
  [cfg]
  (let [ctx {:config-path (:path cfg)}]
    (-> cfg
        (update :realizers
                (fn [realizers]
                  (mapv #(-> %
                             (resolve-qualified-symbols :txn-fns ctx)
                             (resolve-qualified-symbols :bal-fns ctx))
                    realizers))))))

(defn read-from-file
  "Read harvest config from EDN file, and resolve any function symbols"
  [config-path]
  (let [raw-string (error/slurp-or-throw
                     config-path
                     (ex-info "Can't read file"
                              {:type :limabean.harvest/error-config,
                               :config-path config-path}))
        raw-config (edn/read-string raw-string)]
    (if (s/valid? ::spec/raw-config raw-config)
      (-> raw-config
          (assoc :path config-path)
          (resolve-fns))
      (throw (ex-info "invalid config"
                      {:type :limabean.harvest/error-config,
                       :config-path config-path,
                       :details (with-out-str (expound/expound
                                                ::spec/raw-config
                                                raw-config))})))))
