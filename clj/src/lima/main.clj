(ns lima.main
  (:require [cli-matic.core :refer [run-cmd]])
  (:require [lima.cli :refer [CONFIGURATION]])
  (:gen-class))

(defn -main [& args] (run-cmd args CONFIGURATION))
