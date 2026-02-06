(ns limabean.harvest.api
  (:require [java-time.api :as jt]))

;; functions which may be referenced from config, for which stability is
;; important

(defn inc-date
  "If there's a date field of the correct type, increment it"
  [x]
  (cond-> x
    (and (:date x) (jt/local-date? (:date x)))
      (assoc :date (jt/plus (:date x) (jt/days 1)))))
