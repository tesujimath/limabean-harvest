(ns limabean.harvest.main
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [limabean.harvest.app :as app]
            [limabean.harvest.adapter.logging :as logging]
            [taoensso.telemere :as tel])
  (:gen-class))

(def cli-options
  [["-h" "--help" "Help"] ["-v" "--verbose" "Verbose"]
   [nil "--context PATH"
    "path to Beancount file for import context, default $LIMABEAN_BEANFILE"
    :default-fn (fn [_opts] (System/getenv "LIMABEAN_BEANFILE"))]
   [nil "--config PATH" "Import config path, default $LIMABEAN_HARVEST_CONFIG"
    :default-fn (fn [_opts] (System/getenv "LIMABEAN_HARVEST_CONFIG"))]
   [nil "--standalone"
    "Generate include directive so import file may be used standalone"]])

(defn usage
  [options-summary]
  (->> ["limabean-harvest: usage: limabean-harvest [options]" "" "Options:"
        options-summary]
       (str/join \newline)))

(defn error-msg
  [errors]
  (str "limabean-harvest: argument parsing errors:\n"
       (str/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with an error message, and optional ok status), or a map
  with the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args
                                                               cli-options)]
    (tel/log! {:id ::options, :data options})
    (cond
      (:help options) ; help => exit OK with usage summary
        {:exit-message (usage summary), :ok? true}
      errors ; errors => exit with description of errors
        {:exit-message (error-msg errors)}
      ;; custom validation on arguments
      (not (:context options))
        {:exit-message
           "limabean-harvest: --context or $LIMABEAN_BEANFILE is required"}
      (let [context (io/file (:context options))]
        (not (and (.exists context) (.isFile context))))
        {:exit-message (str "limabean-harvest: no such beanfile "
                            (:context options))}
      (not (:context options))
        {:exit-message
           "limabean-harvest: --context or $LIMABEAN_HARVEST_CONFIG is required"}
      (let [config (io/file (:config options))]
        (not (and (.exists config) (.isFile config))))
        {:exit-message (str "limabean-harvest: no such config file "
                            (:config options))}
      (empty? arguments) {:exit-message "no import files given"}
      :else {:options options, :import-paths arguments})))

(defn exit
  [status msg]
  (binding [*out* *err*] (println msg))
  (System/exit status))

(defn -main
  [& args]
  (logging/initialize)
  (tel/log! {:id ::main, :data {:args args}})
  (let [{:keys [import-paths options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (app/run import-paths options)))
  (flush)
  (System/exit 0) ;; TODO check whether this is still required, or does it
                  ;; hang?
)
