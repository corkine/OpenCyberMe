(ns cyberme.file-share)

;/file 前端 URL 允许出现的查询参数，file.cljs 允许更新到 URL Query Param 的参数
(def file-key
  [:search-kind
   :search-size
   :search-sort
   :search-range-x
   :search-range-y
   :search-type
   :q])

(def file-cn->k {"书籍"   :book
                 "磁盘"   :disk
                 "私有云" :onedrive-cn
                 "公有云" :onedrive})

(def file-k->cn {:book        "书籍"
                 :disk        "磁盘"
                 :onedrive-cn "私有云"
                 :onedrive    "公有云"})

(def file-query-range
  {:sort    ["最早优先" "最晚优先"]
   :kind    ["正则表达式" "简单搜索"]
   :size    ["不限大小" "1MB 以下" "1-10 MB" "10MB 以下" "10-100 MB"
             "1-100 MB" "100MB 以下" "100MB 以上"]
   :range-x ["仅搜索文件名" "搜索完整路径"]})

(def file-query-range-first
  (into {} (for [[k v] file-query-range] [k (first v)])))