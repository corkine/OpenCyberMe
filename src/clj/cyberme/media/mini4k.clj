(ns cyberme.media.mini4k
  (:require [cyberme.db.core :as db]
            [org.httpkit.client :as client]
            [org.httpkit.sni-client :as sni-client]
            [hickory.core :as hi]
            [hickory.select :refer [select child first-child tag] :as s]
            [clojure.tools.logging :as log]
            [clojure.set :as set]
            [cyberme.cyber.slack :as slack]
            [cyberme.config :refer [edn-in]]
            [clojure.string :as str]))

(defn parse-data
  "根据 URL 解析获得现在更新的集数"
  [url]
  (try
    (let [req (binding [org.httpkit.client/*default-client* sni-client/default-client]
                (client/request {:url        url
                                 :method     :get
                                 :user-agent "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Safari/537.36 Edg/94.0.992.31"
                                 :headers    {"Cookie" "__cf_bm=QLfbwBdATTDQFL5mk6PZ2hlMjJcH5o8S4CsNiq8Rc0M-1632655569-0-AX5Gwf1p8qZYSfnjAOXuPhixrpZUeukw5qJ0ZM2XjTe+hGaRAFprA9ERA1UHcER3XRc0lSABtnOK02KJSfWPyRDxTUYVJpSbgykaspHPj++v2RVDtZ1HRU6MuMgWl6KomA=="}}))
          {:keys [body]} @req
          tree (-> body hi/parse hi/as-hickory)
          data (sort
                 (set (filter (comp not nil?)
                              (mapv #(let [content (:content %)]
                                       (if (and (vector? content) (= (count content) 1) (string? (first content)))
                                         (let [data-str (first content)
                                               [_ find] (re-find #"(S\d\dE\d\d)" data-str)]
                                           find) nil))
                                    (->> tree (select (child (tag :a))))))))]
      (set data))
    (catch Exception e
      (log/info "[mini4k-parse] failed because: " (.getMessage e))
      nil)))

(defn recent-update
  "返回最近更新的影视剧信息"
  [{:keys [day]}]
  (let [data (db/recent-movie-update {:day day})]
    data))

(defn call-slack-async [name need-add-series]
  (future (slack/notify (str "Series " name " Updated: "
                             (str/join " " (sort (vec need-add-series))))
                        "MOVIE")))

(defn fetch-and-merge [{:keys [id name url info]}]
  (try
    (log/debug "[mini4k-check] start checking " name)
    (when (or (nil? url) (nil? name))
      (throw (RuntimeException. "传入的数据不包含 URL 和 NAME")))
    (let [db-series (set (or (:series info) []))
          web-series (parse-data url)
          need-add-series (set/difference web-series db-series)]
      (if (empty? need-add-series)
        (do (log/debug "[mini4k-check] no data need to merge, go on...")
            #{})
        (do (call-slack-async name need-add-series)
            (db/update-movie {:id id :info (merge info {:series web-series})})
            need-add-series)))
    (catch Exception e
      (log/error "[mini4k-check] failed for " name ", " (.getMessage e))
      #{})))

(defn backend-mini4k-routine []
  (while true
    (try
      (let [sleep-sec (or (edn-in [:movie :sleep-seconds]) 1800)]
        (try
          (log/debug "[movie-service] starting sync with server...")
          (doseq [need-check (db/all-movie)]
            (fetch-and-merge need-check))
          (log/debug "[movie-service] end sync with server, try to sleep sec: " sleep-sec)
          (catch Exception e
            (log/info "[movie-service] sync with ms-server failed: " (.getMessage e))))
        (Thread/sleep (* 1000 sleep-sec)))
      (catch Exception e
        (log/info "[movie-service] movie-service routine failed: " (.getMessage e))))))

(defn handle-add-movie [{:keys [name url]}]
  (try
    (let [_ (db/insert-movie {:name name :url url})]
      {:message (str "添加 " name "成功！")
       :status 1})
    (catch Exception e
      {:message (str "添加 " name "失败！" (.getMessage e))
       :status 0})))

(defn handle-list-movie []
  (try
    {:message "获取列表成功！"
     :data (db/all-movie)
     :status 1}
    (catch Exception e
      {:message (str "获取列表失败！" (.getMessage e))  :status 0})))

(defn handle-delete-movie [id-map]
  (try
    {:message (str "删除电影成功，ID：" (:id id-map))
     :data (db/delete-movie id-map)
     :status 1}
    (catch Exception e
      {:message (str "删除电影失败！" (.getMessage e))  :status 0})))

(comment
  (user/start)
  (user/create-migration "movie")
  (user/migrate)
  (cyberme.db.core/bind)
  (db/all-movie)
  (db/delete-movie {:id 2333})
  (db/insert-movie {:name "A"
                    :url  "https://xxx.xx"})
  (db/update-movie {:id   1
                    :info {:A "B"}})
  (db/all-track)
  (def data (parse-data {:id 1 :name "异星灾变" :url "https://www.mini4k.com/shows/305654" :info {}}))
  (def tree (-> data hi/parse hi/as-hickory))
  (sort
    (set (filter (comp not nil?)
                 (mapv #(let [content (:content %)]
                          (if (and (vector? content) (= (count content) 1) (string? (first content)))
                            (let [data-str (first content)
                                  [_ find] (re-find #"(S\d\dE\d\d)" data-str)]
                              find) nil))
                       (->> tree (select (child (tag :a))))))))
  )