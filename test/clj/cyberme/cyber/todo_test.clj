(ns cyberme.cyber.todo_test
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
    [cyberme.cyber.todo :as todo])
  (:import (java.util UUID)))

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

(deftest test-todo
  (testing "handle list test"
    (let [a (atom {})
          name (str (UUID/randomUUID))]
      (with-redefs [db/to-do-recent-day (fn [{:keys [day]}]
                                          (swap! a assoc :day day)
                                          (mapv (fn [_] {:day day
                                                         :list name})
                                                (range 0 day)))]
        (let [day (+ (rand-int 100) 30)
              {:keys [data status]}
              (todo/handle-list {:day day :listName name})]
          (is (= status 1))
          (is (= (:day @a) day))
          (is (= (count data) day))))))

  (testing "handle list test without list name match"
    (let [a (atom {})
          name (str (UUID/randomUUID))]
      (with-redefs [db/to-do-recent-day (fn [{:keys [day]}]
                                          (swap! a assoc :day day)
                                          (mapv (fn [_] {:day day
                                                         :list (str name "-hello")})
                                                (range 0 day)))]
        (let [day (+ (rand-int 100) 30)
              {:keys [data status]}
              (todo/handle-list {:day day :listName name})]
          (is (= status 1))
          (is (= (:day @a) day))
          (is (= (count data) 0))))))

  (testing "handle list test without listName"
    (let [a (atom {})]
      (with-redefs []
        (let [day (+ (rand-int 100) 30)
              {:keys [message data status]} (todo/handle-list {:day day})]
          (is (= status 0))
          (is (str/includes? message "没有传入列表"))
          (is (= (count data) 0)))))))