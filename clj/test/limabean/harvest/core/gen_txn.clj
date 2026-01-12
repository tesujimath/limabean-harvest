(ns limabean.harvest.core.gen-txn
  (:require [clojure.test.check.generators :as gen]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [java-time.api :as jt]
            [limabean.harvest.core.correlation :as correlation]
            [limabean.harvest.spec.txn :as txn])
  (:import [java.time LocalDate]
           [java.math BigDecimal]))


(defn realized-txn-gen
  "Generate a realized txn, with accid among known-accids with freq-known, and randomly otherwise"
  [known-accids freq-known freq-unknown]
  (gen/let [date (s/gen ::txn/date)
            accid (gen/frequency [[freq-known (gen/elements known-accids)]
                                  [freq-unknown (s/gen ::txn/accid)]])
            payee (gen/frequency [[8 (s/gen ::txn/payee)] [2 (gen/return nil)]])
            narration (gen/frequency [[2 (s/gen ::txn/narration)]
                                      [8 (gen/return nil)]])
            units (s/gen ::txn/units)
            cur (s/gen ::txn/cur)]
    (correlation/with-id (into {}
                               (keep (fn [[k v]] (when v [k v])))
                               [[:dct :txn] [:date date] [:accid accid]
                                [:payee payee] [:units units] [:cur cur]]))))

(defn qualified-txn-gen
  "Generate a qualified txn"
  ([] (qualified-txn-gen 0 2))
  ([min-acc2 max-acc2]
   (gen/let [date (s/gen ::txn/date)
             accid (s/gen ::txn/accid)
             acc (s/gen ::txn/acc)
             acc2 (gen/vector (s/gen ::txn/acc2) min-acc2 max-acc2)
             payee (gen/frequency [[8 (s/gen ::txn/payee)]
                                   [2 (gen/return nil)]])
             narration (gen/frequency [[2 (s/gen ::txn/narration)]
                                       [8 (gen/return nil)]])
             units (s/gen ::txn/units)
             cur (s/gen ::txn/cur)]
     (correlation/with-id (into {}
                                (keep (fn [[k v]] (when v [k v])))
                                [[:dct :txn] [:date date] [:accid accid]
                                 [:acc acc] [:acc2 acc2] [:payee payee]
                                 [:narration narration] [:units units]
                                 [:cur cur]])))))

(defn pairable-txns-gen
  "Generate pairable txns"
  [date-offset]
  (gen/let [txn (qualified-txn-gen 1 1)
            accid2 (s/gen ::txn/accid)]
    [txn
     (correlation/with-id (merge txn
                                 {:date (jt/plus (:date txn)
                                                 (jt/days date-offset)),
                                  :accid accid2,
                                  :acc (get-in txn [:acc2 0 :name]),
                                  :acc2 [{:name (:acc txn)}],
                                  :units (- (:units txn))}))]))
