(ns lima.harvest.api.contrib.kiwibank-ofx1
  (:require [clojure.string :as str]))

;; Alas Kiwibank OFX1 replicates the payee into the narration, in different
;; ways:
;; -
;; modified by removing payee prefix from narration if it contains a semi-colon

(defn =truncated?
  [truncated untruncated limit]
  (= truncated (subs untruncated 0 (min (count untruncated) limit))))

(defn strip-prefix
  "Strip a prefix from a string if it's there, otherwise just return the string"
  [s prefix]
  (if (str/starts-with? s prefix) (subs s (count prefix)) s))


(defn clean-payee-narration-xf
  "Return a transducer to cleanup payee and narration in a Kiwibank txn"
  []
  (map
    (fn [txn]
      (let [payee (:payee txn)
            narration (:narration txn)
            [prefix narration-proper] (str/split narration #";" 2)
            prefix (str/trim prefix)
            narration-proper (str/trim narration-proper)
            payee-limit 32]
        (cond
          ;; we may have "<payee> ;<narration>"
          (and narration-proper
               (or (str/starts-with? prefix payee)
                   (str/starts-with? (strip-prefix "POS W/D " prefix) payee)))
            (let [updated-payee (assoc txn :payee prefix)]
              (if (empty? narration-proper)
                (dissoc updated-payee :narration)
                (assoc updated-payee :narration narration-proper)))
          ;; if the narration is simply the untruncated version of
          ;; the payee, then replace the payee with it
          (=truncated? payee narration payee-limit)
            (assoc (dissoc txn :narration) :payee narration)
          :else txn)))))
