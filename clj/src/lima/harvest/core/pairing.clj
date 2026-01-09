(ns lima.harvest.core.pairing
  (:require [java-time.api :as jt]
            [lima.harvest.core.transient :refer [update!]]))

(defn unique-match?
  "Return whether primary account `a` uniquely matches secondary account `s`"
  [a s]
  (and (= (count s) 1) (= a (:name (first s)))))

(defn pairable-txns?
  "Do the transactions comprise a pair, that is, values sum to zero and the accounts match counter-symmetrically.

  Note: dates are ignored here, a date threshold should be applied before calling this
  note that if either transaction has already been paired (has txnid2), it is
  no longer available.
  "
  [txn0 txn1]
  (and (= (:dct txn0) :txn)
       (= (:dct txn1) :txn)
       (not (contains? txn0 :txnid2))
       (not (contains? txn1 :txnid2))
       (let [u0 (:units txn0)
             u1 (:units txn1)
             a0 (:acc txn0)
             a1 (:acc txn1)
             s0 (or (:acc2 txn0) [])
             s1 (or (:acc2 txn1) [])]
         (and (number? u0)
              (number? u1)
              (= u0 (- u1))
              (unique-match? a0 s1)
              (unique-match? a1 s0)))))


(defn pair
  "Pair two transactions by returning the first with txnid2 from the second's
  txnid (if any), otherwise a comment
  also with payee and narration from the second as additional fields
  "
  [txn0 txn2]
  (let [txnid2 (:txnid txn2)
        payee2 (:payee txn2)
        narration2 (:narration txn2)
        with-txnid (if txnid2
                     (assoc txn0 :txnid2 txnid2)
                     (let [comment (format "paired with \"%s\" \"%s\""
                                           (or (:payee txn2) "")
                                           (or (:narration txn2) ""))]
                       (assoc txn0 :comment comment)))
        with-payee (if payee2 (assoc with-txnid :payee2 payee2) with-txnid)
        with-narration
          (if narration2 (assoc with-payee :narration2 narration2) with-payee)]
    with-narration))

(defn try-pair
  "Try to pair txn2 into txns, returning [txns-with-paired true] or [txns false]

  txns may be nil, in which case the return value is [nil false]"
  [txns txn2]
  (if txns
    (let [[acc paired?] (reduce (fn [[acc paired?] txn]
                                  (if (and (not paired?)
                                           (pairable-txns? txn2 txn))
                                    [(conj! acc (pair txn txn2)) true]
                                    [(conj! acc txn) paired?]))
                          [(transient []) false]
                          txns)]
      [(persistent! acc) paired?])
    [nil false]))

(defn merge-pairable-txns!
  "Attempt to pair a transaction into a transient map by date of vec of txn.

  If no pair is found across window days in either direction, simply append in its base date.
  "
  [window]
  (fn [tm txn]
    (let [j-base (:date txn)]
      (loop [j-offset 0]
        (let [j (jt/plus j-base (jt/days j-offset))
              [txns paired?] (try-pair (get tm j) txn)]
          (if paired?
            (assoc! tm j txns)
            (if (> j-offset 0)
              (recur (- j-offset))
              (let [next-offset (inc (abs j-offset))]
                (if (<= next-offset window)
                  (recur next-offset)
                  (update! tm j-base #(conj (or % []) txn)))))))))))
