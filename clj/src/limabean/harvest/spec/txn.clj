(ns limabean.harvest.spec.txn
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [java-time.api :as jt])
  (:import [java.time LocalDate]
           [java.math BigDecimal]))

;; custom generators

(def subacc-gen
  (gen/let [first (gen/elements (map char (range (int \A) (inc (int \Z)))))
            rest (gen/vector (gen/elements (map char
                                             (range (int \a) (inc (int \z)))))
                             2
                             4)]
    (str first (apply str rest))))

(def acc-gen
  (gen/let [type (gen/elements ["Assets" "Liabilities" "Equity" "Income"
                                "Expenses"])
            subaccs (gen/vector subacc-gen 1 3)]
    (format "%s:%s" type (str/join ":" subaccs))))

(def accid-gen (gen/fmap #(format "acc-%d" %) (gen/choose 100000 999999)))

(def cur-gen (gen/elements ["CAD" "GBP" "EUR" "NZD"]))

(def date-gen
  (gen/fmap (fn [days-since-epoch] (LocalDate/ofEpochDay days-since-epoch))
            (gen/choose 15000 20000)))

(def infer-category-gen (gen/elements ["payee" "narration"]))

(defn cents->units [cents] (BigDecimal. (.toBigInteger (bigint cents)) 2))
(def units-pos-gen (gen/fmap cents->units (gen/choose 1 25000)))
(def units-neg-gen (gen/fmap cents->units (gen/choose -25000 -1)))
(def units-gen (gen/fmap cents->units (gen/choose -25000 25000)))


(def payee-gen (gen/fmap #(format "payee-%02d" %) (gen/choose 1 99)))
(def narration-gen
  (gen/fmap clojure.string/join (gen/vector gen/char-alpha 3 8)))

(def txnid-gen (gen/fmap #(format "txn-%d" %) (gen/choose 100000 999999)))


;; specs

(s/def ::dct #{:txn :bal})
(s/def ::acc (s/with-gen string? (fn [] acc-gen)))
(s/def ::accid (s/with-gen string? (fn [] accid-gen)))
(s/def ::comment string?)
(s/def ::cur (s/with-gen string? (fn [] cur-gen)))
(s/def ::date (s/with-gen jt/local-date? (fn [] date-gen)))

(s/def ::category (s/with-gen string? (fn [] infer-category-gen)))
(s/def ::count (s/with-gen int? (fn [] (gen/choose 1 50))))
(s/def ::infer (s/keys :req-un [::category ::count]))

(s/def ::name (s/with-gen string? (fn [] acc-gen)))
(s/def ::narration (s/with-gen string? (fn [] narration-gen)))
(s/def ::narration2 (s/with-gen string? (fn [] narration-gen)))
(s/def ::payee (s/with-gen string? (fn [] payee-gen)))
(s/def ::payee2 (s/with-gen string? (fn [] payee-gen)))
(s/def ::txnid (s/with-gen string? (fn [] txnid-gen)))
(s/def ::txnid2 (s/with-gen string? (fn [] txnid-gen)))
(s/def ::units (s/with-gen decimal? (fn [] units-gen)))


;; secondary accounts have a name, and optionally information about the
;; inference
(s/def ::acc2 (s/keys :req-un [::name] :opt-un [::infer]))

(s/def ::realized-txn
  (s/keys :req-un [::dct ::date ::units ::cur]
          :opt-un [::accid ::txnid ::payee ::narration]))

(s/def ::qualified-txn (s/merge ::realized-txn (s/keys :opt-un [::acc ::acc2])))

(s/def ::paired-txn
  (s/merge ::qualified-txn (s/keys :opt-un [::txnid2 ::payee2 ::narration2
                                            ::comment])))
