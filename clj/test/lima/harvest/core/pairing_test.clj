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


(deftest try-pair!-test
  (testing "try-pair!"
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
        (let [[txns paired?] (sut/try-pair! (transient [t1]) t2)]
          (is (= (persistent! txns) [(sut/pair t1 t2)]))
          (is (= paired? true))))
      ())))

(defn persistent-with-values!
  "Persist both the map and its values"
  [m]
  (into {} (map (fn [[k v]] [k (persistent! v)]) (persistent! m))))

(deftest insert-with-pairing!-test
  (testing "insert-with-pairing!"
    (let [t0 (gen/generate (gen-txn/qualified-txn-gen 0 3))
          [ta tb] (gen/generate gen-txn/pairable-txns-gen)
          txns (transient {(:date t0) (transient [t0]),
                           (:date ta) (transient [ta])})]
      (let [result (sut/insert-with-pairing! 0 txns tb)]
        (is (= (persistent-with-values! result)
               {(:date t0) [t0], (:date ta) [(sut/pair ta tb)]}))))))
