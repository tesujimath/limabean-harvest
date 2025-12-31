(ns lima.harvest.core.pairing-test
  (:require [clojure.test :refer :all]
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
  (testing "try-pair"
    (let [a0 "Assets:Current"
          a1 "Assets:Savings"
          ta (fn [txnid date units]
               {:txnid txnid,
                :date date,
                :units units,
                :acc a0,
                :acc2 [{:name a1}]})
          ts (fn [txnid date units]
               {:txnid txnid,
                :date date,
                :units units,
                :acc a1,
                :acc2 [{:name a0}]})]
      (let [t1 (ta "t1" 1 10.50M)
            t2 (ts "t2" 2 -10.50M)]
        (is (= (sut/try-pair [t1] t2) [[(sut/pair t1 t2)] true]))))))
