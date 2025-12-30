(ns lima.harvest.cli
  (:require [cli-matic.core :as cli-matic]
            [lima.harvest.adapter.beanfile :as beanfile]
            [lima.harvest.adapter.import :as import]))

(defn import-files
  "Import files"
  [{config-path :config beanpath :context import-paths  :_arguments}]
  (let [config (if config-path (import/read-config config-path) import/DEFAULT-CONFIG)
        digest (and context (beanfile/digest context))
        import-paths _arguments
        classified (mapv (fn [import-path] (import/classify config import-path)) import-paths)
        ingested (import/)]
    (println "import" imports "with digest" digest)
    (import)
    ))

(def CONFIGURATION
  {:command "lima-harvest",
   :description "Import framework for Beancount using Lima",
   :version "0.0.1",
   :subcommands [{:command "import",
                  :description "Import various format files into Beancount",
                  :opts [{:as "Beancount file path for import context",
                          :option "context",
                          :type :string,
                          :env "LIMA_BEANPATH"}
                         {:as "Import config path",
                          :option "config",
                          :type :string,
                          :env "LIMA_IMPORT_CONFIG"
                          }],
                  :runs import-files}]})
