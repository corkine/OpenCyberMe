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
  (:import (java.util UUID)
           (java.time LocalDate LocalDateTime)))

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

(def fnn (fn [wrap-fn] (fn [& _] (wrap-fn))))

(deftest test-hcm
  (testing "handle-serve-set-auto success"
    (with-redefs [db/set-auto (fn [&_] "RANDOM")]
      (let [data {:date  "20220101"
                  :start "10:01-10:32"
                  :end   "10:30-11:20"}
            {:keys [message status]} (inspur/handle-serve-set-auto data)]
        (is (str/includes? message "成功"))
        (is (str/includes? message "RANDOM")))))

  (testing "handle-serve-set-auto not right param"
    (with-redefs [db/set-auto (fn [&_] "RANDOM")]
      (let [data {:date  "2022-01-01"
                  :start "10:01-10:32"
                  :end   "10:30-11:20"}
            {:keys [message status]} (inspur/handle-serve-set-auto data)]
        (is (str/includes? message "失败")))))

  (testing "handle-serve-set-auto not right range"
    (with-redefs [db/set-auto (fn [&_] "RANDOM")]
      (let [data {:date  "20220101"
                  :start "10:34-10:32"
                  :end   "10:30-11:20"}
            {:keys [message status]} (inspur/handle-serve-set-auto data)]
        (is (str/includes? message "不合法")))))

  (let [a (atom {})
        set (fn ([p v] (swap! a assoc p v) v)
              ([p v r] (swap! a assoc p v) r)
              ([p v m r] (swap! a assoc p v) (println m) r))
        got (fn [p v] (= (get @a p) v))
        non (fn [p v] (not= (get @a p) v))
        clean! #(reset! a {})]

    (testing "get-hcm-info with right token"
      (with-redefs [inspur/call-hcm (fn [_ _] {:status 200 :body (json/generate-string {})})
                    inspur/notice-expired-async #(set :notice :done)
                    inspur/hcm-info-from-cache (fn [_] nil)
                    inspur/set-hcm-cache (fnn #(set :cache :done))]
        (let [_ (clean!)
              {:keys [message status]}
              (inspur/get-hcm-info {:time (LocalDateTime/now) :token "good token"})]
          (is (= status 1))
          (is (got :cache :done))
          (is (non :notice :done)))))

    (testing "get-hcm-info with invalid token"
      (with-redefs [inspur/call-hcm (fn [_ _] {:status 400 :body nil})
                    inspur/notice-expired-async #(set :notice :done)
                    inspur/hcm-info-from-cache (fn [_] nil)
                    inspur/set-hcm-cache #(set :cache :done "set cache done." :done)]
        (let [_ (clean!)
              {:keys [message status]}
              (inspur/get-hcm-info {:time (LocalDateTime/now) :token "invalid token"})]
          (is (str/includes? message "get-hcm-info failed"))
          (is (= status 0))
          (is (non :cache :done))
          (is (got :notice :done)))))

    (testing "get-hcm-info with default token success"
      (with-redefs [inspur/call-hcm (fn [_ _] {:status 200 :body (json/generate-string {})})
                    inspur/notice-expired-async #(set :notice :set)
                    inspur/hcm-info-from-cache (fn [_] nil)
                    inspur/set-hcm-cache (fnn #(set :cache :done))
                    inspur/fetch-cache (fn [] {:token "123"})]
        (let [_ (clean!)
              {:keys [message status]}
              (inspur/get-hcm-info {:time (LocalDateTime/now)})
              _ (println message status)]
          (is (= status 1))
          (is (got :cache :done))
          (is (non :notice :set)))))

    (testing "get-hcm-info with default token but hcm expired"
      (with-redefs [inspur/call-hcm (fn [_ _] {:status 400 :body (json/generate-string {})})
                    inspur/notice-expired-async #(set :notice :set)
                    inspur/hcm-info-from-cache (fn [_] nil)
                    inspur/set-hcm-cache (fnn #(set :cache :done))
                    inspur/fetch-cache (fn [] {:token "123"})]
        (let [_ (clean!)
              {:keys [message status]}
              (inspur/get-hcm-info {:time (LocalDateTime/now)})]
          (is (= status 0))
          (is (non :cache :done))
          (is (got :notice :set)))))

    (testing "get-hcm-info with default token but token not-find"
      (with-redefs [inspur/call-hcm (fn [_ _] {:status 200 :body (json/generate-string {})})
                    inspur/notice-expired-async #(set :notice :set)
                    inspur/hcm-info-from-cache (fn [_] nil)
                    inspur/set-hcm-cache (fnn #(set :cache :done))
                    inspur/fetch-cache (fn [] {:token nil})]
        (let [_ (clean!)
              {:keys [message status]}
              (inspur/get-hcm-info {:time (LocalDateTime/now)})]
          (is (= status 0))
          (is (non :cache :done))
          (is (got :notice :set)))))))