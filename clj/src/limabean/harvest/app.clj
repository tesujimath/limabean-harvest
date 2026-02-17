(ns limabean.harvest.app
  (:require [clojure.java.io :as io]
            [limabean.harvest.adapter.beanfile :as beanfile]
            [limabean.harvest.adapter.config :as config]
            [limabean.harvest.adapter.logging :as logging]
            [limabean.harvest.adapter.prepare :as prepare]
            [limabean.harvest.adapter.user-clj :as user-clj]
            [limabean.harvest.core.correlation :as correlation]
            [limabean.harvest.core.digest :as digest]
            [limabean.harvest.core.error :as error]
            [limabean.harvest.core.format :as format]
            [limabean.harvest.core.pairing :as pairing]
            [limabean.harvest.core.realize :as realize]
            [limabean.harvest.core.sort :as sort]))

(defn txns-from-prepared-ef
  "Eduction to harvest txns from a single prepared import file"
  [config digest prepared]
  (let [{:keys [hdr txns realizer]} prepared]
    (eduction (comp
                (logging/wrap (correlation/xf)
                              {:id ::ingested-txn, :data {:hdr hdr}})
                (logging/wrap
                  (realize/txn-xf realizer hdr {:config-path (:path config)})
                  {:id ::realized-txn})
                (digest/resolve-accid-xf digest)
                (digest/dedupe-xf digest)
                (logging/wrap
                  (digest/infer-secondary-accounts-xf (:output config) digest)
                  {:id ::resolved-txn}))
              txns)))

(defn bal-from-prepared-ef
  "Eduction to harvest balance, if any, from a single prepared import file"
  [config digest prepared]
  (let [{:keys [hdr txns realizer]} prepared]
    (eduction (comp (logging/wrap (correlation/xf)
                                  {:id ::ingested-bal, :data {:hdr hdr}})
                    (logging/wrap (realize/bal-xf realizer
                                                  hdr
                                                  {:config-path (:path config)})
                                  {:id ::realized-bal})
                    (digest/resolve-accid-xf digest))
              txns)))

(defn txns-and-bal-from-prepared-xf
  "Return a transducer to harvest txns and balance from a single prepared import file"
  [config digest]
  (mapcat (fn [prepared]
            (eduction cat
                      [(txns-from-prepared-ef config digest prepared)
                       (bal-from-prepared-ef config digest prepared)]))))

(defn harvest-txns
  "Eduction to harvest transaction from import paths"
  [config digest import-paths]
  (let [date-insertion-fn! (if-let [pairing (:pairing config)]
                             (let [window (or (:window pairing) 0)]
                               (pairing/merge-pairable-txns! window))
                             sort/append-to-txns!)]
    (eduction (comp (prepare/xf config digest)
                    ;; prepared stream
                    (txns-and-bal-from-prepared-xf config digest)
                    ;; txn stream
                    (logging/wrap (sort/by-date-xf date-insertion-fn!)
                                  {:id ::ordered-txn}))
              import-paths)))

(defn run
  "limabean-harvest entry point after CLI argument processing"
  [import-paths opts]
  (try (logging/initialize)
       (binding [*ns* (find-ns 'user)]
         (let [beanfile (:context opts)
               standalone (:standalone opts)
               _ (user-clj/load-user-cljs)
               config (config/build opts)
               digest
                 (if beanfile (beanfile/digest beanfile) beanfile/EMPTY-DIGEST)
               harvested (harvest-txns config digest import-paths)]
           (when (and standalone beanfile)
             (print (format "include \"%s\"\n\n" beanfile)))
           (run! #(print (format "%s\n" %))
                 (eduction (format/xf (:output config)) harvested)))
         (catch clojure.lang.ExceptionInfo e
           (binding [*out* *err*]
             (println (error/format-user e))
             (System/exit 1))))))

(defn version
  "Get the library version from pom.properties, else returns \"unknown\"."
  []
  (or
    (let [props (java.util.Properties.)]
      (try
        (with-open
          [in
             (io/input-stream
               (io/resource
                 "META-INF/maven/io.github.tesujimath/limabean-harvest/pom.properties"))]
          (.load props in)
          (.getProperty props "version"))
        (catch Exception _ nil)))
    "unknown"))
