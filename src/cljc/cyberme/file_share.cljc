(ns cyberme.file-share)

;/file 前端 URL 允许出现的查询参数，file.cljs 允许更新到 URL Query Param 的参数
(def file-key
  [:kind :size :sort :range-x :range-y :type :q :take :drop])

(def file-query-range-size
  [["不限大小" [0 2147483647]]
   ["1MB 以下" [0 1048576]]
   ["1MB 以上" [1048576 2147483647]]
   ["1-10 MB" [1048576 10485760]]
   ["10MB 以下" [0 10485760]]
   ["10-100 MB" [10485760 104857600]]
   ["1-100 MB" [1048576 104857600]]
   ["100MB 以下" [0 104857600]]
   ["100MB 以上" [104857600 2147483647]]])

(def file-query-range
  {:sort    ["最晚优先" "最早优先"]
   :kind    ["简单搜索" "正则表达式"]
   :size    (mapv first file-query-range-size)
   :range-x ["仅搜索文件名" "搜索完整路径"]})

(def diary-key
  [:search :origin :tag :from-year :to-year :from-month :to-month :year :month
   :from :to])