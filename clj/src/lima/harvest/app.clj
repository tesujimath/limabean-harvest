(ns lima.harvest.app
  (:require [cli-matic.core :as cli-matic]
            [failjure.core :as f]
            [lima.harvest.adapter.beanfile :as beanfile]
            [lima.harvest.adapter.config :as config]
            [lima.harvest.adapter.prepare :as prepare]
            [lima.harvest.core.config :refer [DEFAULT-CONFIG]]
            [lima.harvest.core.format :as format]
            [lima.harvest.core.harvest :as harvest]
            [lima.harvest.core.pairing :as pairing]
            [taoensso.telemere :as tel]))


(defn harvest-txns
  "Harvest transaction from import paths"
  [config digest import-paths]
  (into []
        (comp (prepare/xf config digest)
              (harvest/txns-from-prepared-xf config digest)
              (if-let [pairing (:pairing config)]
                (let [window (or (:window pairing) 0)
                      _ (tel/log! ["pairing across" window "days"])]
                  (pairing/pairing-xf window))
                identity))
        import-paths))


(defn run
  "lima-harvest entry point after CLI argument processing"
  [maybe-config-path maybe-beanpath import-paths]
  (f/attempt-all [config (if maybe-config-path
                           (config/read-from-file maybe-config-path)
                           DEFAULT-CONFIG)
                  digest (if maybe-beanpath
                           (beanfile/digest maybe-beanpath)
                           beanfile/EMPTY-DIGEST)
                  harvested (harvest-txns config digest import-paths)]
    (run! (fn [txn] (println (format/transaction txn))) harvested)
    (f/when-failed [e] (do (println (f/message e) *err*) (System/exit 1)))))
