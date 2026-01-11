(ns limabean.harvest.app
  (:require [cli-matic.core :as cli-matic]
            [failjure.core :as f]
            [limabean.harvest.adapter.beanfile :as beanfile]
            [limabean.harvest.adapter.config :as config]
            [limabean.harvest.adapter.logging :as logging]
            [limabean.harvest.adapter.prepare :as prepare]
            [limabean.harvest.core.config :refer [DEFAULT-CONFIG]]
            [limabean.harvest.core.correlation :as correlation]
            [limabean.harvest.core.digest :as digest]
            [limabean.harvest.core.format :as format]
            [limabean.harvest.core.pairing :as pairing]
            [limabean.harvest.core.realize :as realize]
            [limabean.harvest.core.sort :as sort]
            [limabean.harvest.core.xf :as xf]
            [taoensso.telemere :as tel]))


(defn txns-from-prepared-ef
  "Eduction to harvest txns from a single prepared import file"
  [digest prepared]
  (let [{:keys [hdr txns realizer]} prepared]
    (eduction (comp (correlation/xf)
                    (logging/xf {:id ::ingested-txn, :data {:hdr hdr}})
                    (realize/xf realizer hdr)
                    (logging/xf {:id ::realized-txn})
                    (digest/resolve-accid-xf digest)
                    (digest/dedupe-xf digest)
                    (digest/infer-secondary-accounts-xf digest)
                    (logging/xf {:id ::resolved-txn}))
              txns)))

(defn txns-from-prepared-xf
  "Return a transducer to harvest txns from a single prepared import file"
  [config digest]
  (xf/mapcat-or-fail #(txns-from-prepared-ef digest %)))

(defn harvest-txns
  "Harvest transaction from import paths"
  [config digest import-paths]
  (let [date-insertion-fn! (if-let [pairing (:pairing config)]
                             (let [window (or (:window pairing) 0)]
                               (pairing/merge-pairable-txns! window))
                             sort/append-to-txns!)]
    (into []
          (comp (prepare/xf config digest)
                ;; prepared stream
                (txns-from-prepared-xf config digest)
                ;; txn stream
                (sort/by-date-xf date-insertion-fn!)
                (logging/xf {:id ::ordered-txn}))
          import-paths)))

(defn run
  "limabean-harvest entry point after CLI argument processing"
  [maybe-config-path maybe-beanpath import-paths]
  (tel/add-handler! :json-file
                    (logging/json-file-handler "limabean-harvest-log.json"))
  (tel/remove-handler! :default/console)
  (tel/call-on-shutdown! (fn [] (tel/stop-handlers!)))
  ;;(binding [*out* *err*] (println (tel/get-handlers)))
  (f/attempt-all [config (if maybe-config-path
                           (config/read-from-file maybe-config-path)
                           DEFAULT-CONFIG)
                  digest (if maybe-beanpath
                           (beanfile/digest maybe-beanpath)
                           beanfile/EMPTY-DIGEST)
                  harvested (harvest-txns config digest import-paths)]
    (run! (fn [txn] (println (format/transaction txn))) harvested)
    (f/when-failed [e] (do (println (f/message e) *err*) (System/exit 1)))))
