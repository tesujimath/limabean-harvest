(ns limabean.harvest.spec.config
  (:require [clojure.spec.alpha :as s]))


(s/def ::ingester-arg
  (s/or :string string?
        :path #{:path}))

(s/def ::ingester (s/coll-of ::ingester-arg :kind vector?))
(s/def ::path-glob string?)
(s/def ::classifier-selector (s/keys :req-un [::path-glob]))
(s/def ::hdr (s/map-of keyword? string?))

(s/def ::classifier
  (s/keys :req-un [::id ::selector ::ingester] :opt-un [::hdr]))

(s/def ::classifiers (s/coll-of ::classifier :kind vector?))


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

(s/def ::id keyword?)
(s/def ::base keyword?)
(s/def ::selector (s/map-of keyword? string?))
(s/def ::bal ::field-map)
(s/def ::bal-fns (s/coll-of symbol? :kind vector?))
(s/def ::txn ::field-map)
(s/def ::txn-fns (s/coll-of symbol? :kind vector?))

(s/def ::realizer
  (s/keys :req-un [::id ::selector]
          :opt-un [::base ::bal ::bal-fns ::txn ::txn-fns]))

(s/def ::realizers (s/coll-of ::realizer :kind vector?))


(s/def ::comment int?)
(s/def ::units int?)
(s/def ::columns (s/keys :opt-un [::comment ::units]))

(s/def ::assets string?)
(s/def ::expenses string?)
(s/def ::income string?)
(s/def ::acc (s/keys :opt-un [::assets ::expenses ::income]))
(s/def ::default (s/keys :opt-un [::acc]))

(s/def ::indent int?)

(s/def ::output (s/keys :opt-un [::columns ::default ::indent]))

(s/def ::window int?)
(s/def ::pairing (s/nilable (s/keys :opt-un [::window])))


(s/def ::path string?)

(s/def ::raw-config
  (s/keys :req-un [::classifiers ::realizers] :opt-un [::output ::pairing]))
(s/def ::config (s/merge ::raw-config (s/keys :req-un [::path])))
