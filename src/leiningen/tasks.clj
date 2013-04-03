(ns leiningen.tasks
  (:use leiningen.lein-haml-sass.render-engine
        leiningen.lein-haml-sass.options
        [cemerick.pomegranate :only [add-dependencies]]
        leiningen.lein-common.lein-utils))

;; Maybe don't need this (unless I find a better way to get the task functions
(def current-ns *ns*)

(defn- once-doc [src-type]
  (str "Compiles the " (name src-type) " files once"))

(defn- once [options]
  (let [type (name (:src-type options))]
    (println "Compiling" type "files located in" (:src options))
    (render-all! options false true)))


(defn- auto
  "Automatically recompiles when files are modified"
  [options]
  (let [type (name (:src-type options))]
    (println "Ready to compile" type "files located in" (:src options))
    (render-all! options true)))


(defn- clean
  "Removes the autogenerated files"
  [options]
  (let [type (name (:src-type options))]
    (println "Deleting files generated by lein" type)
    (clean-all! options)))


(defn- fn-meta [fn doc]
  (assoc (meta fn) :doc doc))

(defn- enrich-fn [fn doc]
  (with-meta fn (fn-meta fn doc)))

(defn- download-gem-using-gemjars [gem-name gem-version]
  (let [gem-id (symbol (str "org.rubygems/" gem-name))]
    (try
      (add-dependencies :coordinates [[gem-id gem-version]]
                        :repositories (merge cemerick.pomegranate.aether/maven-central
                                             {"gem-jars" "http://gemjars.org/maven/"}))
      (catch Exception e
        (do
          (println (.getMessage e))
          false)))))

(defn- ensure-gem-installed! [project options]
  (let [gem-name (:gem-name options)
        gem-version (:gem-version options)]
    (when gem-version ;; Only try to fetch if there is a gem specified
      (download-gem-using-gemjars gem-name gem-version))))

(defn- ensure-using-lein2 []
  (when-not lein2?
    (exit-failure "You need to use leiningen 2.XX with the version of the plugin.
Refer to the README for further details.")))

(defmacro def-lein-task [task-name]
  (let [type  (name task-name)
        src-type (keyword task-name)
        fname (symbol type)
        doc   (str "Compiles " type " files.")
        once-fn (enrich-fn once (once-doc src-type))
        arg-list ['once 'auto 'clean ]]
    `(defn ~task-name
       ~doc
       {:help-arglists '(~arg-list)
        :subtasks [~once-fn ~#'auto ~#'clean]}
       ([~'project]
          (exit-failure (lhelp/help-for ~type)))

       ([~'project ~'subtask & ~'args]
          (~ensure-using-lein2)
          (if-let [options# (extract-options ~src-type ~'project)]
            (do (#'ensure-gem-installed! ~'project options#)
                (case ~'subtask
                  "once"  (~once  options#)
                  "auto"  (~auto  options#)
                  "clean" (~clean options#)
                  (task-not-found ~'subtask)))
            (exit-failure))))))

(defn- task-fn-for [subtask]
  ;;(ns-resolve current-ns (symbol (name subtask)))
  (case subtask
    :once  once
    :auto  auto
    :clean clean))

(defn standard-hook [src-type subtask]
  (fn [task project & args]
    (let [options (extract-options src-type project)
          task-fn (task-fn-for subtask)]
      (when-not (subtask (:ignore-hooks options))
        (ensure-using-lein2)
        (ensure-gem-installed! project options)
        (apply task (cons project args))
        (task-fn options)))))
