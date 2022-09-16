(ns cyberme.router
  (:require
    #?@(:clj  [[cyberme.middleware :as middleware]
               [cyberme.routes.home :as home]]
        :cljs [[spec-tools.data-spec :as ds]
               [re-frame.core :as rf]
               [cyberme.pages :as core]
               [cyberme.about :as about]
               [cyberme.file-share :as file-share]
               [cyberme.util.storage :as storage]])
    [clojure.string :as str]))

(defn parse-params
  []
  #?(:cljs
     (let [param-strs (-> (.-location js/window) (str/split #"\?") last (str/split #"\&"))]
       (into {} (for [[k v] (map #(str/split % #"=") param-strs)]
                  [(keyword k) v])))))

(defn share-router []
  ["" #?(:clj {:middleware [middleware/wrap-csrf
                            middleware/wrap-formats]
               :get        home/home-page})
   ["/"
    (merge {:name :dashboard}
           #?(:cljs {:view        #'core/dashboard-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:user/fetch-from-local])
                                             (rf/dispatch [:dashboard/recent])
                                             (rf/dispatch [:dashboard/day-work])
                                             (rf/dispatch [:dashboard/plant-week]))}]}))]

   ["/properties"
    (merge {:name :properties}
           #?(:cljs {:view        #'core/properties-page
                     :controllers [{:parameters {:query [:status :location :labels]}
                                    :start      (fn [{query :query}]
                                                  (if (or (nil? query) (empty? query))
                                                    (let [data-db (storage/get-item "good-filter")]
                                                      (rf/dispatch [:set-filter data-db])
                                                      (reitit.frontend.easy/replace-state :properties nil data-db))
                                                    (rf/dispatch [:set-filter query]))
                                                  (rf/dispatch [:user/fetch-from-local])
                                                  (rf/dispatch [:place/fetch])
                                                  (rf/dispatch [:recent/fetch]))}]}))]

   #_["/clothes"
      (merge {:name :clothes}
             #?(:cljs {:view        #'core/clothes-page
                       :controllers [{:parameters {:query [:status :location :labels]}
                                      :start      (fn [{query :query}]
                                                    (if (or (nil? query) (empty? query))
                                                      (let [data-db (storage/get-item "good_filter")]
                                                        (rf/dispatch [:set-filter data-db])
                                                        (reitit.frontend.easy/replace-state :properties nil data-db))
                                                      (rf/dispatch [:set-filter query]))
                                                    (rf/dispatch [:user/fetch-from-local])
                                                    (rf/dispatch [:place/fetch])
                                                    (rf/dispatch [:recent/fetch]))}]}))]

   ["/work"
    (merge {:name :work}
           #?(:cljs {:view        #'core/hcm-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:user/fetch-from-local])
                                             (rf/dispatch [:hcm/month])
                                             (rf/dispatch [:hcm/todo]))}]}))]

   ["/work-at-inspur"
    (merge {:name :work-all}
           #?(:cljs {:view        #'core/hcm-all-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:user/fetch-from-local])
                                             (rf/dispatch [:hcm/all])
                                             (rf/dispatch [:hcm/hint]))}]}))]

   ["/diary"
    (merge {:name :diary}
           #?(:cljs {:view #'core/diary-page
                     :controllers
                     [{:parameters {:query file-share/diary-key}
                       :start
                       (fn [{query :query}]
                         ;加载本地存储保存的凭证
                         (rf/dispatch [:user/fetch-from-local])
                         ;删除上一次处理的日记数据
                         (rf/dispatch [:diary/current-data-clean])
                         ;按照 query 获取指定范围的日记，如果解析出错，使用 1 - 10 范围
                         (let [[from to] (if (and (:from query) (:to query))
                                           (let [from (js/parseInt (:from query))
                                                 from (if (< from 1) 1 from)
                                                 to (js/parseInt (:to query))
                                                 to (if (<= to from) (+ from 10) to)]
                                             [from to]) [1 10])
                               all-obj (merge query {:from from :to to})]
                           (rf/dispatch [:diary/search-obj-reset! all-obj])
                           (rf/dispatch [:diary/list all-obj]))
                         ;FOR WEEK-PLAN 直接访问日记时，不显示周计划
                         #_(rf/dispatch [:dashboard/plant-week]))}]}))]

   ["/diary-new"
    (merge {:name :diary-new}
           #?(:cljs {:view        #'core/diary-new-page
                     :controllers [{:parameters {:query []}
                                    :start      (fn [_]
                                                  (rf/dispatch [:user/fetch-from-local]))}]}))]

   ["/diary/by-id/:id/edit"
    (merge {:name :diary-edit}
           #?(:cljs {:view        #'core/diary-edit-page
                     :controllers [{:parameters {:path [:id]}
                                    :start      (fn [{path :path}]
                                                  (rf/dispatch [:user/fetch-from-local])
                                                  (rf/dispatch [:diary/current-by-id (:id path)]))
                                    :stop       (fn [_]
                                                  (rf/dispatch [:diary/current-data-clean]))}]}))]

   ["/diary/by-id/:id"
    (merge {:name :diary-view}
           #?(:cljs {:view        #'core/diary-view-page
                     :controllers [{:parameters {:path [:id]}
                                    :start      (fn [{path :path}]
                                                  (rf/dispatch [:user/fetch-from-local])
                                                  (rf/dispatch [:diary/current-by-id (:id path)]))
                                    :stop       (fn [_]
                                                  (rf/dispatch [:diary/current-data-clean]))}]}))]

   ["/diary/by-date/:date/edit"
    (merge {:name :diary-edit-by-date}
           #?(:cljs {:view        #'core/diary-edit-page
                     :controllers [{:parameters {:path [:date]}
                                    :start      (fn [{path :path}]
                                                  (rf/dispatch [:user/fetch-from-local])
                                                  (rf/dispatch [:diary/current-by-date (:date path)]))
                                    :stop       (fn [_]
                                                  (rf/dispatch [:diary/current-data-clean]))}]}))]

   ["/goods"
    (merge {:name :goods}
           #?(:cljs {:view        #'core/goods-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:user/fetch-from-local]))}]}))]

   ["/package"
    (merge {:name :package}
           #?(:cljs {:view        #'core/package-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:user/fetch-from-local]))}]}))]

   ["/about"
    (merge {:name :about}
           #?(:cljs {:view        #'about/about-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:user/fetch-from-local])
                                             (rf/dispatch [:fetch-usage])
                                             (rf/dispatch [:fetch-wishlist]))}]}))]

   ["/demo"
    (merge {:name :demo}
           #?(:cljs {:view        #'core/demo-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:user/fetch-from-local]))}]}))]

   ["/psych-exp"
    (merge {:name :psych-exp}
           #?(:cljs {:view        #'core/psy-exp-page
                     :controllers [{:parameters {:query [:debug]}
                                    :start      (fn [{{debug :debug} :query}]
                                                  (if (= "true" debug)
                                                    (reset! cyberme.psych.exp1.main/is-debug true))
                                                  (rf/dispatch [:clean-all-answer])
                                                  (when-let [params (parse-params)]
                                                    (println params)
                                                    (rf/dispatch [:save-answer ["标记数据" params]]))
                                                  (rf/dispatch [:user/fetch-from-local]))}]}))]

   ["/cook"
    (merge {:name :cook}
           #?(:cljs {:view        #'core/cook-page
                     :controllers [{:start (fn [_]
                                             (rf/dispatch [:user/fetch-from-local]))}]}))]

   ["/file"
    (merge {:name :file}
           #?(:cljs {:view        #'core/file-page
                     :controllers [{:parameters {:query file-share/file-key}
                                    :start      (fn [{query :query}]
                                                  (rf/dispatch [:user/fetch-from-local])
                                                  (if (:q query)
                                                    (do
                                                      ;search-obj 用来指导页面展示数据
                                                      ;如果第一次加载，没有 search-obj 这里构造
                                                      ;如果由页面搜索触发，选择选项的时候已经有了 search-obj，这里什么也不做
                                                      ;如果由搜索栏触发，这里有 clean 字段，等同于第一次加载，清空并重新构造 search-obj
                                                      (rf/dispatch [:file/reset-search-obj-if-outdated! query])
                                                      (rf/dispatch [:file/search query]))
                                                    (rf/dispatch [:file/search-clean])))}]}))]])