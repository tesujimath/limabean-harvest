(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]
            [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [deps-deploy.deps-deploy :as deps-deploy]))

(defn cargo-version
  "Read the version from lima"
  []
  (-> (sh/sh "cargo"
             "metadata" "--no-deps"
             "--format-version" "1"
             "--manifest-path" "../rust/Cargo.toml")
      :out
      (cheshire/parse-string true)
      :packages
      first
      :version))

(def lib 'io.github.tesujimath/limabean-harvest)
(def version (cargo-version))
(def main 'limabean.harvest.main)
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
;; mvn-local-repo must be an absolute path outside of clj
;; so we can test install the jar without access to local deps.edn
(def mvn-local-repo
  (.getPath (io/file (System/getProperty "user.dir") ".." "target" "m2")))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn- pom-template
  [version]
  [[:description "New import framework and importers for Beancount."]
   [:url "https://github.com/tesujimath/limabean-harvest"]
   [:licenses
    [:license [:name "Apache License, Version 2.0"]
     [:url "https://www.apache.org/licenses/LICENSE-2.0"]]
    [:license [:name "MIT license"]
     [:url "https://opensource.org/licenses/MIT"]]]
   [:developers
    [:developer [:name "Simon Guest"] [:email "simon.guest@tesujimath.org"]
     [:url "https://github.com/tesujimath"]]]
   [:scm [:url "https://github.com/tesujimath/limabean-harvest"]
    [:connection "scm:git:git://github.com/tesujimath/limabean-harvest.git"]
    [:developerConnection
     "scm:git:ssh://git@github.com/tesujimath/limabean-harvest.git"]
    [:tag version]]])

(defn test
  "Run all the tests."
  [opts]
  (let [cmds (b/java-command {:basis basis,
                              :main 'clojure.main,
                              :main-args ["-m" "cognitect.test-runner"]})
        {:keys [exit]} (b/process cmds)]
    (when-not (zero? exit) (throw (ex-info "Tests failed" {}))))
  opts)

(defn clean [opts] (b/delete {:path "target"}) opts)

(defn write-pom
  "Write pom.xml from template"
  [opts]
  (b/write-pom (assoc opts
                 :class-dir class-dir
                 :lib lib
                 :version version
                 :basis basis
                 :src-dirs ["src"]
                 :pom-data (pom-template version)))
  (println "wrote" (format "target/classes/META-INF/maven/%s/pom.xml" lib))
  (assoc opts
    :pom-file (format "target/classes/META-INF/maven/%s/pom.xml" lib)))

(defn- jar-opts
  [opts]
  (assoc opts
    :class-dir class-dir
    :jar-file jar-file
    :manifest {"Implementation-Version" version}))

(defn jar
  [opts]
  (let [opts (clean opts)
        opts (write-pom opts)
        opts (jar-opts opts)]
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["resources" "src"], :target-dir class-dir})
    (println "\nBuilding jar" (:jar-file opts))
    (b/jar opts)
    opts))

(defn install
  "Install the JAR and pom into `mvn-local-repo` for testing"
  [opts]
  (let [opts (jar opts)]
    (let [artifact (:jar-file opts)
          pom-file (:pom-file opts)
          basis {:mvn/local-repo mvn-local-repo}]
      (println "Installing jarfile using basis" basis)
      (b/install (assoc opts
                   :basis basis
                   :lib lib
                   :version version)))
    opts))

(defn deploy
  [opts]
  (let [opts (jar opts)]
    (let [artifact (:jar-file opts)
          pom-file (:pom-file opts)]
      (println "deploying pom-file" pom-file "artifact" artifact)
      (deps-deploy/deploy {:installer :remote,
                           :sign-releases true,
                           :artifact artifact,
                           :pom-file pom-file}))
    opts))
