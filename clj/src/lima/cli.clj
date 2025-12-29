(ns lima.cli
  (:require [cli-matic.core :as cli-matic]
            [lima.adapter.count :as count]
            [lima.adapter.tabulate :as tabulate]))

(defn report
  "Run the named report"
  [{:keys [name beanfile]}]
  (case name
    "count" (let [inv (count/inventory beanfile)
                  tab (tabulate/inventory inv)]
              (println tab))))

(def CONFIGURATION
  {:command "lima",
   :description "A new implementation of Beancount in Clojure/Rust",
   :version "0.0.1",
   :subcommands [{:command "report",
                  :description "Run a canned Lima report",
                  :opts [{:as "Name",
                          :option "name",
                          :short 0,
                          :type #{"count"},
                          :default "count"}
                         {:as "Bean file",
                          :option "beanfile",
                          :short 1,
                          :type :string,
                          :env "LIMA_BEANFILE",
                          :default :present}],
                  :runs report}]})
