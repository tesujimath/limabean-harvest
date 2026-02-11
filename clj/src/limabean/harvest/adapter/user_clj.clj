(ns limabean.harvest.adapter.user-clj
  (:require [clojure.string :as str]
            [limabean.harvest.adapter.exception :refer [print-causes]]))

(defn load-user-cljs
  "Load user Clojure code from $LIMABEAN_HARVEST_USER_CLJ"
  []
  (when-let [cljs (System/getenv "LIMABEAN_HARVEST_USER_CLJ")]
    (run! (fn [clj]
            (try (load-file clj)
                 (catch Exception e
                   (binding [*out* *err*]
                     (println "Failed to load"
                              clj
                              "from $LIMABEAN_HARVEST_USER_CLJ")
                     (print-causes e)))))
          (str/split cljs #":"))))
