(ns limabean.harvest.api
  (:require [clojure.string :as str]
            [failjure.core :as f]))

;; functions which may be referenced from config, for which stability is
;; important

(defn infer-accid-from-path
  "Infer the accid from the path of the import file, by matching against accids in the digest"
  [hdr digest m]
  (let [accids (or (and digest (:accids digest)) {})
        path (:path m)
        matching (filterv #(str/includes? path %) (keys accids))]
    (case (count matching)
      0 (f/fail "infer-from-path failed - no accid matches %s" path)
      1 (assoc hdr :accid (first matching))
      (f/fail "multiple accids match %s: " path (str/join " " matching)))))
