(ns lima.harvest.core.realize
  (:require [clojure.string :as str]
            [java-time.api :as jt]
            [failjure.core :as f]))

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
  "Realize the transaction, and if f is defined, apply that after the event."
  [realizer f hdr txn]
  (let [m (into {}
                (map (fn [[k v]] [k (realize-field hdr txn (get realizer k))])
                  realizer))]
    (if f (f m) m)))

(defn lookup-accid
  "Lookup the accid if any in the digest for the account"
  [digest txn]
  (if-let [accid (:accid txn)]
    (if-let [acc (get (digest :accids) accid)]
      (assoc txn :acc acc)
      txn)
    txn))

(defn xf
  "Transducer to realize transactions"
  [digest realizer hdr]
  (map #(->> %
             (realize-txn (:txn realizer) (:txn-fn realizer) hdr)
             (lookup-accid digest))))
