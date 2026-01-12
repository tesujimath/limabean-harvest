(ns limabean.harvest.spec
  (:require [clojure.spec.alpha :as s]
            [expound.alpha :as expound]
            [failjure.core :as f]))

(defn conform-or-fail
  "Check v confirms to spec, or fail with message of explanation."
  [spec v context]
  (if (s/valid? spec v)
    v
    (f/fail "%s\n%s" context (with-out-str (expound/expound spec v)))))
