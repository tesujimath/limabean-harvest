(ns limabean.harvest.core.realize
  (:require [clojure.string :as str]
            [java-time.api :as jt]
            [limabean.harvest.core.correlation :as correlation]))

(defn- realize-field
  "Realize a field with the already-validated realizer"
  [hdr txn r ctx]
  (cond (string? r) r
        (map? r) (let [src (case (:src r)
                             :hdr hdr
                             :txn txn)
                       v-raw (get src (:key r))
                       fmt (or (:fmt r) "yyyy-MM-dd")]
                   ;; a field which is absent from the source is not realized
                   (when (some? v-raw)
                     (case (:type r)
                       :date (jt/local-date fmt v-raw)
                       :decimal (BigDecimal. v-raw)
                       nil v-raw)))
        (vector? r) (str/join "" (map #(realize-field hdr txn % ctx) r))))

(defn- thread-fns
  "Thread x through fns"
  [x fns]
  (reduce (fn [v f] (f v)) x fns))

(defn- realize-txn
  "Realize the transaction, threading the realized value through the txn-fns, if any."
  [realizer txn-fns hdr txn ctx]
  (-> (into {}
            (map (fn [[k _v]] [k (realize-field hdr txn (get realizer k) ctx)])
              realizer))
      (thread-fns (or txn-fns []))
      (correlation/with-id-from txn)
      (assoc :dct :txn)))

(defn txn-xf
  "Transducer to realize transactions"
  [realizer hdr ctx]
  (map (fn [txn]
         (realize-txn (:txn realizer) (:txn-fns realizer) hdr txn ctx))))

(defn- realize-bal
  "Realize the balance, and if bal-fn is defined, apply that after the event.
   A balance any of whose fields is absent from the source is not realized at
   all, as is the case for an OFX statement without a LEDGERBAL."
  [realizer bal-fns hdr txn ctx]
  (let [fields (into {}
                     (map (fn [[k _v]]
                            [k (realize-field hdr txn (get realizer k) ctx)])
                       realizer))]
    (when (every? some? (vals fields))
      (-> fields
          (thread-fns (or bal-fns []))
          (correlation/with-id-from txn)
          (assoc :dct :bal)))))

(defn- max-by-date
  "Return the later of `x1`, the more recently seen balance, and `x2`.
   Balances which share the latest date are resolved by `bal-pick`, being
   whichever of :first or :last in statement order is the closing balance."
  [bal-pick x1 x2]
  (let [later? (case bal-pick
                 :last jt/not-before?
                 jt/after?)]
    (if (later? (:date x1) (:date x2)) x1 x2)))

(def ^:private no-bal-xf
  "Transducer which emits nothing, for a realizer without a balance"
  (filter (constantly false)))

(defn bal-xf
  "Transducer to realize just the most recent balance, if any.
   A realizer without a bal emits no balance at all."
  [realizer hdr ctx]
  (if-not (:bal realizer)
    no-bal-xf
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
                 txn-bal (realize-bal (:bal realizer)
                                      (:bal-fns realizer)
                                      hdr
                                      txn
                                      ctx)
                 latest-bal (cond (nil? txn-bal) prev-bal
                                  (nil? prev-bal) txn-bal
                                  :else (max-by-date (:bal-pick realizer)
                                                     txn-bal
                                                     prev-bal))]
             (vreset! state latest-bal))
           result))))))
