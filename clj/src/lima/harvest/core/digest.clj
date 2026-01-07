(ns lima.harvest.core.digest)

(defn resolve-accid-xf
  "Return a transducer to augment with acc by resolving accid if any in the digest"
  [digest]
  (let [{:keys [accids]} digest]
    (map (fn [txn]
           (if-let [accid (:accid txn)]
             (if-let [acc (get accids accid)]
               (assoc txn :acc acc)
               txn)
             txn)))))

(defn dedupe-xf
  "Return a transducer to dedupe with respect to txnids in the digest"
  [digest]
  (let [{:keys [txnids]} digest]
    (filter #(not (if-let [txnid (:txnid %)] (contains? txnids txnid))))))

(defn infer-secondary-accounts-xf
  "Return a transducer to infer secondary accounts from payees and narrations"
  [digest]
  (let [{:keys [payees narrations]} digest]
    (map
      (fn [txn]
        (let [units (or (:units txn) 0M)
              primary-acc (:acc txn)
              found-payee (get payees (:payee txn))
              found-narration (get narrations (:narration txn))
              order-accounts
                (fn [acc-count category]
                  (let [all-account-names (keys acc-count)
                        candidate-account-names (filterv #(not= % primary-acc)
                                                  all-account-names)
                        annotated-accounts (mapv (fn [acc]
                                                   {:name acc,
                                                    :infer-count (get acc-count
                                                                      acc),
                                                    :infer-category category})
                                             candidate-account-names)]
                    (vec (sort
                           ;; by infer-count descending, then by name
                           ;; ascending
                           (fn [acc0 acc1]
                             (let [count-cmp (compare (:infer-count acc1)
                                                      (:infer-count acc0))]
                               (if (not= count-cmp 0)
                                 count-cmp
                                 (compare (:name acc0) (:name acc1)))))
                           annotated-accounts))))
              secondary-accounts
                (cond found-payee (order-accounts found-payee "payee")
                      found-narration (order-accounts found-narration
                                                      "narration")
                      (> units 0) [{:name "Income:Unknown"}]
                      (< units 0) [{:name "Expenses:Unknown"}]
                      :else [])]
          (assoc txn :acc2 secondary-accounts))))))
