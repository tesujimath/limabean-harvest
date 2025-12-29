(ns lima.adapter.import
  (:require [cheshire.core :as cheshire]
            [clojure.edn :as edn]
            [clojure.java.shell :as shell]))

(def DEFAULT-CONFIG '())

(defn read-config
  "Read import config from EDN file"
  [config-path]
  (let [config (slurp config-path)] (edn/read-string config)))

(defn ingest
  "Ingest a single file using lima-pod"
  [ingest-path]
  (let [ingested (shell/sh "lima-pod" "ingest" ingest-path)]
    (if (= (ingested :exit) 0)
      (cheshire/parse-string (ingested :out))
      (do (println "lima-pod error" (ingested :err))
          (throw (Exception. "lima-pod failed"))))))
