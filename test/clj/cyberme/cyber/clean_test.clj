(ns cyberme.cyber.clean-test
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
    [cyberme.cyber.inspur :as inspur]
    [cyberme.cyber.clean :as clean])
  (:import (java.util UUID)
           (java.time LocalDate LocalDateTime)))

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

(def fnn (fn [wrap-fn] (fn [& _] (wrap-fn))))

(deftest test-clean
  (testing "clean-count"
    (let [year [{:day (LocalDate/now)
                 :info {:MorningBrushTeeth true
                        :NightBrushTeeth true
                        :MorningCleanFace true
                        :NightCleanFace true}}
                {:day (.minusDays (LocalDate/now) 1)
                 :info {:MorningBrushTeeth true
                        :NightBrushTeeth true
                        :MorningCleanFace true
                        :NightCleanFace true}}
                {:day (.minusDays (LocalDate/now) 2)
                 :info {:MorningBrushTeeth true
                        :NightBrushTeeth true
                        :MorningCleanFace true
                        :NightCleanFace true}}]
          count (clean/clean-count year)]
      (is (= count 2))))

  (testing "clean-count"
    (let [year [{:day (LocalDate/now)
                 :info {:MorningBrushTeeth true
                        :NightBrushTeeth true
                        :MorningCleanFace true
                        :NightCleanFace true}}
                {:day (.minusDays (LocalDate/now) 1)
                 :info {:MorningBrushTeeth true
                        :NightBrushTeeth false
                        :MorningCleanFace true
                        :NightCleanFace true}}
                {:day (.minusDays (LocalDate/now) 2)
                 :info {:MorningBrushTeeth true
                        :NightBrushTeeth true
                        :MorningCleanFace true
                        :NightCleanFace true}}]
          count (clean/clean-count year)]
      (is (= count 0))))

  (testing "clean-count"
    (let [year [{:day (LocalDate/now)
                 :info {:MorningBrushTeeth true
                        :NightBrushTeeth false
                        :MorningCleanFace true
                        :NightCleanFace true}}
                {:day (.minusDays (LocalDate/now) 1)
                 :info {:MorningBrushTeeth true
                        :NightBrushTeeth false
                        :MorningCleanFace true
                        :NightCleanFace true}}
                {:day (.minusDays (LocalDate/now) 2)
                 :info {:MorningBrushTeeth true
                        :NightBrushTeeth false
                        :MorningCleanFace true
                        :NightCleanFace true}}]
          count (clean/clean-count year)]
      (is (= count 0)))))
