(ns cyberme.cyber.track_test
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
    [cyberme.cyber.express :as express]
    [cyberme.tool :as tool])
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

(deftest test-track
  (testing "API route /track/handle-track"
    (with-redefs [track/req-loc (fn [& _] {:status 200
                                           :body   (json/generate-string
                                                     {:result {:formatted_address "A"
                                                               :business          "B"}})})
                  track/save-track-to-db (fn [& _] :DONE)
                  track/req-report (fn [& _] {:body nil})]
      (let [test-user (edn :test-user)
            test-pass (edn :test-pass)
            by-name (str (UUID/randomUUID))
            response ((app) (request :get "/cyber/location"
                                     {:user   test-user
                                      :secret test-pass
                                      :by     by-name
                                      :lo     1.1
                                      :la     1.2}))
            body (decode response)]
        (is (= 200 (:status response)))
        (is (str/includes? (:message body) by-name)))))

  (testing "simple track for exist and not going on"
    (let [a (atom {})]
      (with-redefs [db/find-express (fn [& _]
                                      (swap! a assoc :find-express :done)
                                      {:track "23333"})
                    db/set-express-track (fn [& _] :done)
                    express/request-express-api
                    (fn [& _]
                      {:status 200
                       :body   (json/generate-string
                                 {:status "100" :state  3})})]
        (let [{:keys [message data status]}
              (express/simple-track {:no "123456" :rewriteIfExist false})]
          (is (= (-> @a :find-express) :done))
          (is (str/includes? message "存在"))
          (is (= status 0))
          (is (= data "23333"))))))

  (testing "simple track for exist but still going on"
    (let [a (atom {})]
      (with-redefs [db/find-express (fn [& _]
                                      (swap! a assoc :find-express :done)
                                      {:track "23333"})
                    db/set-express-track (fn [& _] (swap! a assoc :set-track :done))
                    express/request-express-api
                    (fn [& _]
                      (swap! a assoc :request-api :true)
                      {:status 200
                       :body   (json/generate-string
                                 {:status "200" :state 1})})]
        (let [{:keys [message data status]}
              (express/simple-track {:no "123456"})]
          (is (= (-> @a :find-express) :done))
          (is (= (-> @a :request-api) :true))
          (is (= (-> @a :set-track) :done))
          (is (str/includes? message "追踪设置成功"))
          (is (= status 1))))))

  (testing "simple track not exist and set track"
    (let [a (atom {})]
      (with-redefs [db/find-express (fn [& _]
                                      (swap! a assoc :find-express :done)
                                      nil)
                    db/set-express-track (fn [& _] (swap! a assoc :set-track :done))
                    express/request-express-api
                    (fn [& _]
                      (swap! a assoc :request-api :true)
                      {:status 200
                       :body   (json/generate-string
                                 {:status "200" :state 1})})]
        (let [{:keys [message data status]}
              (express/simple-track {:no "123456"})]
          (is (= (-> @a :find-express) :done))
          (is (= (-> @a :request-api) :true))
          (is (= (-> @a :set-track) :done))
          (is (str/includes? message "追踪设置成功"))
          (is (= status 1))))))

  (testing "simple track server error set db and return"
    (let [a (atom {})]
      (with-redefs [db/find-express (fn [& _]
                                      (swap! a assoc :find-express :done)
                                      nil)
                    db/set-express-track (fn [& _] (swap! a assoc :set-track :done))
                    express/request-express-api
                    (fn [& _]
                      (swap! a assoc :request-api :true)
                      {:status 200
                       :body   (json/generate-string
                                 {:status "100" :state 1})})]
        (let [{:keys [message data status]}
              (express/simple-track {:no "123456"})]
          (is (= (-> @a :find-express) :done))
          (is (= (-> @a :request-api) :true))
          (is (= (-> @a :set-track) :done))
          (is (str/includes? message "单号错误"))
          (is (= status 0)))))))

;(testing "not-found route"
;  (let [response ((app) (request :get "/invalid"))]
;    (is (= 404 (:status response)))))
;(testing "services"
;
;  (testing "success"
;    (let [response ((app) (-> (request :post "/api/math/plus")
;                              (json-body {:x 10, :y 6})))]
;      (is (= 200 (:status response)))
;      (is (= {:total 16} (m/decode-response-body response)))))
;
;  (testing "parameter coercion error"
;    (let [response ((app) (-> (request :post "/api/math/plus")
;                              (json-body {:x 10, :y "invalid"})))]
;      (is (= 400 (:status response)))))
;
;  (testing "response coercion error"
;    (let [response ((app) (-> (request :post "/api/math/plus")
;                              (json-body {:x -10, :y 6})))]
;      (is (= 500 (:status response)))))
;
;  (testing "content negotiation"
;    (let [response ((app) (-> (request :post "/api/math/plus")
;                              (body (pr-str {:x 10, :y 6}))
;                              (content-type "application/edn")
;                              (header "accept" "application/transit+json")))]
;      (is (= 200 (:status response)))
;      (is (= {:total 16} (m/decode-response-body response)))))

