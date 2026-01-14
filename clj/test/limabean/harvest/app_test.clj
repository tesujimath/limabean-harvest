(ns limabean.harvest.app-test
  (:require [limabean.harvest.app :as sut]
            [clojure.data :as data]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.test :refer :all])
  (:import [java.nio.file Files]))

(defn test-base-path [] (file-seq) [io/file "../test-cases"])

(def TEST-CASES-DIR "../test-cases")
(def TEST-CONFIG-PATH (.getPath (io/file TEST-CASES-DIR "harvest.edn")))

(defn get-tests
  "Look for expected output files in test-cases to generate test base paths"
  []
  (->> (.list (io/file TEST-CASES-DIR))
       (filter #(str/ends-with? % ".expected.beancount"))
       (mapv (fn [expected]
               (let [name (str/replace expected ".expected.beancount" "")
                     import-paths (mapv #(.getPath
                                           (io/file TEST-CASES-DIR name %))
                                    (.list (io/file TEST-CASES-DIR name)))
                     context-candidate (io/file TEST-CASES-DIR
                                                (format "%s.beancount" name))]
                 (cond-> {:name name,
                          :expected (slurp (io/file TEST-CASES-DIR expected)),
                          :import-paths import-paths}
                   (.exists context-candidate)
                     (assoc :context (.getPath context-candidate))))))))

(defn temp-file-path
  [prefix ext]
  (str (Files/createTempFile prefix
                             ext
                             (make-array java.nio.file.attribute.FileAttribute
                                         0))))

(defn temp-beancount-file-with-content
  [name content]
  (let [path (temp-file-path name ".beancount")]
    (with-open [w (io/writer path)] (.write w content))
    path))

(defn diff
  "Return diff as a string, or nil if no diffs"
  [name actual expected]
  (let [actual-path (temp-beancount-file-with-content (format "%s.actual" name)
                                                      actual)
        expected-path (temp-beancount-file-with-content (format "%s.expected"
                                                                name)
                                                        expected)
        diff (shell/sh "diff" actual-path expected-path)]
    (io/delete-file actual-path)
    (io/delete-file expected-path)
    (case (:exit diff)
      0 nil
      1 (:out diff))))

(defn golden
  [name actual expected]
  (let [ok (= actual expected)]
    (when-not ok
      (println (format "====================\n%s\n====================\n"
                       (or (diff name actual expected) "no diffs"))))
    ok))

(deftest import-tests
  (doseq [test (get-tests)]
    (testing (:name test)
      (let [actual (with-out-str (sut/run (:import-paths test)
                                          (merge {:config TEST-CONFIG-PATH}
                                                 (select-keys test
                                                              [:context]))))]
        (is (golden (:name test) actual (:expected test)))))))
