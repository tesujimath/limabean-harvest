(ns lima.harvest.spec.txn
  (:require [clojure.spec.alpha :as s]
            [java-time.api :as jt]))

(s/def ::acc string?)
(s/def ::accid string?)
(s/def ::comment string?)
(s/def ::cur string?)
(s/def ::date jt/local-date?)
(s/def ::infer-category string?)
(s/def ::infer-count int?)
(s/def ::name string?)
(s/def ::narration string?)
(s/def ::narration2 string?)
(s/def ::payee string?)
(s/def ::payee2 string?)
(s/def ::txnid string?)
(s/def ::txnid2 string?)
(s/def ::units decimal?)

;; secondary accounts have a name, and optionally information about the
;; inference
(s/def ::acc2
  (s/keys :req-un [::name] :opt-un [::infer-count ::infer-category]))

(s/def ::realized-txn
  (s/keys :req-un [::date ::units ::cur]
          :opt-un [::accid ::txnid ::payee ::narration]))

(s/def ::qualified-txn (s/merge ::realized-txn (s/keys :opt-un [::acc ::acc2])))

(s/def ::paired-txn
  (s/merge ::qualified-txn (s/keys :opt-un [::txnid2 ::payee2 ::narration2
                                            ::comment])))
