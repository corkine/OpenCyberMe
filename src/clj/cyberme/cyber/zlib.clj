(ns cyberme.cyber.zlib
  "Zlibrary 支持模块"
  (:require [clojure.java.io]
            [clojure.string :as str]
            [cyberme.db.core :as db])
  (:import (com.monitorjbl.xlsx StreamingReader)
           (com.monitorjbl.xlsx.impl StreamingCell StreamingRow)
           (java.nio.file Files Path Paths)
           (java.time LocalDate)
           (java.time.format DateTimeFormatter)
           (java.util ArrayList)))

(defn handle-search
  "搜索书籍"
  [search-kind search-text use-regex?]
  (try
    {:message "搜索成功"
     :data    (case search-kind
                :title (db/search-zlib-book
                         {:search search-text :regex (true? use-regex?)})
                (throw (RuntimeException. "未指定搜索类型")))
     :status  1}
    (catch Exception e
      {:message (str "未处理的异常：" (.getMessage e)) :status -1})))

(comment
  ;id, file_update, info_update, file_type, file_size,
  ;name, author, publisher, language,
  ;description, publish_year, page, torrent, info
  ; Note: 种子可能为 NULL 字符串
  (db/insert-zlib-batch
    {:books [12001389 "2021/4/13 to LocalDate" "2021/4/21 to LocalDate" "pdf" 8050432
             "焦虑的意义" "罗洛梅" "广西师范大学出版社" "chinese"
             "西方心理学大师经典译丛" 2013 224 "pilimi-zlib-12000000-12039999.torrent" {}]})
  (db/search-zlib-book {:search "corkine" :regex true}))

(def config (atom {}))

(defn import!
  "zlib xlsx importer"
  [file-path]
  (let [workbook (fn [path]
                   (-> (StreamingReader/builder)
                       (.rowCacheSize 100)
                       (.bufferSize 4096)
                       (.open (clojure.java.io/input-stream path))))
        sheets (fn [workbook]
                 (->> workbook (map #(vector (.getSheetName %) %)) (into {})))]
    (reset! config {})
    (with-open [wk (workbook file-path)]
      (let [collect (ArrayList.)
            all-sheets (sheets wk)
            sheet1 (all-sheets "Sheet1")
            pattern (DateTimeFormatter/ofPattern "M/d/yy")
            fuck-num! #(-> (or % "") (str/replace "," "")
                           (str/replace "- 0" "0")
                           (str/replace "NULL" ""))
            year-4! #(if (> (count %) 4) (.substring % 0 4) %)]
        (doseq [row ^StreamingRow (seq sheet1)]
          (when (:break @config) (throw (RuntimeException. "中断工作！")))
          (let [cells (vec (iterator-seq (.cellIterator row)))
                cell->str (fn [idx]
                            (let [cell (get cells idx)]
                              (when (nil? cell) (println "no cell " idx " in row"))
                              (.getStringCellValue ^StreamingCell cell)))
                id-str (cell->str 0)]
            (if (re-matches #"\d+" id-str)
              (try
                (let [id (Long/parseLong (cell->str 0))
                      upload (LocalDate/parse (cell->str 1) pattern)
                      modified (LocalDate/parse (cell->str 2) pattern)
                      file-type (cell->str 3)
                      file-size (Long/parseLong (fuck-num! (cell->str 4)))
                      name (cell->str 5)
                      author (cell->str 6)
                      publisher (cell->str 7)
                      language (cell->str 8)
                      have-intro? (:include-intro? @config)
                      next (if have-intro? 10 9)
                      intro (if have-intro? (cell->str 9) nil)
                      year (let [raw (cell->str next)]
                             (if (str/blank? raw) nil (Integer/parseInt (year-4! raw))))
                      pages (let [raw (cell->str (+ next 1))]
                              (if (str/blank? raw) nil (Integer/parseInt raw)))
                      torrent (let [raw (cell->str (+ next 2))]
                                (if (str/includes? raw "NULL") nil raw))]
                  #_(println id upload modified file-type file-size
                             name author publisher language
                             intro year pages torrent)
                  (when true
                    (.add collect [id upload modified file-type file-size
                                   name author publisher language
                                   intro year pages torrent {}])
                    (when (= (.size collect) 100)
                      (print "⚡ ")
                      (flush)
                      (try
                        (db/insert-zlib-batch {:books collect})
                        (catch Exception e
                          (.printStackTrace e)
                          (doseq [co collect]
                            (println co))
                          (throw e)))
                      (.clear collect)
                      #_(println "upload done!"))))
                (catch Exception e
                  (.printStackTrace e)
                  (println "Error when handling line " id-str)
                  (throw e)))
              (do
                (println "not a valid line, first cell is " (cell->str 0))
                (when (str/includes? (or (cell->str 9) "") "简介")
                  (println "this file contains 简介")
                  (swap! config assoc :include-intro? true))))))
        (when (> (.size collect) 0)
          (println "uploading last batch data now...")
          (db/insert-zlib-batch {:books collect})
          (.clear collect)
          (println "upload done!"))
        (println "done of handle file " file-path)))))

(comment
  (let [paths (iterator-seq
                (.iterator
                  (Files/list
                    (Paths/get "/Users/corkine/Downloads/书籍目录/" (into-array [""])))))]
    (doseq [path ^Path (filterv #(and (str/ends-with? % ".xlsx")
                                      (not (str/starts-with? (last (str/split % #"/")) "~")))
                                (mapv #(.toString %) paths))]
      (println "handling file" path)
      (import! (.toString path)))))