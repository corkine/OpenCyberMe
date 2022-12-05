(ns cyberme.media.news
  (:require [org.httpkit.client :as client]
            [org.httpkit.sni-client :as sni-client]
            [hickory.select :refer [select child first-child tag] :as s]
            [hickory.core :as hi]
            [clojure.tools.logging :as log]
            [cuerdas.core :as str]
            [cyberme.db.core :as db]
            [cyberme.cyber.slack :as slack])
  (:import (java.time LocalDate LocalTime)
           (java.time.format DateTimeFormatter)))

(defn parse-and-find
  "解析打喷嚏首页并且找到是否有新数据需要推送，返回 nil 或者 {:title :url}"
  [^LocalDate time]
  (let [req (binding [org.httpkit.client/*default-client* sni-client/default-client]
              (client/request {:url        "http://www.dapenti.com/blog/index.asp"
                               :method     :get
                               :user-agent "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Safari/537.36 Edg/94.0.992.31"
                               :headers    {"Accept"          "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
                                            "Accept-Encoding" "gzip, deflate"
                                            "Accept-Language" "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2"}
                               :as         :byte-array}))
        body (String. (byte-array (:body @req)) "GBK")
        tree (-> body hi/parse hi/as-hickory)]
    (let [now (.format (or time (LocalDate/now)) (DateTimeFormatter/BASIC_ISO_DATE))
          find (first (filter (fn [element]
                                (str/includes? (or (-> element :content first) "")
                                               (str "喷嚏图卦" now)))
                              (->> tree (select (child (s/tag :a))))))]
      (when find
        {:title (-> find :attrs :title)
         :url   (str "http://www.dapenti.com/blog/" (-> find :attrs :href))}))))

(defn save-news-push-result
  "将推送结果保存到数据库"
  [result]
  (db/set-someday-info {:day  (LocalDate/now)
                        :info {:dapenti result}}))

(defn have-push-already
  "今天是否已经有推送，有推送的话，则不继续推送"
  []
  (not (nil? (-> (db/today) :info :dapenti))))

(def at-16 (LocalTime/of 16 0))

(def at-20 (LocalTime/of 20 0))

(defn news-push-routine
  "执行数据库检查，如果今日没有推送，则解析数据，如果有新数据则推送，且更新数据库，反之则返回"
  []
  (try
    (let [now (LocalTime/now)
          is-ok-to-check (and (.isAfter now at-16) (.isBefore now at-20))]
      (when is-ok-to-check
        (if (have-push-already)
          (log/info "[news] already have data in db, skip...")
          (let [res (parse-and-find nil)]
            (if res
              (do (log/info "[news] find and saving to db...")
                  (slack/notify (format "%s <%s| HERE>" (:title res) (:url res)) "SERVER")
                  (save-news-push-result res))
              (log/info "[news] no news find, skip..."))))))
    (catch Exception e
      (log/error (str "[news] call routine filed: " (.getMessage e))))))