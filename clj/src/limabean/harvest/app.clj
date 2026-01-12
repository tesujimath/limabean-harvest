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
    (eduction (comp (logging/wrap (correlation/xf)
                                  {:id ::ingested-txn, :data {:hdr hdr}})
                    (logging/wrap (realize/txn-xf realizer hdr)
                                  {:id ::realized-txn})
                    (digest/resolve-accid-xf digest)
                    (digest/dedupe-xf digest)
                    (logging/wrap (digest/infer-secondary-accounts-xf digest)
                                  {:id ::resolved-txn}))
              txns)))

(defn bal-from-prepared-ef
  "Eduction to harvest balance, if any, from a single prepared import file"
  [digest prepared]
  (let [{:keys [hdr txns realizer]} prepared]
    (eduction (comp (logging/wrap (correlation/xf)
                                  {:id ::ingested-txn, :data {:hdr hdr}})
                    (logging/wrap (realize/bal-xf realizer hdr)
                                  {:id ::realized-txn}))
              txns)))

(defn txns-and-bal-from-prepared-xf
  "Return a transducer to harvest txns and balance from a single prepared import file"
  [config digest]
  (xf/mapcat-or-fail (fn [prepared]
                       (eduction (xf/cat-or-fail)
                                 [(txns-from-prepared-ef digest prepared)
                                  (bal-from-prepared-ef digest prepared)]))))

(defn harvest-txns
  "Eduction to harvest transaction from import paths"
  [config digest import-paths]
  (let [date-insertion-fn! (if-let [pairing (:pairing config)]
                             (let [window (or (:window pairing) 0)]
                               (pairing/merge-pairable-txns! window))
                             sort/append-to-txns!)]
    (eduction (comp (prepare/xf config digest)
                    ;; prepared stream
                    (txns-and-bal-from-prepared-xf config digest)
                    ;; txn stream
                    (logging/wrap (sort/by-date-xf date-insertion-fn!)
                                  {:id ::ordered-txn}))
              import-paths)))

(defn run
  "limabean-harvest entry point after CLI argument processing"
  [maybe-config-path maybe-beanpath import-paths]
  (logging/initialize)
  (f/attempt-all [config (if maybe-config-path
                           (config/read-from-file maybe-config-path)
                           DEFAULT-CONFIG)
                  digest (if maybe-beanpath
                           (beanfile/digest maybe-beanpath)
                           beanfile/EMPTY-DIGEST)
                  harvested (harvest-txns config digest import-paths)]
    (run! println (eduction (format/xf) harvested))
    (f/when-failed [e] (do (println (f/message e) *err*) (System/exit 1)))))
