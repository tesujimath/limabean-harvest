(ns limabean.harvest.app-test
  (:require [limabean.harvest.app :as sut]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]])
  (:import [java.nio.file Files]))

(def TEST-CASES-DIR "../test-cases")

(defn- sorted-dir-entries
  "Return a sorted list of files in `dir`, an `io/file`"
  [dir]
  (let [unsorted (.list dir)] (sort (vec unsorted))))

(defn get-tests
  "Look for expected output files in test-cases to generate test base paths"
  []
  (->> (sorted-dir-entries (io/file TEST-CASES-DIR))
       (mapv
         (fn [name]
           (let [test-dir (io/file TEST-CASES-DIR name)
                 import-paths
                   ;; sorting matters here for deterministic import of
                   ;; multiple files
                   (->> (sorted-dir-entries test-dir)
                        (mapv #(.getPath (io/file test-dir %)))
                        (filter #(not (or (str/ends-with? % ".beancount")
                                          (str/ends-with? % ".edn")))))
                 context-path (.getPath (io/file test-dir "context.beancount"))
                 config-candidate (io/file test-dir "config.edn")]
             (cond-> {:name name,
                      :context context-path,
                      :expected (slurp (io/file test-dir "expected.beancount")),
                      :import-paths import-paths}
               (.exists config-candidate) (assoc :config
                                            (.getPath config-candidate))))))))

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
      (println
        (format
          "%s actual != expected\n====================\n%s\n====================\n"
          name
          (or (diff name actual expected) "no diffs"))))
    ok))

(deftest import-tests
  (doseq [test (get-tests)]
    (testing (:name test)
      (let [actual (with-out-str (sut/run (:import-paths test)
                                          (select-keys test
                                                       [:config :context])))]
        (is (golden (:name test) actual (:expected test)))))))
