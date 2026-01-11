(ns limabean.harvest.adapter.logging
  (:require [cheshire.core :as cheshire]
            [cheshire.generate :as cheshire-generate]
            [taoensso.telemere :as tel])
  (:import [com.fasterxml.jackson.databind ObjectMapper]
           [com.fasterxml.jackson.datatype.jsr310 JavaTimeModule]))

;; ensure cheshire/jackson can encode Java LocalDate
(cheshire-generate/add-encoder
  java.time.LocalDate
  (fn [^java.time.LocalDate d ^com.fasterxml.jackson.core.JsonGenerator jg]
    (.writeString jg (.toString d))))

(defn json-file-handler
  [path]
  (tel/handler:file {:path path,
                     :output-fn (tel/pr-signal-fn
                                  {:pr-fn cheshire/generate-string})}))

(defn xf
  "Logging transducer"
  [{:keys [ns id level data]}]
  (let [level (or level :info)
        ns (or ns *ns*)
        data (or data {})]
    (map (fn [x] (tel/log! {:id id, :level level, :data (merge data x)}) x))))
