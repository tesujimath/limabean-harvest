(ns limabean.harvest.core.realize
  (:require [clojure.string :as str]
            [failjure.core :as f]
            [java-time.api :as jt]
            [limabean.harvest.core.correlation :as correlation]))

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
  (-> (into {}
            (map (fn [[k v]] [k (realize-field hdr txn (get realizer k))])
              realizer))
      (#(if txn-fn (txn-fn %) %))
      (correlation/with-id-from txn)
      (assoc :dct :txn)))

(defn txn-xf
  "Transducer to realize transactions"
  [realizer hdr]
  (map (fn [txn] (realize-txn (:txn realizer) (:txn-fn realizer) hdr txn))))

(defn realize-bal
  "Realize the balance, and if bal-fn is defined, apply that after the event."
  [realizer bal-fn hdr txn]
  (-> (into {}
            (map (fn [[k v]] [k (realize-field hdr txn (get realizer k))])
              realizer))
      (#(if bal-fn (bal-fn %) %))
      (correlation/with-id-from txn)
      (assoc :dct :bal)))

(defn max-by-date [x1 x2] (if (jt/after? (:date x1) (:date x2)) x1 x2))

(defn bal-xf
  "Transducer to realize just the most recent balance, if any"
  [realizer hdr]
  (fn [rf]
    (let [state (volatile! nil)] ;; latest-bal, if any
      (fn
        ;; init
        ([] (rf))
        ;; completion
        ([result]
         (let [latest-bal @state
               ;; emit latest-bal, if any
               result' (if latest-bal (rf result latest-bal) result)]
           (rf result')))
        ;; step
        ([result txn]
         (let [prev-bal @state
               txn-bal (realize-bal (:bal realizer) (:bal-fn realizer) hdr txn)
               latest-bal (if (and prev-bal txn-bal)
                            (max-by-date txn-bal prev-bal)
                            txn-bal)]
           (vreset! state latest-bal))
         result)))))
