(ns limabean.harvest.main
  (:require [cli-matic.core :as cli-matic])
  (:require [limabean.harvest.app :as app])
  (:gen-class))

(def CLI
  {:command "limabean-harvest",
   :version "0.0.1",
   :description "Import various format files into Beancount",
   :opts [{:as "Beancount file path for import context",
           :option "context",
           :type :string,
           :env "LIMABEAN_BEANFILE"}
          {:as "Import config path",
           :option "config",
           :type :string,
           :env "LIMABEAN_HARVEST_CONFIG"}
          {:as
             "Generate include directive so import file may be used standalone",
           :option "standalone",
           :type :with-flag}],
   :runs (fn [args] (app/run (:_arguments args) (dissoc args :_arguments)))})

(defn -main [& args] (cli-matic/run-cmd args CLI))
