(ns cyberme.cyber.week-plan-test
  (:require
    [clojure.test :refer :all]
    [ring.mock.request :refer :all]
    [cyberme.handler :refer :all]
    [cyberme.config :refer [edn]]
    [mount.core :as mount]
    [clojure.java.io :as io]
    [cyberme.cyber.week-plan :as week]
    [cyberme.db.core :as db]
    [cheshire.core :as json]
    [clojure.string :as str]
    [cyberme.auth :as auth]
    [conman.core :as conman]
    [cyberme.tool :as tool]
    [next.jdbc :as jdbc])
  (:import (java.util UUID)
           (java.time LocalDateTime LocalDate)))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'cyberme.config/env)
    (f)))

(deftest test-handle-week-plan
  (with-redefs [db/someday (fn [&_])
                db/set-someday (fn [&_])
                db/set-someday-info (fn [&_])
                db/delete-day (fn [&_])
                week/get-some-week (fn [_]
                                     {:info {:plan [{:name        "1"
                                                     :id          "111"
                                                     :description "1111"
                                                     :category    "learn"
                                                     :progress    0.3
                                                     :logs        []
                                                     :last-update "xxx"}]}})
                week/set-some-week (fn [_ info])
                week/merge-some-week (fn [_ info])
                week/delete-some-week (fn [_ info])]
    (testing "正常获取本周计划"
      (let [{:keys [message data status]}
            (week/handle-get-week-plan)
            _ (println "data is " data)]
        (is (str/includes? message "成功"))
        (is (= status 1))
        (is (vector? data))))
    (testing "没有本周计划"
      (with-redefs [week/get-some-week
                    (fn [_] nil)]
        (let [{:keys [message data status]} (week/handle-get-week-plan)]
          (is (str/includes? message "成功"))
          (is (= status 1))
          (is (nil? data)))))
    (testing "本周计划获取失败"
      (with-redefs [week/get-some-week
                    (fn [_] (throw (RuntimeException. "ERROR")))]
        (let [{:keys [message data status]} (week/handle-get-week-plan)]
          (is (str/includes? message "失败"))
          (is (= status -1))
          (is (nil? data)))))
    (testing "新建本周计划成功（第一次）"
      (let [d (atom [])]
        (with-redefs [week/get-some-week (fn [_ _] (-> @d first))
                      week/set-some-week (fn [_ _ info] (reset! d info))
                      next.jdbc/transact (fn [_ body-fn _] (body-fn nil))]
          (let [{:keys [message status]} (week/handle-add-week-plan-item
                                           {:name "2" :category "learn"})]
            (is (str/includes? message "成功"))
            (is (= status 1))
            (is (= (mapv #(:name %) (:plan @d)) ["2"]))))))
    (testing "新建本周计划成功（非第一次）"
      (let [d (atom [{:info {:plan [{:name        "1"
                                     :id          "111"
                                     :description "1111"
                                     :category    "learn"
                                     :progress    0.3
                                     :logs        []
                                     :last-update "xxx"}]}}])]
        (with-redefs [week/get-some-week (fn [_ _] (-> @d first))
                      week/set-some-week (fn [_ _ info] (reset! d info))
                      next.jdbc/transact (fn [_ body-fn _] (body-fn nil))]
          (let [{:keys [message status]} (week/handle-add-week-plan-item
                                           {:name "2" :category "learn"})]
            (is (str/includes? message "成功"))
            (is (= status 1))
            (is (= (mapv #(:name %) (:plan @d)) ["1" "2"]))))))))

