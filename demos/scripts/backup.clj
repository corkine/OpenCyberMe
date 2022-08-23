#!/usr/bin/env bb
(ns backup
  "一个简单的命令行交互界面，用于备份指定目录，依赖 rsync，使用 babashka 执行脚本。
  包含三类目录，定义在 config 中，:folder->folder 简单执行目录映射备份，
  :hint-folder->folder 在每个目录备份前要求确认，
  :dir-file->where 对每个目录的每个文件和子目录进行备份计划确认：移动 or 复制 or 跳过，然后执行备份计划
  :update-file 用于更新备份信息到文件中"
  (:require [babashka.deps :as deps]
            [babashka.process :refer [process]]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.time LocalDate LocalDateTime)
           (java.nio.file Files Path)
           (java.io File)))

(def config
  {:folder->folder
   [["/Volumes/Cloud/OneDrive/calibre"
     "/Volumes/新加卷/Calibre"]
    ["/Volumes/Cloud/OneDrive/collect"
     "/Volumes/新加卷/Cloud/collect"]]
   :hint-folder->folder
   [["/Volumes/Data/RepoBackup"
     "/Volumes/新加卷/RepoBackup"]]
   :dir-file->where
   [["/Volumes/Data/Download"
     ["/Volumes/新加卷/Series" "/Volumes/新加卷/Movie"]]]
   :update-file "/Volumes/新加卷/script_update.md"})

(when (= "SCI" (-> *clojure-version* :qualifier))
  (deps/add-deps
    '{:deps {clojure-term-colors/clojure-term-colors {:mvn/version "0.1.0"}}}))

(require '[clojure.term.colors :as c])

(def cache (atom {}))

(defn wait-for
  "提示 hint 并等待按键 key，如果是目标按键，返回 true 并退出
   如果是 exit 则退出，返回 false，反之则重试"
  ([hint key]
   (print hint)
   (flush)
   (let [input (read-line)]
     (if (not (str/blank? input))
       (let [KEY (str/upper-case key)
             INPUT (str/upper-case (str/trim input))]
         (cond (= KEY INPUT) true
               (or (= "EXIT" INPUT) (= "N" INPUT)) false
               :else (do
                       (println (c/red "not valid input, try again"))
                       (recur))))
       (do
         (println (c/red "not valid input, try again"))
         (recur)))))
  ([hint key stop-key]
   (print hint)
   (flush)
   (let [input (read-line)]
     (if (not (str/blank? input))
       (let [KEY (str/upper-case key)
             INPUT (str/upper-case (str/trim input))]
         (cond (= KEY INPUT) true
               (or (= (str/upper-case stop-key) INPUT)) false
               :else (do
                       (println (c/red "not valid input, try again"))
                       (recur))))
       (do
         (println (c/red "not valid input, try again"))
         (recur)))))
  ([hint ignore ignore ignore range]
   (print hint)
   (flush)
   (let [input (read-line)]
     (if (not (str/blank? input))
       (let [RANGE (set (mapv (comp str str/upper-case) range))
             INPUT (str/upper-case (str/trim input))]
         (cond (contains? RANGE INPUT) INPUT
               (or (= "EXIT" INPUT) (= "N" INPUT)) false
               :else (do
                       (println (c/red "not valid input, try again"))
                       (recur))))
       (do
         (println (c/red "not valid input, try again"))
         (recur)))))
  ([hint ignore ignore mapper]
   (print hint)
   (flush)
   (let [input (read-line)]
     (if (not (str/blank? input))
       (let [INPUT (str/upper-case (str/trim input))]
         (cond (or (= "EXIT" INPUT) (= "N" INPUT)) nil
               :else (if-let [map-result (mapper input)]
                       map-result
                       (do
                         (println (c/red "not valid input, try again"))
                         (recur)))))
       (do
         (println (c/red "not valid input, try again"))
         (recur))))))

(defn backup-simple-folder! []
  (when (wait-for (str (c/yellow (str "---> will backup file from" "\n"
                                      (str/join "\n" (:folder->folder config)) "\n"
                                      (str/join "\n" (:hint-folder->folder config)) "\n"))
                       (c/on-yellow "press y to continue, n to skip >> "))
                  "y")
    (doseq [[from to] (:folder->folder config)]
      (let [from-dir (if (str/ends-with? from "/") from (str from "/"))]
        (println "Copying from" from " -> " to)
        (-> (process ["mkdir" "-p" to] {:out :inherit})
            (process ["rsync" "-av" from-dir to] {:out :inherit})
            deref)))
    (swap! cache assoc :folder->folder (:folder->folder config))))

(defn backup-before-agree! []
  (doseq [[from to] (:hint-folder->folder config)]
    (if (wait-for (str (c/cyan "---> will backup file from\n" from " -> " to "\n")
                       (c/on-cyan "sync source and press y to continue, n to skip >> "))
                  "y")
      (let [from-dir (if (str/ends-with? from "/") from (str from "/"))]
        (println "Copying from" from " -> " to)
        (-> (process ["mkdir" "-p" to] {:out :inherit})
            (process ["rsync" "-av" from-dir to] {:out :inherit})
            deref)
        (swap! cache assoc :hint-folder->folder
               (conj (or (-> cache deref :hint-folder->folder) [])
                     [from to]))))))

(defn backup-folder-each-item-to-somewhere! []
  (doseq [[from to] (:dir-file->where config)]
    (let [^Path from-dir (Path/of from (into-array [""]))
          ^File from-file (.toFile from-dir)
          dir (str from-file)]
      (when (.isDirectory from-file)
        (let [items (-> (Files/list from-dir)
                        (.iterator) (iterator-seq))
              items (filterv #(not (str/includes? (str (.getFileName %)) ".DS_Store")) items)]
          (if (empty? items)
            (println "empty folder to dispatch: " dir)
            (loop []
              (let [collect (atom [])]
                (doseq [^Path item items]
                  (let [item-file (.toFile item)
                        item-full-name (str item-file)
                        item-name (.getName item-file)
                        item-type (if (.isDirectory item-file) "DIR" "FILE")
                        item-full-name-fix (if (.isDirectory item-file)
                                             (str item-full-name "") ;文件名也复制
                                             item-full-name)
                        item-size (if (.isDirectory item-file)
                                    "-" (format "%.2f MB" (/ (.length item-file) 1048576.0)))]
                    (if-let [{:keys [to-folder delete?]}
                             (wait-for (c/magenta
                                         (format
                                           "for %s %s, size %s, backup to: \n%s\npress n skip, 1-n copy, {1-n}d copy and delete \n>> "
                                           item-type item-name item-size
                                           (str/join "\n"
                                                     (map-indexed
                                                       (fn [index item] (str (+ index 1) " : " item)) to))))
                                       nil nil
                                       (fn [input]
                                         (try
                                           (let [f (Integer/parseInt (.toString ^Character (first input)))
                                                 in-range? (contains? (set (range 1 (+ 1 (count to)))) f)
                                                 delete? (str/includes? input "d")]
                                             (if in-range?
                                               {:to-folder (get to (- f 1)) :delete? delete?}
                                               nil))
                                           (catch Exception e
                                             nil))))]
                      (let []
                        (println (format "will %s %s -> %s" (if delete? "move" "copy") item-name to-folder))
                        (swap! collect conj {:from          item-full-name-fix :to to-folder
                                             :delete-origin delete?
                                             :is-dir?       (.isDirectory item-file)})))))
                (if (wait-for (c/magenta (format "will do this process, continue? \n%s\ntype yyy to continue, nnn to restart select\n>> "
                                                 (str/join "\n" @collect)))
                              "yyy" "nnn")
                  (do
                    (swap! cache assoc :dir-file->where @collect)
                    (doseq [{:keys [from to delete-origin is-dir?]} @collect]
                      (println "handing " from)
                      (if delete-origin
                        (do (-> (process ["mkdir" "-p" to] {:out :inherit})
                                (process ["rsync" "-av" from to] {:out :inherit})
                                deref)
                            (Thread/sleep 1000)
                            @(process ["rm" (if is-dir? "-rf" "-f") from]))
                        (-> (process ["mkdir" "-p" to] {:out :inherit})
                            (process ["rsync" "-av" from to] {:out :inherit})
                            deref))))
                  (recur))))))))))

(defn append-info []
  (format "\n\n===========================\n%s \nBackup log \n===========================\n%s"
          (LocalDateTime/now)
          (let [cache @cache
                c1 (mapv #(let [path (str/split (second %) #"/")
                                last-path (last path)]
                            (if (Character/isUpperCase ^Character (.charAt last-path 0))
                              last-path
                              (str (second (reverse path)) "/" last-path)))
                         (:folder->folder cache))
                c2 (mapv #(let [path (str/split (second %) #"/")
                                last-path (last path)]
                            (if (Character/isUpperCase ^Character (.charAt last-path 0))
                              last-path
                              (str (second (reverse path)) "/" last-path)))
                         (:hint-folder->folder cache))
                c3 (mapv (fn [{:keys [from to delete-origin is-dir?]}]
                           (format "%s[%s] -%s-> %s"
                                   from (if is-dir? "DIR" "FILE")
                                   (if delete-origin "MOVE" "COPY") to))
                         (:dir-file->where cache))]
            (str/join "\n" (flatten (-> (conj c1 c2) (conj c3)))))))

(when (= "SCI" (-> *clojure-version* :qualifier))
  ;备份一般文件夹，一次确认完整同步
  (backup-simple-folder!)
  ;备份数据仓库，备份前需要手动执行同步或确保和 Github 同步
  (backup-before-agree!)
  ;对于特定文件夹的每个文件，进行高级处理：删除源文件？备份到特定目录？
  (backup-folder-each-item-to-somewhere!)
  (let [info (append-info)]
    (spit (:update-file config) info :append true)))