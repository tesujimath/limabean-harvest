(ns limabean.harvest.core.pairing-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [limabean.harvest.core.gen-txn :as gen-txn]
            [limabean.harvest.core.pairing :as sut]))

(deftest pairable-txns?-test
  (testing "pairable-txns?"
    (is (sut/pairable-txns? {:dct :txn,
                             :units 5,
                             :acc "Assets:Current",
                             :acc2 [{:name "Assets:Savings"}]}
                            {:dct :txn,
                             :units -5,
                             :acc "Assets:Savings",
                             :acc2 [{:name "Assets:Current"}]}))))

(deftest is-not-pair?-test
  (testing "is-not-pair?"
    (is (not (sut/pairable-txns? {:dct :txn, :units 5, :acc "Assets:Current"}
                                 {:dct :txn,
                                  :units -5,
                                  :acc "Assets:Savings",
                                  :acc2 ["Assets:Current"]})))
    (is (not (sut/pairable-txns? {:dct :txn,
                                  :units 5,
                                  :acc "Assets:Current",
                                  :acc2 [{:name "Assets:Savings"}]}
                                 {:dct :txn,
                                  :units -6,
                                  :acc "Assets:Savings",
                                  :acc2 [{:name "Assets:Current"}]})))
    (is (not (sut/pairable-txns? {:dct :txn,
                                  :units 5,
                                  :acc "Assets:Current",
                                  :acc2 [{:name "Assets:Savings"}]}
                                 {:dct :txn,
                                  :units -5,
                                  :acc "Assets:Savings",
                                  :acc2 [{:name "Assets:Current"}
                                         {:name "Assets:Another"}]})))))

(defn equal-modulo-correlation
  [t1 t2]
  (letfn [(without-correlation [x] (dissoc x :correlation-id :provenance))]
    (= (without-correlation t1) (without-correlation t2))))

(deftest pair-test
  (testing "pair"
    (is (equal-modulo-correlation (sut/pair {:txnid "t1"} {:txnid "t2"})
                                  {:txnid "t1", :txnid2 "t2"}))
    (is (equal-modulo-correlation (sut/pair {:txnid "t1"}
                                            {:txnid "t2", :payee "p2"})
                                  {:txnid "t1", :txnid2 "t2", :payee2 "p2"}))
    (is (equal-modulo-correlation
          (sut/pair {:txnid "t1"} {:txnid "t2", :narration "n2"})
          {:txnid "t1", :txnid2 "t2", :narration2 "n2"}))
    (is (equal-modulo-correlation
          (sut/pair {:txnid "t1"} {:txnid "t2", :payee "p2", :narration "n2"})
          {:txnid "t1", :txnid2 "t2", :payee2 "p2", :narration2 "n2"}))))

(defspec pair-txnid-prop-test
         5
         (prop/for-all [[t1 t2] (gen-txn/pairable-txns-gen {:with-txnid true})]
           (let [p (sut/pair t1 t2)
                 common-keys [:dct :date :accid :acc :acc2 :payee :narration
                              :units :cur]]
             (and (= (select-keys p common-keys) (select-keys t1 common-keys))
                  (= (get p :txnid) (get t1 :txnid))
                  (= (get p :txnid2) (get t2 :txnid))))))

(defspec pair-no-txnid-prop-test
         20
         (prop/for-all [[t1 t2] (gen-txn/pairable-txns-gen {:with-txnid false})]
           (let [p (sut/pair t1 t2)
                 common-keys [:dct :date :accid :acc :acc2 :payee :narration
                              :units :cur]]
             (and (= (select-keys p common-keys) (select-keys t1 common-keys))
                  (or (not (:payee t2)) (= (:payee2 p) (:payee t2)))
                  (or (not (:narration t2))
                      (= (:narration2 p) (:narration t2)))))))

(defspec pair-payee-narration-prop-test
         100
         (prop/for-all [[t1 t2] (gen-txn/pairable-txns-gen)]
           (let [p (sut/pair t1 t2)
                 payee2 (:payee t2)
                 narration2 (:narration t2)]
             (and (or (nil? payee2) (= (:payee2 p) payee2))
                  (or (nil? narration2) (= (:narration2 p) narration2))))))

(defspec pair-correlated-prop-test
         20
         (prop/for-all [[t1 t2] (gen-txn/pairable-txns-gen)]
           (let [p (sut/pair t1 t2)
                 common-keys [:dct :date :accid :acc :acc2 :payee :narration
                              :units :cur]]
             (and (= (select-keys p common-keys) (select-keys t1 common-keys))
                  (= (get-in p [:provenance :correlation-ids])
                     [(:correlation-id t1) (:correlation-id t2)])))))

(deftest try-pair-test
  (testing "try-pair nil"
    (let [t2 (gen/generate (gen-txn/qualified-txn-gen))
          [result paired?] (sut/try-pair nil t2)]
      (is (not paired?))
      (is (= result nil))))
  (testing "try-pair not pairable"
    (let [t0 (gen/generate (gen-txn/qualified-txn-gen))
          t1 (gen/generate (gen-txn/qualified-txn-gen))
          t2 (gen/generate (gen-txn/qualified-txn-gen))
          txns [t0 t1]
          [result paired?] (sut/try-pair txns t2)]
      (is (not paired?))
      (is (= result txns))))
  (testing "try-pair pairable"
    (let [t0 (gen/generate (gen-txn/qualified-txn-gen 0 3))
          [t1 t2] (gen/generate (gen-txn/pairable-txns-gen))
          txns [t0 t1]
          [[r0 rp] paired?] (sut/try-pair txns t2)]
      (is paired?)
      (is (= r0 t0))
      (is (equal-modulo-correlation rp (sut/pair t1 t2))))))

(defn merge-pairable-txns
  [w tm t2]
  (persistent! ((sut/merge-pairable-txns! w) tm t2)))

(deftest merge-pairable-txns!-test
  (testing "merge-pairable-txns! not pairable different dates"
    (let [t0 (gen/generate (gen-txn/qualified-txn-gen))
          t1 (gen/generate (gen-txn/qualified-txn-gen))
          t2 (gen/generate (gen-txn/qualified-txn-gen))
          tm (transient {(:date t0) [t0], (:date t1) [t1]})
          result (merge-pairable-txns 0 tm t2)]
      (is (= result {(:date t0) [t0], (:date t1) [t1], (:date t2) [t2]}))))
  (testing "merge-pairable-txns! not pairable same date"
    (let [t0 (gen/generate (gen-txn/qualified-txn-gen))
          t1 (gen/generate (gen-txn/qualified-txn-gen))
          t2 (assoc (gen/generate (gen-txn/qualified-txn-gen)) :date (:date t1))
          tm (transient {(:date t0) [t0], (:date t1) [t1]})
          result (merge-pairable-txns 0 tm t2)]
      (is (= result {(:date t0) [t0], (:date t1) [t1 t2]}))))
  (testing "merge-pairable-txns! pairable"
    (let [t0 (gen/generate (gen-txn/qualified-txn-gen 0 3))
          [t1 t2] (gen/generate (gen-txn/pairable-txns-gen))
          tm (transient {(:date t0) [t0], (:date t1) [t1]})
          result (merge-pairable-txns 0 tm t2)]
      (is (= (get result (:date t0)) [t0]))
      (let [[r1 r1'] (get result (:date t1))]
        (is (equal-modulo-correlation r1 (sut/pair t1 t2)))
        (is (nil? r1'))))))
