(ns limabean.harvest.core.pairing-test
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
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

(defspec pair-correlated-prop-test
         100
         (prop/for-all [[t1 t2] (gen-txn/pairable-txns-gen 0)]
                       (let [p (sut/pair t1 t2)
                             common-keys [:dct :date :accid :acc :acc2 :payee
                                          :narration :units :cur]]
                         (is (= (select-keys p common-keys)
                                (select-keys t1 common-keys)))
                         (is (= (get-in p [:provenance :correlation-ids])
                                [(:correlation-id t1) (:correlation-id t2)])))))

(deftest try-pair-test
  (testing "try-pair nil"
    (let [t2 (gen/generate (gen-txn/qualified-txn-gen))]
      (let [[result paired?] (sut/try-pair nil t2)]
        (is (not paired?))
        (is (= result nil)))))
  (testing "try-pair not pairable"
    (let [t0 (gen/generate (gen-txn/qualified-txn-gen))
          t1 (gen/generate (gen-txn/qualified-txn-gen))
          t2 (gen/generate (gen-txn/qualified-txn-gen))
          txns [t0 t1]]
      (let [[result paired?] (sut/try-pair txns t2)]
        (is (not paired?))
        (is (= result txns)))))
  (testing "try-pair pairable"
    (let [t0 (gen/generate (gen-txn/qualified-txn-gen 0 3))
          [t1 t2] (gen/generate (gen-txn/pairable-txns-gen 0))
          txns [t0 t1]]
      (let [[[r0 rp] paired?] (sut/try-pair txns t2)]
        (is paired?)
        (is (= r0 t0))
        (is (equal-modulo-correlation rp (sut/pair t1 t2)))))))

(defn merge-pairable-txns
  [w tm t2]
  (persistent! ((sut/merge-pairable-txns! w) tm t2)))

(deftest merge-pairable-txns!-test
  (testing "merge-pairable-txns! not pairable different dates"
    (let [t0 (gen/generate (gen-txn/qualified-txn-gen))
          t1 (gen/generate (gen-txn/qualified-txn-gen))
          t2 (gen/generate (gen-txn/qualified-txn-gen))
          tm (transient {(:date t0) [t0], (:date t1) [t1]})]
      (let [result (merge-pairable-txns 0 tm t2)]
        (is (= result {(:date t0) [t0], (:date t1) [t1], (:date t2) [t2]})))))
  (testing "merge-pairable-txns! not pairable same date"
    (let [t0 (gen/generate (gen-txn/qualified-txn-gen))
          t1 (gen/generate (gen-txn/qualified-txn-gen))
          t2 (assoc (gen/generate (gen-txn/qualified-txn-gen)) :date (:date t1))
          tm (transient {(:date t0) [t0], (:date t1) [t1]})]
      (let [result (merge-pairable-txns 0 tm t2)]
        (is (= result {(:date t0) [t0], (:date t1) [t1 t2]})))))
  (testing "merge-pairable-txns! pairable"
    (let [t0 (gen/generate (gen-txn/qualified-txn-gen 0 3))
          [t1 t2] (gen/generate (gen-txn/pairable-txns-gen 0))
          tm (transient {(:date t0) [t0], (:date t1) [t1]})]
      (let [result (merge-pairable-txns 0 tm t2)]
        (is (= (get result (:date t0)) [t0]))
        (let [[r1 r1'] (get result (:date t1))]
          (is (equal-modulo-correlation r1 (sut/pair t1 t2)))
          (is (nil? r1')))))))
