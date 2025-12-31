(ns lima.harvest.core.txn-gen
  (:require [lima.harvest.core.txn :as sut]
            [clojure.test :as t]
            [clojure.test.check.generators :as gen]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop])
  (:import [java.time LocalDate]
           [java.math BigDecimal]))

(def date-gen
  (gen/fmap (fn [days-since-epoch] (LocalDate/ofEpochDay days-since-epoch))
            (gen/choose 15000 20000)))

(def accid-gen (gen/fmap #(format "acc-%d" %) (gen/choose 100000 999999)))

(defn cents->units [cents] (BigDecimal. (.toBigInteger (bigint cents)) 2))

(def units-pos-gen (gen/fmap cents->units (gen/choose 1 25000)))

(def units-neg-gen (gen/fmap cents->units (gen/choose -25000 -1)))

(def units-gen (gen/fmap cents->units (gen/choose -25000 25000)))

(def cur-gen (gen/elements [:CAD :GBP :EUR :NZD]))

(def payee-gen (gen/fmap #(format "payee-%02d" %) (gen/choose 1 99)))

(def narration-gen
  (gen/fmap clojure.string/join (gen/vector gen/char-alpha 3 8)))

(def txnid-gen (gen/fmap #(format "txn-%d" %) (gen/choose 100000 999999)))

(defn realized-txn-gen
  "Generate a realized txn, with accid among known-accids with freq-known, and randomly otherwise"
  [known-accids freq-known freq-unknown]
  (gen/let [date date-gen
            accid (gen/frequency [[freq-known (gen/elements known-accids)]
                                  [freq-unknown accid-gen]])
            payee (gen/frequency [[8 payee-gen] [2 (gen/return nil)]])
            units units-gen
            cur cur-gen]
    (into {}
          (keep (fn [[k v]] (when v [k v])))
          [[:date date] [:accid accid] [:payee payee] [:units units]
           [:cur cur]])))

(defn realized-txn-gen
  "Generate a realized txn, with accid among known-accids with freq-known, and randomly otherwise"
  [known-accids freq-known freq-unknown]
  (gen/let [date date-gen
            accid (gen/frequency [[freq-known (gen/elements known-accids)]
                                  [freq-unknown accid-gen]])
            payee (gen/frequency [[8 payee-gen] [2 (gen/return nil)]])
            units units-gen
            cur cur-gen]
    (into {}
          (keep (fn [[k v]] (when v [k v])))
          [[:date date] [:accid accid] [:payee payee] [:units units]
           [:cur cur]])))
