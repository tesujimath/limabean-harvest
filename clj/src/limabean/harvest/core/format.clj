(ns limabean.harvest.core.format
  (:require [clojure.string :as str]))

;; these may be set in config:
(def INDENT 2)
(def UNIT-COLUMN 75)
(def COMMENT-COLUMN 40)
(def DEFAULT-ASSETS "Assets:Unknown")

;; but not these:
(def TXNID_KEY "txnid")
(def TXNID2_KEY "txnid2")
(def PAYEE2_KEY "payee2")
(def NARRATION2_KEY "narration2")

(defn- spaces [n] (apply str (repeat n " ")))

(defn- escape-string [s] (str/escape s {\" "\\\"", \\ "\\\\"}))

(defn- payee-narration
  "Format payee/narration, including sep prefix if anything to format"
  [sep txn]
  (let [p (:payee txn)
        n (:narration txn)]
    (cond (and p n)
            (format "%s\"%s\" \"%s\"" sep (escape-string p) (escape-string n))
          (and (nil? p) (nil? n)) ""
          p (format "%s\"%s\" \"\"" sep (escape-string p))
          n (format "%s\"%s\"" sep (escape-string n)))))

(defn- decimal->anchored-string
  "Convert decimal to string anchored at the units digit, so will align with e.g. integers"
  [d]
  (let [s (str d) dp (or (str/index-of s ".") (count s))] [s (dec dp)]))

(defn- format-acc-amount
  "Return a function to format account and amount, with unit digit of units at UNIT-COLUMN"
  [config]
  (let [unit-column (get-in config [:columns :units] UNIT-COLUMN)
        default-assets (get-in config [:default :acc :assets] DEFAULT-ASSETS)]
    (fn [prefix acc units cur]
      (let [prefixed-acc (format "%s%s" prefix (or acc default-assets))
            width (count prefixed-acc)
            [u-str u-anchor] (decimal->anchored-string units)
            pad (spaces (max 1 (- (inc unit-column) (+ width u-anchor 1))))]
        (format "%s%s%s %s\n" prefixed-acc pad u-str cur)))))

(defn- post-acc2
  "Return a function to format a post for a secondary acc, with comment if any at COMMENT-COLUMN"
  [config]
  (let [comment-column (get-in config [:columns :comment] COMMENT-COLUMN)]
    (fn [acc2]
      (let [space-indent (spaces (get config :indent INDENT))
            indent-acc (format "%s%s" space-indent (:name acc2))
            infer (:infer acc2)]
        (if infer
          (let [width (count indent-acc)
                n-pad (max 1 (- (inc comment-column) (+ width 1)))
                pad (apply str (repeat n-pad " "))
                plural (if (> (:count infer) 1) "s" "")]
            (format "%s%s; inferred from %d %s%s\n"
                    indent-acc
                    pad
                    (:count infer)
                    (:category infer)
                    plural))
          (format "%s\n" indent-acc))))))

(defn- transaction
  "Return a function to format a transaction"
  [config]
  (let [space-indent (spaces (get config :indent INDENT))]
    (fn [txn]
      (format "%tF txn%s\n%s%s%s%s%s%s"
              (:date txn)
              (payee-narration " " txn)
              (if-let [txnid (:txnid txn)]
                (format "%s%s: \"%s\"\n" space-indent TXNID_KEY txnid)
                "")
              (if-let [txnid2 (:txnid2 txn)]
                (format "%s%s: \"%s\"\n" space-indent TXNID2_KEY txnid2)
                "")
              (if-let [payee2 (:payee2 txn)]
                (format "%s%s: \"%s\"\n" space-indent PAYEE2_KEY payee2)
                "")
              (if-let [narration2 (:narration2 txn)]
                (format "%s%s: \"%s\"\n" space-indent NARRATION2_KEY narration2)
                "")
              ((format-acc-amount config)
                space-indent
                (:acc txn)
                (:units txn)
                (:cur txn))
              (str/join (map (post-acc2 config) (:acc2 txn)))))))

(defn- balance
  "Return a function to format a balance"
  [config]
  (fn [bal]
    ((format-acc-amount config)
      (format "%tF balance " (:date bal))
      (:acc bal)
      (:units bal)
      (:cur bal))))

(defn- directive
  "Return a function to format a directive"
  [config]
  (fn [d]
    (case (:dct d)
      :txn ((transaction config) d)
      :bal ((balance config) d))))

(defn xf "Formatting transducer" [config] (map (directive config)))
