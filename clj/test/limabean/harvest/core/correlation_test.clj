(ns limabean.harvest.core.correlation-test
  (:require [clojure.test :refer [deftest is testing]]
            [limabean.harvest.core.correlation :as sut]))

(deftest with-provenance-test
  []
  (testing "with-provenance"
    (is (= (sut/with-provenance {:id :fred, :correlation-id :fred-cor}
                                [{:id :alice, :correlation-id :alice-cor}
                                 {:id :bob, :correlation-id :bob-cor}])
           {:id :fred,
            :correlation-id :fred-cor,
            :provenance {:correlation-ids [:alice-cor :bob-cor]}}))))

(deftest new-with-provenance-test
  []
  (testing "new-with-provenance"
    (let [result (sut/new-with-provenance
                   {:id :fred, :correlation-id :fred-cor}
                   [{:id :alice, :correlation-id :alice-cor}
                    {:id :bob, :correlation-id :bob-cor}])]
      (is (= (dissoc result :correlation-id)
             {:id :fred,
              :provenance {:correlation-ids [:alice-cor :bob-cor]}})))))
