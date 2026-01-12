(ns limabean.harvest.core.format
  (:require [clojure.string :as str]))

;; TODO get these from config:
(def INDENT "  ")
(def UNIT-COLUMN 76)
(def COMMENT-COLUMN 41)
(def UNKNOWN_ACC "Assets:Unknown")

;; but not these:
(def TXNID_KEY "txnid")
(def TXNID2_KEY "txnid2")
(def PAYEE2_KEY "payee2")
(def NARRATION2_KEY "narration2")

(defn escape-string [s] (str/escape s {\" "\\\"", \\ "\\\\"}))

(defn payee-narration
  "Format payee/narration, including sep prefix if anything to format"
  [sep txn]
  (let [p (:payee txn)
        n (:narration txn)]
    (cond (and p n)
            (format "%s\"%s\" \"%s\"" sep (escape-string p) (escape-string n))
          (and (nil? p) (nil? n)) ""
          p (format "%s\"%s\" \"\"" sep (escape-string p))
          n (format "%s\"%s\"" sep (escape-string n)))))

(defn decimal->anchored-string
  "Convert decimal to string anchored at the units digit, so will align with e.g. integers"
  [d]
  (let [s (str d) dp (or (str/index-of s ".") (count s))] [s (dec dp)]))

(defn format-acc-amount
  "Format account and amount, with unit digit of units at UNIT-COLUMN"
  [prefix acc units cur]
  (let [prefixed-acc (format "%s%s" prefix (or acc UNKNOWN_ACC))
        width (count prefixed-acc)
        [u-str u-anchor] (decimal->anchored-string units)
        n-pad (max 1 (- UNIT-COLUMN (+ width u-anchor 1)))
        pad (apply str (repeat n-pad " "))]
    (format "%s%s%s %s\n" prefixed-acc pad u-str cur)))

(defn post-acc2
  "Format a post for a secondary acc, with comment if any at COMMENT-COLUMN"
  [acc2]
  (let [indent-acc (format "%s%s" INDENT (:name acc2))
        infer (:infer acc2)]
    (if infer
      (let [width (count indent-acc)
            n-pad (max 1 (- COMMENT-COLUMN (+ width 1)))
            pad (apply str (repeat n-pad " "))
            plural (if (> (:count infer) 1) "s" "")]
        (format "%s%s; inferred from %d %s%s\n"
                indent-acc
                pad
                (:count infer)
                (:category infer)
                plural))
      (format "%s\n" indent-acc))))

(defn transaction
  "format a transaction"
  [txn]
  (format "%tF txn%s\n%s%s%s%s%s%s%s"
          (:date txn)
          (payee-narration " " txn)
          (if-let [comment (:comment txn)]
            (format "%s; %s\n" INDENT comment)
            "")
          (if-let [txnid (:txnid txn)]
            (format "%s%s: \"%s\"\n" INDENT TXNID_KEY txnid)
            "")
          (if-let [txnid2 (:txnid2 txn)]
            (format "%s%s: \"%s\"\n" INDENT TXNID2_KEY txnid2)
            "")
          (if-let [payee2 (:payee2 txn)]
            (format "%s%s: \"%s\"\n" INDENT PAYEE2_KEY payee2)
            "")
          (if-let [narration2 (:narration2 txn)]
            (format "%s%s: \"%s\"\n" INDENT NARRATION2_KEY narration2)
            "")
          (format-acc-amount INDENT (:acc txn) (:units txn) (:cur txn))
          (str/join (map post-acc2 (:acc2 txn)))))

(defn balance
  "Format a balance"
  [bal]
  (format-acc-amount (format "%tF balance " (:date bal))
                     (:acc bal)
                     (:units bal)
                     (:cur bal)))

(defn directive
  "Format a directive"
  [d]
  (case (:dct d)
    :txn (transaction d)
    :bal (balance d)))

(defn xf "Formatting transducer" [] (map directive))
