(ns cyberme.cyber.mini4k-test
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
    [cyberme.db.core :as db]
    [cyberme.cyber.track :as track]
    [cyberme.media.mini4k :as mi]
    [cyberme.cyber.slack :as slack]
    [cyberme.db.core :as db])
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

(deftest test-mini4k
  (testing "merge movie data"
    (with-redefs [mi/parse-data (fn [_] #{"S01E01" "S01E02" "S01E03"})
                  mi/call-slack-async (fn [& _] :done)
                  db/update-movie (fn [& _] :done)]
      (let [data {:id 1234
                  :name "TEST"
                  :url "TEST"
                  :info {:series ["S01E01" "S01E02"]}}
            merge (mi/fetch-and-merge data)]
        (is (= merge #{"S01E03"})))))

  (testing "merge movie data - 2"
    (with-redefs [mi/parse-data (fn [_] #{"S01E01" "S01E02" "S01E03"})
                  mi/call-slack-async (fn [& _] :done)
                  db/update-movie (fn [& _] :done)]
      (let [data {:id 1234
                  :name "TEST"
                  :url "TEST"
                  :info {}}
            merge (mi/fetch-and-merge data)]
        (is (= merge #{"S01E01" "S01E02" "S01E03"})))))

  (testing "merge movie data - 2"
    (with-redefs [mi/parse-data (fn [_] #{})
                  mi/call-slack-async (fn [& _] :done)
                  db/update-movie (fn [& _] :done)]
      (let [data {:id 1234
                  :name "TEST"
                  :url "TEST"
                  :info {}}
            merge (mi/fetch-and-merge data)]
        (is (= merge #{}))))))