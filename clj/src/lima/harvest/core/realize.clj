(ns lima.harvest.core.realize
  (:require [clojure.string :as str]
            [failjure.core :as f]
            [java-time.api :as jt]
            [lima.harvest.core.correlation :as correlation]))

(defn realize-field
  [hdr txn r]
  (cond (string? r) r
        (map? r) (let [{:keys [:fmt :key :src :type]} r]
                   (let [src (case src
                               :hdr hdr
                               :txn txn)
                         v-raw (get src key)
                         fmt (or fmt "yyyy-MM-dd")]
                     (case type
                       :date (jt/local-date fmt v-raw)
                       :decimal (BigDecimal. v-raw)
                       nil v-raw)))
        (vector? r) (str/join "" (map #(realize-field hdr txn %) r))
        ;; TODO validate this ahead of time so we can't fail here
        :else (throw (Exception. (str "bad realizer val " r)))))

(defn realize-txn
  "Realize the transaction, and if txn-fn is defined, apply that after the event."
  [realizer txn-fn hdr txn]
  (let [txn1 (into {}
                   (map (fn [[k v]] [k
                                     (realize-field hdr txn (get realizer k))])
                     realizer))
        txn2 (if txn-fn (txn-fn txn1) txn1)]
    (correlation/with-id-from txn2 txn)))

(defn xf
  "Transducer to realize transactions"
  [realizer hdr]
  (map (fn [txn] (realize-txn (:txn realizer) (:txn-fn realizer) hdr txn))))
