(ns cyberme.cyber.note_test
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
    [cyberme.db.core :as db]
    [cheshire.core :as json]
    [clojure.string :as str]
    [cyberme.auth :as auth]
    [conman.core :as conman])
  (:import (java.util UUID)
           (java.time LocalDateTime)))

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

(defn decode-prt [response]
  (let [res (m/decode-response-body response)]
    (println res)
    res))

(deftest test-note

  (testing "quick note add"
    (with-redefs [db/insert-note (fn [& _] :done)]
      (let [test-user (edn :test-user)
            test-pass (edn :test-pass)
            response ((app) (request :get "/cyber/note"
                                     {:user    test-user
                                      :secret  test-pass
                                      :quick   true
                                      :content "HELLO WORLD"}))
            body (decode response)
            {:keys [status message]} body
            _ (println body)]
        (is (= status 1))
        (is (str/includes? message "done")))))

  (testing "last note fetch - nil"
    (with-redefs [db/note-last (fn [& _] nil)
                  db/note-by-id (fn [& _] :done)]
      (let [test-user (edn :test-user)
            test-pass (edn :test-pass)
            response ((app) (request :get "/cyber/note"
                                     {:user   test-user
                                      :secret test-pass}))
            {:keys [status message]} (decode-prt response)]
        (is (= status 0)
            (str/includes? message "过期")))))

  (testing "last note fetch - not nil and not expired"
    (with-redefs [db/note-last (fn [& _] {:id        10000
                                          :content   "HELLO"
                                          :info      {:liveSeconds 1000}
                                          :create_at (LocalDateTime/now)})
                  db/note-by-id (fn [& _] :done)]
      (let [test-user (edn :test-user)
            test-pass (edn :test-pass)
            response ((app) (request :get "/cyber/note"
                                     {:user   test-user
                                      :secret test-pass}))
            {:keys [LiveSeconds From Id LastUpdate Content]} (decode-prt response)]
        (is (= Id 10000))
        (is (= Content "HELLO")))))

  (testing "last note fetch - not nil but expired"
    (with-redefs [db/note-last (fn [& _] {:id        10000
                                          :content   "HELLO"
                                          :info      {:liveSeconds 1000}
                                          :create_at (.minusSeconds (LocalDateTime/now)
                                                                    1003)})
                  db/note-by-id (fn [& _] :done)]
      (let [test-user (edn :test-user)
            test-pass (edn :test-pass)
            response ((app) (request :get "/cyber/note"
                                     {:user   test-user
                                      :secret test-pass}))
            {:keys [status message]} (decode-prt response)]
        (is (= status 0)
            (str/includes? message "过期")))))

  (testing "note by id"
    (with-redefs [db/note-last (fn [& _] :done)
                  db/note-by-id (fn [& _] {:id 1001 :content "WORLD"})]
      (let [test-user (edn :test-user)
            test-pass (edn :test-pass)
            response ((app) (request :get "/cyber/note"
                                     {:user   test-user
                                      :secret test-pass
                                      :id     1001}))
            {:keys [LiveSeconds From Id LastUpdate Content]} (decode-prt response)]
        (is (= Id 1001)
            (= Content "WORLD")))))

  (testing "note add by post"
    (with-redefs [db/insert-note (fn [& _] {:update-count 1})]
      (let [test-user (edn :test-user)
            test-pass (edn :test-pass)
            response ((app) (-> (request :post "/cyber/note")
                                (header "authorization"
                                        (str "Basic " (auth/encode-base64
                                                        (str test-user ":" test-pass))))
                                (json-body {:from "Corkine" :content "HELLO"})))
            {:keys [message status data]} (decode-prt response)
            {:keys [content from]} data]
        (is (= status 1))
        (is (str/includes? message "成功"))
        (is (= content "HELLO"))
        (is (= from "Corkine"))))))

