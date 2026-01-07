(ns lima.harvest.core.pairing)



(defn unique-match?
  "Return whether primary account `a` uniquely matches secondary account `s`"
  [a s]
  (and (= (count s) 1) (= a (:name (first s)))))

(defn is-pair?
  "Do the transactions comprise a pair, that is, values sum to zero and the accounts match counter-symmetrically.

  Note: dates are ignored here, a date threshold should be applied before calling this
  note that if either transaction has already been paired (has txnid2), it is
  no longer available.
  "
  [txn0 txn1]
  (and (not (contains? txn0 :txnid2))
       (not (contains? txn1 :txnid2))
       (let [u0 (:units txn0)
             u1 (:units txn1)
             a0 (:acc txn0)
             a1 (:acc txn1)
             s0 (or (:acc2 txn0) [])
             s1 (or (:acc2 txn1) [])]
         (and (= u0 (- u1)) (unique-match? a0 s1) (unique-match? a1 s0)))))


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


;; try pairing a transaction into a vector of txns
(defn try-pair
  [txns txn2]
  (reduce (fn [[acc paired?] txn]
            (if (and (not paired?) (is-pair? txn2 txn))
              [(conj acc (pair txn txn2)) true]
              [(conj acc txn) paired?]))
    [[] false]
    txns))

(defn insert-with-pairing
  "Pair transactions, from a (persistent) map by date of (transient) vec of txn"
  [j-fn j-plus-fn j-window m txn]
  (let [j-base (j-fn txn)]
    (loop [j-offset 0]
      (let [j (j-plus-fn j-base j-offset)
            [txns paired?] (try-pair txn (or (get m j) []))]
        (if paired?
          (assoc m j txns)
          (if (> j-offset 0)
            (recur (- j-offset))
            (let [next-offset (j-plus-fn 1 (abs j-offset))]
              (if (<= next-offset j-window)
                (recur next-offset)
                (update m j-base #(conj txn (or % [])))))))))))


(defn pairing-xf
  "Return a (stateful) transducer to pair opposite transactions up to n-days apart"
  [date-fn n-days]
  (fn [rf]
    (let [state (volatile! (transient {}))] ;; keyed by date, of transient
                                            ;; vec of txn
      (fn
        ;; init
        ([] (rf))
        ;; completion
        ([result]
         (let [m (persistent! @state)]
           (reduce (fn [result k] (reduce rf result (persistent! (get m k))))
             result
             (sort (keys m)))))
        ;; step
        ([result item]
         (let [k (date-fn item)
               m @state
               txns (get m k (transient []))]
           (vreset! state (assoc! m k (conj! txns item))))
         result)))))
