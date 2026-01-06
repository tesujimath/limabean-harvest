(ns lima.harvest.main
  (:require [cli-matic.core :as cli-matic])
  (:require [lima.harvest.app :as app])
  (:gen-class))

(def CLI
  {:command "lima-harvest",
   :version "0.0.1",
   :description "Import various format files into Beancount",
   :opts [{:as "Beancount file path for import context",
           :option "context",
           :type :string,
           :env "LIMA_BEANPATH"}
          {:as "Import config path",
           :option "config",
           :type :string,
           :env "LIMA_HARVEST_CONFIG"}],
   :runs (fn [{maybe-config-path :config,
               maybe-beanpath :context,
               import-paths :_arguments}]
           (app/run maybe-config-path maybe-beanpath import-paths))})

(defn -main [& args] (cli-matic/run-cmd args CLI))
