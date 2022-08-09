(ns cyberme.cyber.book
  (:require [cyberme.db.core :as db])
  (:import (java.io File)
           (java.nio.file Paths)
           (java.sql DriverManager ResultSet)
           (java.util UUID)))

(defn read-from-file
  "从 Calibre 数据库中读取书籍信息，返回格式（数据均为字符串）
   {:status :message :data {:title :author_sort :path :uuid :last_modified}}"
  [db-file]
  (try
    (if (let [file ^File (-> (Paths/get db-file (into-array [""])) (.toFile))]
          (and (.exists file) (.isFile file)))
      (let [connection (DriverManager/getConnection (str "jdbc:sqlite:" db-file))]
        (try
          (let [statement (.createStatement connection)]
            (.setQueryTimeout statement 10)
            (let [resultset ^ResultSet
                            (.executeQuery statement "select * from books
                        order by last_modified desc")
                  result (mapv #(select-keys % [:title :author_sort :path :uuid :last_modified])
                               (resultset-seq resultset))]
              {:message "读取文件成功" :status 1 :data result}))
          (catch Exception e
            {:message (str "未处理的异常：" (.getMessage e)) :status -1})
          (finally (.close connection))))
      {:message "读取文件失败，不存在此文件" :status -1})
    (catch Exception e
      {:message (str "未处理的异常：" (.getMessage e)) :status -1})))

(defn handle-upload-file
  "处理从前端上传的数据库文件，解析 SQLite 文件并将其同步到 PostgreSQL 中并返回
  传入参数：文件大小，文件名和文件"
  [filename file]
  (try
    (let [{:keys [message data status]} (read-from-file (str file))]
      (if (= status -1)
        {:message message :status -1}
        (let [seq-data (mapv (fn [row]
                               [(get row :uuid (str (UUID/randomUUID)))
                                (get row :title "未知标题")
                                (get row :author_sort "佚名")
                                {:last_modified (:last_modified row) :path (:path row)}])
                             data)]
          {:message (str "上传 " filename " 数据成功！")
           :status  1
           :data    (db/insert-books-batch {:books seq-data})})))
    (catch Exception e
      {:message (str "未处理的异常：" (.getMessage e)) :status -1})))

(defn handle-search
  "搜索书籍名或者作者"
  [search-kind search-text]
  (try
    {:message "搜索成功"
     :data    (case search-kind
                :author (db/find-book-by-author {:search search-text})
                :title (db/find-book-by-title {:search search-text})
                :unify (db/find-book-by-title-author {:search search-text})
                (throw (RuntimeException. "未指定搜索类型")))
     :status  1}
    (catch Exception e
      {:message (str "未处理的异常：" (.getMessage e)) :status -1})))

(comment
  (def connection (DriverManager/getConnection "jdbc:sqlite:D:/test.db"))
  (def statement (.createStatement connection))
  (.setQueryTimeout statement 30)
  (let [resultset ^ResultSet
                  (.executeQuery statement "select * from books order by last_modified desc")]
    (while (.next resultset)
      (println (.getString resultset "title")
               (.getString resultset "author_sort")
               (.getTimestamp resultset "last_modified")
               (.getString resultset "path")))))

(comment
  (db/insert-books-batch {:books [["abcd" "corkine" "book1" {:last_modified "xxx"
                                                             :path2         "xxx/yyy/zzz"}]
                                  ["abcde" "corkine" "book1" {:last_modified "xxx"
                                                              :path          "xxx/yyy/zzz"}]]})
  (db/find-book-by-title {:search "corkine"})
  (db/find-book-by-author {:search "corkine"})
  (db/get-book {:id "001a"}))