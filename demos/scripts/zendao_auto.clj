#!/usr/bin/env bb -Sdeps '{:paths ["."] :deps {etaoin/etaoin {:mvn/version,"1.0.39"}}}'
#!/usr/bin/env bb clojure -Sdeps '{:paths ["."] :deps {etaoin/etaoin {:mvn/version,"1.0.39"} http-kit/http-kit {:mvn/version,"2.6.0"}}}' -M
(ns zendao-auto
  "每周一学自动化，需要暴露变量 EDU_INSPUR_PASS，格式为 {username}::{password}
  或提供 config.edn 文件。"
  (:require [org.httpkit.client :as http]
            [etaoin.api :as e]
            [cheshire.core :as json])
  (:import (java.util Base64)))

(def config (merge
              (if-let [pass-env (System/getenv "ZENDAO_INSPUR_PASS")]
                (let [pass (.split (String. (.decode (Base64/getDecoder) pass-env)) "::")]
                  {:user (first pass) :pass (second pass)})
                {:user "xxx@inspur.com"
                 :pass "YOUR_PASS_HERE"})
              {:path-driver (if (= "SCI" (-> *clojure-version* :qualifier))
                              "../resources/chromedriver.exe"
                              "resources/chromedriver.exe")
               :cyberToken  (System/getenv "CYBER_TOKEN")
               :cyberUrl    "https://cyber.mazhangjing.com/cyber/todo/work-today"}))

(defn go-to-log-page [driver]
  (println "try login now...")
  (e/go driver "http://cd.icn.local")
  (e/wait-visible driver {:id :account})
  (e/fill driver {:id :account} (:user config))
  (e/fill driver {:type :password} (:pass config))
  (e/click driver {:id :submit})
  (e/wait-visible driver "//*[@id=\"menuMainNav\"]/li[1]/a/span")
  (println "login success, go to log page...")
  (e/switch-frame driver {:id :appIframe-my})
  (e/click driver {:xpath "/html/body/header/div/div/nav/ul/li[3]/a"})
  (e/wait-visible driver {:class "cell-day current-month current future with-plus-sign"}))

(defn go-to-new-log-page
  "进入新增日志模态框"
  [driver]
  (println "go to new log page...")
  (e/click driver {:class "cell-day current-month current future with-plus-sign"})
  (e/switch-frame driver {:id :iframe-triggerModal})
  (e/wait-visible driver {:id :submit}))

(defn fill-tasks-on-log-page [driver tasks]
  (if (empty? tasks)
    (do (println "no task today, exist now..."))
    (let [contents (e/query-all driver {:id "work[]"})
          objs (e/query-all driver {:id "objectType_chosen"})]
      (doseq [[{:keys [title hour] :as item} index]
              (zipmap tasks [0 1 2 3 4 5 6 7 8])]
        (println "now handling" item)
        (e/click-el driver (nth contents index))
        (e/fill-el driver (nth contents index) title)
        (e/click-el driver (nth objs index))
        (let [suggest-obj-selects (e/query driver {:class "active-result highlighted"})]
          (println "choose task: " (e/get-element-attr-el driver suggest-obj-selects :title))
          (e/click-el driver suggest-obj-selects)
          (e/click driver {:id (format "left[%d]" (+ index 1))})
          (e/fill driver {:id (format "left[%d]" (+ index 1))} "1")
          (let [costs (e/query-all driver {:id "consumed[]"})]
            (e/click-el driver (nth costs index))
            (e/fill-el driver (nth costs index) (str hour))
            (e/wait driver 0.5))))
      (e/click driver {:id "submit"}))))

(defn -main []
  (let [tasks (-> (http/request {:url     (:cyberUrl config)
                                 :headers {"authorization" (:cyberToken config)}})
                  deref
                  :body
                  (json/parse-string true)
                  :data)]
    (if-not (empty? tasks)
      (let [driver (e/chrome (merge {:path-driver (:path-driver config)
                                     :log-level   :warn}
                                    (if-let [chrome (:chrome config)]
                                      {:path-browser chrome}
                                      {})))]
        (go-to-log-page driver)
        (go-to-new-log-page driver)
        (fill-tasks-on-log-page driver tasks)
        (e/close-window driver)
        (System/exit 0))
      (println "no task in Microsoft TODO, exist now..."))))

(if (= "SCI" (-> *clojure-version* :qualifier))
  (-main))

(comment
  (String. (.encode (Base64/getEncoder) (.getBytes "?::?"))))