(ns lima.harvest.main
  (:require [cli-matic.core :refer [run-cmd]])
  (:require [lima.harvest.cli :refer [CONFIGURATION]])
  (:gen-class))

(defn -main [& args] (run-cmd args CONFIGURATION))
