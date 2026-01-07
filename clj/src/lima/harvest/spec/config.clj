(ns lima.harvest.spec.config
  (:require [clojure.spec.alpha :as s]))


(s/def ::ingester-arg
  (s/or :string string?
        :path #{:path}))

(s/def ::ingester (s/coll-of ::ingester-arg :kind vector?))
(s/def ::path-glob string?)
(s/def ::classifier-selector (s/keys :req-un [::path-glob]))
(s/def ::hdr (s/map-of keyword? string?))
(s/def ::hdr-fn symbol?)

(s/def ::classifier
  (s/keys :req-un [::name ::selector ::ingester] :opt-un [::hdr ::hdr-fn]))




(s/def ::key keyword?)
(s/def ::src #{:hdr :txn})
(s/def ::type #{:date :decimal})
(s/def ::fmt string?)

(s/def ::simple-field
  (s/or :fixed string?
        :lookup (s/keys :req-un [::key ::src] :opt-un [::type ::fmt])))

(s/def ::field
  (s/or :simple ::simple-field
        :composite (s/coll-of ::simple-field :kind vector?)))

(s/def ::field-map (s/map-of keyword? ::field))

(s/def ::name string?)
(s/def ::selector (s/map-of keyword? string?))
(s/def ::bal ::field-map)
(s/def ::txn ::field-map)
(s/def ::txn-fn symbol?)

(s/def ::realizer
  (s/keys :req-un [::name ::selector ::txn] :opt-un [::bal ::txn-fn]))


(s/def ::window int?)


(s/def ::classifiers (s/coll-of ::classifier :kind vector?))
(s/def ::realizers (s/coll-of ::realizer :kind vector?))
(s/def ::pairing (s/keys :opt-un [::window]))


(s/def ::path string?)

(s/def ::raw-config (s/keys :req-un [::classifiers ::realizers]))
(s/def ::config (s/merge ::raw-config (s/keys :req-un [::path])))
