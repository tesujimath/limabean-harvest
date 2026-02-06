(ns limabean.harvest.core.error
  (:require [clojure.string :as str]))

(defn remove-and-format
  "Format e-data for appending to error message and remove keys used"
  [[e-data s] f & keys]
  [(apply dissoc e-data keys) (str s (f e-data))])

(defn format-user
  [e]
  (let [e-data (ex-data e)
        e-type (:type e-data)
        [_ s]
          (cond-> [(dissoc e-data :type) (or (.getMessage e) "unknown error")]
            (= e-type :limabean.harvest/error-config)
              (remove-and-format #(format ", configuration %s" (:config-path %))
                                 :config-path)
            (= e-type :limabean.harvest/error-external-command)
              (remove-and-format #(format "\n%s" (str/join " " (:command %)))
                                 :command)
            (= e-type :limabean.harvest/error-import-path)
              (remove-and-format #(format ", import path %s, configuration %s"
                                          (:import-path %)
                                          (:config-path %))
                                 :import-path
                                 :config-path)
            (= e-type :limabean.harvest/error-unmatched-realizer)
              (remove-and-format
                #(format ", import path %s, import header %s, configuration %s"
                         (:import-path %)
                         (:hdr %)
                         (:config-path %))
                :import-path
                :hdr
                :config-path)
            (:details e-data) (remove-and-format #(format "\n%s\n" (:details %))
                                                 :details)
            ;; append any unprocessed fields from e-data
            true ((fn [[e-data s]]
                    (let [s' (if (seq e-data) (format "%s\n%s" s e-data) s)]
                      [{} s']))))]
    s))

(defn slurp-or-throw
  [path e]
  (try (slurp path) (catch java.io.FileNotFoundException _ (throw e))))
