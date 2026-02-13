(ns limabean.harvest.adapter.config
  (:require [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [expound.alpha :as expound]
            [limabean.harvest.spec.config :as spec]
            [limabean.harvest.core.config :refer [DEFAULT-CONFIG]]
            [limabean.harvest.core.error :as error]
            [taoensso.telemere :as tel]))

(defn- resolve-qualified-symbol
  [sym]
  (let [[ns-name sym-name] (str/split (str sym) #"/")
        ns (symbol ns-name)]
    (require ns)
    (ns-resolve ns (symbol sym-name))))

(defn- resolve-qualified-symbols
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

(defn- resolve-fns
  [cfg]
  (let [ctx {:config-path (:path cfg)}]
    (-> cfg
        (update :realizers
                (fn [realizers]
                  (mapv #(-> %
                             (resolve-qualified-symbols :txn-fns ctx)
                             (resolve-qualified-symbols :bal-fns ctx))
                    realizers))))))

;; from https://clojuredocs.org/clojure.core/merge-with
(defn- deep-merge
  [& maps]
  (letfn [(reconcile-keys [val-in-result val-in-latter]
            (if (and (map? val-in-result) (map? val-in-latter))
              (merge-with reconcile-keys val-in-result val-in-latter)
              val-in-latter))
          (reconcile-maps [result latter]
            (merge-with reconcile-keys result latter))]
    (reduce reconcile-maps maps)))

(defn- merge-default
  "Merge default config and `cfg`, where classifiers from `cfg` appear first, and realizers second, and output deep-merged."
  [cfg]
  (let [merged (merge DEFAULT-CONFIG
                      cfg
                      {:output (deep-merge (get DEFAULT-CONFIG :output {})
                                           (get cfg :output {}))})]
    (assoc merged
      :classifiers (vec (concat (get cfg :classifiers [])
                                (get DEFAULT-CONFIG :classifiers [])))
      :realizers (vec (concat (get DEFAULT-CONFIG :realizers [])
                              (get cfg :realizers []))))))

(defn build
  "Read harvest config from EDN file (if any), merge with default, and resolve any function symbols"
  [opts]
  (let [config (if-let [config-path (:config opts)]
                 (let [raw-string (error/slurp-or-throw
                                    config-path
                                    (ex-info "Can't read file"
                                             {:type
                                                :limabean.harvest/error-config,
                                              :config-path config-path}))
                       raw-config (edn/read-string raw-string)
                       merged-config
                         (if (s/valid? ::spec/raw-config raw-config)
                           (-> raw-config
                               (assoc :path config-path)
                               (merge-default))
                           (throw (ex-info
                                    "invalid config"
                                    {:type :limabean.harvest/error-config,
                                     :config-path config-path,
                                     :details (with-out-str (expound/expound
                                                              ::spec/raw-config
                                                              raw-config))})))]
                   merged-config)
                 DEFAULT-CONFIG)
        _ (tel/log! {:id ::config, :data config})]
    (resolve-fns config)))
