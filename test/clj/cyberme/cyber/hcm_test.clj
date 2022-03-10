(ns cyberme.cyber.hcm-test
  (:require
    [clojure.test :refer :all]
    [ring.mock.request :refer :all]
    [cyberme.handler :refer :all]
    [cyberme.middleware.formats :as formats]
    [muuntaja.core :as m]
    [cyberme.config :refer [edn]]
    [mount.core :as mount]
    [clojure.java.io :as io]
    [org.httpkit.client :as client]
    [cyberme.db.core :as db]
    [cheshire.core :as json]
    [clojure.string :as str]
    [cyberme.auth :as auth]
    [conman.core :as conman]
    [cyberme.cyber.track :as track]
    [cyberme.db.core :as db]
    [cyberme.cyber.inspur :as inspur])
  (:import (java.util UUID)))

(defn parse-json [body]
  (m/decode formats/instance "application/json" body))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'cyberme.config/env
                 #'cyberme.handler/app-routes
                 #'cyberme.db.core/*db*)
    (f)))

(defn prn-body [response]
  (println (slurp (io/reader (:body response)))))

(defn data [response]
  (with-open [r (io/reader (:body response))] (slurp r)))

(defn decode [response]
  (m/decode-response-body response))

(deftest test-hcm
  (testing "handle-serve-set-auto success"
    (with-redefs [db/set-auto (fn [&_] "RANDOM")]
      (let [data {:date "20220101"
                  :start "10:01-10:32"
                  :end "10:30-11:20"}
            {:keys [message status]} (inspur/handle-serve-set-auto data)]
        (is (str/includes? message "成功"))
        (is (str/includes? message "RANDOM")))))

  (testing "handle-serve-set-auto not right param"
    (with-redefs [db/set-auto (fn [&_] "RANDOM")]
      (let [data {:date "2022-01-01"
                  :start "10:01-10:32"
                  :end "10:30-11:20"}
            {:keys [message status]} (inspur/handle-serve-set-auto data)]
        (is (str/includes? message "失败")))))

  (testing "handle-serve-set-auto not right range"
    (with-redefs [db/set-auto (fn [&_] "RANDOM")]
      (let [data {:date "20220101"
                  :start "10:34-10:32"
                  :end "10:30-11:20"}
            {:keys [message status]} (inspur/handle-serve-set-auto data)]
        (is (str/includes? message "不合法"))))))
