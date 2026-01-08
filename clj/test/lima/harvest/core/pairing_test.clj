(ns lima.harvest.core.pairing-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :as gen]
            [lima.harvest.core.gen-txn :as gen-txn]
            [lima.harvest.core.pairing :as sut]))

(deftest is-pair?-test
  (testing "is-pair?"
    (is
      (sut/is-pair?
        {:units 5, :acc "Assets:Current", :acc2 [{:name "Assets:Savings"}]}
        {:units -5, :acc "Assets:Savings", :acc2 [{:name "Assets:Current"}]}))))

(deftest is-not-pair?-test
  (testing "is-not-pair?"
    (is (not (sut/is-pair?
               {:units 5, :acc "Assets:Current"}
               {:units -5, :acc "Assets:Savings", :acc2 ["Assets:Current"]})))
    (is (not (sut/is-pair? {:units 5,
                            :acc "Assets:Current",
                            :acc2 [{:name "Assets:Savings"}]}
                           {:units -6,
                            :acc "Assets:Savings",
                            :acc2 [{:name "Assets:Current"}]})))
    (is (not
          (sut/is-pair?
            {:units 5, :acc "Assets:Current", :acc2 [{:name "Assets:Savings"}]}
            {:units -5,
             :acc "Assets:Savings",
             :acc2 [{:name "Assets:Current"} {:name "Assets:Another"}]})))))

(deftest pair-test
  (testing "pair"
    (is (= (sut/pair {:txnid "t1"} {:txnid "t2"}) {:txnid "t1", :txnid2 "t2"}))
    (is (= (sut/pair {:txnid "t1"} {:txnid "t2", :payee "p2"})
           {:txnid "t1", :txnid2 "t2", :payee2 "p2"}))
    (is (= (sut/pair {:txnid "t1"} {:txnid "t2", :narration "n2"})
           {:txnid "t1", :txnid2 "t2", :narration2 "n2"}))
    (is (= (sut/pair {:txnid "t1"} {:txnid "t2", :payee "p2", :narration "n2"})
           {:txnid "t1", :txnid2 "t2", :payee2 "p2", :narration2 "n2"}))))


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
      (let [[result paired?] (sut/try-pair txns t2)]
        (is paired?)
        (is (= result [t0 (sut/pair t1 t2)]))))))

(deftest insert-with-pairing!-test
  (testing "insert-with-pairing! not pairable different dates"
    (let [t0 (gen/generate (gen-txn/qualified-txn-gen))
          t1 (gen/generate (gen-txn/qualified-txn-gen))
          t2 (gen/generate (gen-txn/qualified-txn-gen))
          tm (transient {(:date t0) [t0], (:date t1) [t1]})]
      (let [result (sut/insert-with-pairing! 0 tm t2)]
        (is (= (persistent! result)
               {(:date t0) [t0], (:date t1) [t1], (:date t2) [t2]})))))
  (testing "insert-with-pairing! not pairable same date"
    (let [t0 (gen/generate (gen-txn/qualified-txn-gen))
          t1 (gen/generate (gen-txn/qualified-txn-gen))
          t2 (assoc (gen/generate (gen-txn/qualified-txn-gen)) :date (:date t1))
          tm (transient {(:date t0) [t0], (:date t1) [t1]})]
      (let [result (sut/insert-with-pairing! 0 tm t2)]
        (is (= (persistent! result) {(:date t0) [t0], (:date t1) [t1 t2]})))))
  (testing "insert-with-pairing! pairable"
    (let [t0 (gen/generate (gen-txn/qualified-txn-gen 0 3))
          [t1 t2] (gen/generate (gen-txn/pairable-txns-gen 0))
          tm (transient {(:date t0) [t0], (:date t1) [t1]})]
      (let [result (persistent! (sut/insert-with-pairing! 0 tm t2))]
        (is (= result {(:date t0) [t0], (:date t1) [(sut/pair t1 t2)]}))))))
