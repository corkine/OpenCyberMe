;clojure -Sdeps '{:paths ["".""] :deps {clj-file-zip/clj-file-zip {:mvn/version,""0.1.0""}}}' -M -m auto-backup
(ns auto-backup
  (:gen-class)
  (:require [clojure.string :as str]
            [clj-file-zip.core :as zip]
            [clojure.java.shell :as shell])
  (:import (java.io File)
           (java.time LocalDateTime)
           (java.time.format DateTimeFormatter)))

(def config
  {:cwd "C:/Users/mazhangjing/Desktop"
   :collect #{#_:jar :txt :pdf :doc :docx :ppt :pptx :drawio :jpeg :png :json #_:hprof}
   :without-file-start #{"~$"} ;Office 临时文件
   :collect-folder-starts #{"sync_"}
   :run-command "wsl -e /mnt/c/Users/demoUser/ossutil cp $cwd oss://cmgit/backup/"})

(defn windows-to-wsl
  "将 Windows 路径字符串转换为 WSL 格式路径字符串"
  [path]
  (let [[_ drive] (re-find #"^(\w:)/.*?" path )]
    (str/replace path drive
                 (str "/mnt/"
                      (str/lower-case (.substring drive 0 1))))))

(defn backup-zip-file-name
  "zip 文件命名规则：备份文件夹名-backup-时间.zip
  cwd 当前需要备份处理的文件夹路径，字符串"
  [cwd]
  (let [timestamp (.format (LocalDateTime/now)
                           (DateTimeFormatter/ofPattern "yyyyMMdd-HHmmss"))
        backup-folder (.getName (clojure.java.io/file cwd))
        backup-file (str backup-folder "-backup-" timestamp ".zip")]
    backup-file))

(defn -main []
  (let [collect (set (mapv name (:collect config)))
        with-out-file-start (:without-file-start config)
        collect-folder-starts (:collect-folder-starts config)
        cwd (:cwd config)
        all-files (seq (.listFiles (clojure.java.io/file cwd)))
        collect-files (filterv (fn [^File file]
                                 (or (and (.isFile file)
                                          (let [file-name (.getName file)]
                                            (and (contains? collect (last (str/split file-name #"\.")))
                                                 (not (some #(str/starts-with? file-name %) with-out-file-start)))))
                                     (and (.isDirectory file)
                                          (let [file-name (.getName file)]
                                            (some #(str/starts-with? file-name %) collect-folder-starts)))))
                               all-files)]
    (print "zipping files:")
    (doseq [file collect-files] (print (.getName file) " "))
    (println "\n")
    (let [backup-file (backup-zip-file-name cwd)
          backup-path (str cwd "/" backup-file)
          files (mapv #(.getPath %) collect-files)]
      (println "will zip files to:" backup-path)
      (zip/zip-files files backup-path)
      (println "backup done!\n")
      (if-let [command (:run-command config)]
        (let [real-command
              (str/replace command "$cwd" (str (windows-to-wsl cwd) "/" backup-file))]
          (println "will run command:" real-command)
          (println "oss-util output: " (:out (apply shell/sh (str/split real-command #" "))))
          (println "uploading done!\n")
          (.delete (clojure.java.io/file backup-path))
          (println "deleted zip file done!")
          (System/exit 0))))))