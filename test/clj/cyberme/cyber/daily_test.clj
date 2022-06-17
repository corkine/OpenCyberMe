(ns cyberme.cyber.daily_test
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
    [cyberme.cyber.track :as track]
    [cyberme.cyber.diary :as d]
    [cyberme.db.core :as db]
    [cheshire.core :as json]
    [clojure.string :as str]
    [cyberme.auth :as auth]
    [conman.core :as conman]
    [cyberme.tool :as tool])
  (:import (java.util UUID)
           (java.time LocalDateTime LocalDate)))

(defn parse-json [body]
  (m/decode formats/instance "application/json" body))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'cyberme.config/env
                 #'cyberme.handler/app-routes)
    (f)))

(defn prn-body [response]
  (println (slurp (io/reader (:body response)))))

(defn data [response]
  (with-open [r (io/reader (:body response))] (slurp r)))

(defn decode [response]
  (m/decode-response-body response))

(defn decode-prt [response]
  (let [res (m/decode-response-body response)]
    (println res)
    res))

(deftest test-plant-learn-week
  (with-redefs [tool/all-week-day (fn []
                                    [(LocalDate/of 2022 6 13)
                                     (LocalDate/of 2022 6 14)
                                     (LocalDate/of 2022 6 15)
                                     (LocalDate/of 2022 6 16)
                                     (LocalDate/of 2022 6 17)
                                     (LocalDate/of 2022 6 18)
                                     (LocalDate/of 2022 6 19)])]
    (testing "本周没有学习任务，没有浇花（周一）"
      (with-redefs [db/day-range (fn [& _]
                                   [{:day (LocalDate/of 2022 6 13)}])]
        (let [week-info (d/handle-plant-learn-week)
              status (-> week-info :data :status)
              learn (-> week-info :data :learn)]
          (is (= learn :done))
          (is (= status [0 0 0 0 0 0 0])))))

    (testing "本周没有学习、浇过花了（周一）"
      (with-redefs [db/day-range (fn [& _]
                                   [{:day (LocalDate/of 2022 6 13)
                                     :info {:plant :done}}])]
        (let [week-info (d/handle-plant-learn-week)
              status (-> week-info :data :status)
              learn (-> week-info :data :learn)]
          (is (= learn :done))
          (is (= status [1 0 0 0 0 0 0])))))

    (testing "本周没有学习、周一和周二都浇过花了（周二）"
      (with-redefs [db/day-range (fn [& _]
                                   [{:day (LocalDate/of 2022 6 13)
                                     :info {:plant :done}}
                                    {:day (LocalDate/of 2022 6 14)
                                     :info {:plant :done}}])]
        (let [week-info (d/handle-plant-learn-week)
              status (-> week-info :data :status)
              learn (-> week-info :data :learn)]
          (is (= learn :done))
          (is (= status [1 1 0 0 0 0 0])))))

    (testing "本周没有学习、周二浇过花了（周二）"
      (with-redefs [db/day-range (fn [& _]
                                   [{:day (LocalDate/of 2022 6 13)
                                     :info {}}
                                    {:day (LocalDate/of 2022 6 14)
                                     :info {:plant :done}}])]
        (let [week-info (d/handle-plant-learn-week)
              status (-> week-info :data :status)
              learn (-> week-info :data :learn)]
          (is (= learn :done))
          (is (= status [0 1 0 0 0 0 0])))))

    (testing "本周周一下发学习任务，未完成（当前周一）"
      (with-redefs [db/day-range (fn [& _]
                                   [{:day (LocalDate/of 2022 6 13)
                                     :info {:learn-request true}}])]
        (let [week-info (d/handle-plant-learn-week)
              status (-> week-info :data :status)
              learn (-> week-info :data :learn)]
          (is (= learn :not-done)))))

    (testing "本周周一下发学习任务，已完成（当前周一）"
      (with-redefs [db/day-range (fn [& _]
                                   [{:day (LocalDate/of 2022 6 13)
                                     :info {:learn-request true
                                            :learn-done true}}])]
        (let [week-info (d/handle-plant-learn-week)
              status (-> week-info :data :status)
              learn (-> week-info :data :learn)]
          (is (= learn :done)))))

    (testing "本周周一下发学习任务，未完成（当前周二）"
      (with-redefs [db/day-range (fn [& _]
                                   [{:day (LocalDate/of 2022 6 13)
                                     :info {:learn-request true}}
                                    {:day (LocalDate/of 2022 6 14)
                                     :info {}}])]
        (let [week-info (d/handle-plant-learn-week)
              status (-> week-info :data :status)
              learn (-> week-info :data :learn)]
          (is (= learn :not-done)))))

    (testing "本周周一下发学习任务，已完成（当前周二）"
      (with-redefs [db/day-range (fn [& _]
                                   [{:day (LocalDate/of 2022 6 13)
                                     :info {:learn-request true}}
                                    {:day (LocalDate/of 2022 6 14)
                                     :info {:learn-done true}}])]
        (let [week-info (d/handle-plant-learn-week)
              status (-> week-info :data :status)
              learn (-> week-info :data :learn)]
          (is (= learn :done)))))

    (testing "本周周一下发学习任务，周一完成，周三下发学习任务，未完成（当前周三）"
      (with-redefs [db/day-range (fn [& _]
                                   [{:day (LocalDate/of 2022 6 13)
                                     :info {:learn-request true
                                            :learn-done true}}
                                    {:day (LocalDate/of 2022 6 14)
                                     :info {}}
                                    {:day (LocalDate/of 2022 6 15)
                                     :info {:learn-request true}}])]
        (let [week-info (d/handle-plant-learn-week)
              status (-> week-info :data :status)
              learn (-> week-info :data :learn)]
          (is (= learn :not-done)))))

    (testing "本周周一下发学习任务，周一完成，周三下发学习任务，已完成（当前周三）"
      (with-redefs [db/day-range (fn [& _]
                                   [{:day (LocalDate/of 2022 6 13)
                                     :info {:learn-request true
                                            :learn-done true}}
                                    {:day (LocalDate/of 2022 6 14)
                                     :info {}}
                                    {:day (LocalDate/of 2022 6 15)
                                     :info {:learn-request true
                                            :learn-done true}}])]
        (let [week-info (d/handle-plant-learn-week)
              status (-> week-info :data :status)
              learn (-> week-info :data :learn)]
          (is (= learn :done)))))

    (testing "本周周一下发学习任务，周一完成，周三下发学习任务，已完成（当前周四）"
      (with-redefs [db/day-range (fn [& _]
                                   [{:day (LocalDate/of 2022 6 13)
                                     :info {:learn-request true
                                            :learn-done true}}
                                    {:day (LocalDate/of 2022 6 14)
                                     :info {}}
                                    {:day (LocalDate/of 2022 6 15)
                                     :info {:learn-request true}}
                                    {:day (LocalDate/of 2022 6 16)
                                     :info {:learn-done true}}])]
        (let [week-info (d/handle-plant-learn-week)
              status (-> week-info :data :status)
              learn (-> week-info :data :learn)]
          (is (= learn :done)))))))

