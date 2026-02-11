(ns local
  (:require [clojure.string :as str]))

(defn lowercase-payee
  "Example realizer function to lowercase the payee.  Use in EDN config as local/lowercase-payee"
  [txn]
  (cond-> txn (:payee txn) (update :payee str/lower-case)))
