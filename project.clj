(defproject cyberme "1.0.1-SNAPSHOT"

  :description "个人业务平台验证系统"
  :url "https://mazhangjing.com"

  :dependencies [[ch.qos.logback/logback-classic "1.2.3"]
                 [cheshire "5.10.0"]
                 [com.taoensso/carmine "3.1.0"]
                 [cljs-ajax "0.8.1"]
                 [clojure.java-time "0.3.2"]
                 [com.cognitect/transit-clj "1.0.324"]
                 [com.google.javascript/closure-compiler-unshaded "v20200504" :scope "provided"]
                 [conman "0.9.1"]
                 [etaoin "1.0.39"]
                 [cprop "0.1.17"]
                 [day8.re-frame/http-fx "0.2.2"]
                 [expound "0.8.7"]
                 [funcool/promesa "10.0.594"]
                 [funcool/struct "1.4.0"]
                 [luminus-http-kit "0.2.0"]
                 [luminus-migrations "0.7.1"]
                 [luminus-transit "0.1.2"]
                 [luminus/ring-ttl-session "0.3.3"]
                 [markdown-clj "1.10.5"]
                 [metosin/muuntaja "0.6.7"]
                 [metosin/reitit "0.5.10"]
                 [metosin/ring-swagger-ui "2.2.10"]
                 [metosin/ring-http-response "0.9.1"]
                 [ring-basic-authentication "1.1.1"]
                 [mount "0.1.16"]
                 [nrepl "0.8.3"]
                 [lt.tokenmill/docx-utils "1.0.3"]
                 [clj-pdf "2.5.5"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.773" :scope "provided"]
                 [org.clojure/core.async "1.3.610"]
                 [org.clojure/google-closure-library "0.0-20191016-6ae1f72f" :scope "provided"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.postgresql/postgresql "42.2.18"]
                 [org.webjars.npm/bulma "0.9.1"]
                 [org.webjars.npm/material-icons "0.3.1"]
                 [org.webjars/webjars-locator "0.40"]
                 [re-frame "1.1.2"]
                 [reagent "1.0.0"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.8.2"]
                 [ring/ring-defaults "0.3.2"]
                 [bk/ring-gzip "0.3.0"]
                 [selmer "1.12.31"]
                 [hickory "0.7.1"]
                 [org.xerial/sqlite-jdbc "3.16.1"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [thheller/shadow-cljs "2.11.5" :scope "provided"]
                 [com.aliyun.oss/aliyun-sdk-oss "2.8.3"]
                 [com.monitorjbl/xlsx-streamer "2.1.0"]]

  ;:repositories [["central" "https://maven.aliyun.com/nexus/content/groups/public"]
  ;               ["clojars" "https://mirrors.tuna.tsinghua.edu.cn/clojars/"]]

  :min-lein-version "2.0.0"

  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :test-paths ["test/clj"]
  :resource-paths ["resources" "target/cljsbuild"]
  :target-path "target/%s/"
  :main ^:skip-aot cyberme.core

  :plugins [[lein-shadow "0.4.0"] [lein-cloverage "1.2.3"]]
  :clean-targets ^{:protect false}
  [:target-path "target/cljsbuild"]
  :shadow-cljs
  {:nrepl {:port 7002}
   :fs-watch {:hawk false}
   :builds
   {:app
    {:target     :browser
     :output-dir "target/cljsbuild/public/js"
     :asset-path "/js"
     :modules    {:app {:entries [cyberme.app]}}
     :devtools
     {:watch-dir "resources/public" :preloads [re-frisk.preload]}
     :dev
     {:closure-defines {"re_frame.trace.trace_enabled_QMARK_" true}}}
    :test
    {:target    :node-test
     :output-to "target/test/test.js"
     :autorun   true}}}

  :npm-deps [[xregexp "5.1.0"]
             [echarts "5.3.0"]
             [echarts-liquidfill "3.1.0"]
             [buffer "6.0.3"]
             [react-markdown "8.0.0"]
             [hastscript "7.0.2"]
             [remark-gfm "3.0.1"]
             #_[react-syntax-highlighter "15.4.5"]]
  :npm-dev-deps [[xmlhttprequest "1.8.0"]]

  :profiles
  {:uberjar       {:omit-source    true
                   :prep-tasks     ["compile" ["shadow" "release" "app"]]

                   :aot            :all
                   :uberjar-name   "cyberme.jar"
                   :source-paths   ["env/prod/clj" "env/prod/cljs"]
                   :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev   {:jvm-opts       ["-Dconf=dev-config.edn" "--enable-preview"]
                   :dependencies   [[binaryage/devtools "1.0.2"]
                                    [cider/piggieback "0.5.2"]
                                    [pjstadig/humane-test-output "0.10.0"]
                                    [prone "2020-01-17"]
                                    [re-frisk "1.3.5"]
                                    [ring/ring-devel "1.8.2"]
                                    [ring/ring-mock "0.4.0"]]
                   :plugins        [[com.jakemccrary/lein-test-refresh "0.24.1"]
                                    [jonase/eastwood "0.3.5"]]


                   :source-paths   ["env/dev/clj" "env/dev/cljs" "test/cljs"]
                   :resource-paths ["env/dev/resources"]
                   :repl-options   {:init-ns user
                                    :timeout 120000}
                   :injections     [(require 'pjstadig.humane-test-output)
                                    (pjstadig.humane-test-output/activate!)]}
   :project/test  {:jvm-opts       ["-Dconf=test-config.edn" "--enable-preview"]
                   :resource-paths ["env/test/resources"]}



   :profiles/dev  {}
   :profiles/test {}}

  :test-refresh {;; Specifies a command to run on test
                 ;; failure/success. Short message is passed as the
                 ;; last argument to the command.
                 ;; Defaults to no command.
                 ;:notify-command    ["terminal-notifier" "-title" "Tests" "-message"]

                 ;; set to true to send notifications to growl
                 ;; Defaults to false.
                 :growl             false

                 ;; only growl and use the notify command if there are
                 ;; failures.
                 ;; Defaults to true.
                 :notify-on-success false

                 ;; Stop clojure.test from printing
                 ;; "Testing namespace.being.tested". Very useful on
                 ;; codebases with many test namespaces.
                 ;; Defaults to false.
                 :quiet             true

                 ;; If this is specified then only tests in namespaces
                 ;; that were just reloaded by tools.namespace
                 ;; (namespaces where a change was detected in it or a
                 ;; dependent namespace) are run. This can also be
                 ;; passed as a command line option: lein test-refresh :changes-only.
                 :changes-only      true

                 ;; If specified, binds value to clojure.test/*stack-trace-depth*
                 :stack-trace-depth nil

                 ;; specifiy a custom clojure.test report method
                 ;; Specify the namespace and multimethod that will handle reporting
                 ;; from test-refresh.  The namespace must be available to the project dependencies.
                 ;; Defaults to no custom reporter
                 ;:report            myreport.namespace/my-report

                 ;; If set to a truthy value, then lein test-refresh
                 ;; will only run your tests once. Also supported as a
                 ;; command line option. Reasoning for feature can be
                 ;; found in PR:
                 ;; https://github.com/jakemcc/lein-test-refresh/pull/48
                 ;:run-once          true

                 ;; If given, watch for changes only in the given
                 ;; folders. By default, watches for changes on entire
                 ;; classpath.
                 ;:watch-dirs        ["src" "test"]

                 ;; If given, only refresh code in the given
                 ;; directories. By default every directory on the
                 ;; classpath is refreshed. Value is passed through to clojure.tools.namespace.repl/set-refresh-dirs
                 ;; https://github.com/clojure/tools.namespace/blob/f3f5b29689c2bda53b4977cf97f5588f82c9bd00/src/main/clojure/clojure/tools/namespace/repl.clj#L164
                 ;:refresh-dirs      ["src" "test"]


                 ;; Use this flag to specify your own flag to add to
                 ;; cause test-refresh to focus. Intended to be used
                 ;; to let you specify a shorter flag than the default
                 ;; :test-refresh/focus.
                 :focus-flag        :test-refresh/focus})
