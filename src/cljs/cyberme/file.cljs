(ns cyberme.file
  "统一前端搜索页面：书籍、私有云、公有云和磁盘文件

  搜索关键词 q ----> 搜索类型（书籍、磁盘..）---> 搜索选项（大小排序、特定磁盘） ---> 更新 URL Query Param ---> 搜索
  Enter 触发    -----  使用默认值     --------   不同搜索类型选项不同   -------  |                            |
                      下拉框选择 --------------------- 使用默认值- ----------- | 在 cljc 中触发搜索 -> 分派请求 |
                                                    下拉框选择 ------------- |                            |
  "
  (:require [clojure.string :as str]
            [cyberme.util.request :refer [ajax-flow]]
            [cyberme.util.upload :as upload]
            [cyberme.file-share :refer [file-query-range]]
            [goog.string :as gstring]
            [reagent.core :as r]
            [re-frame.core :as rf]))

(def basic-book-cloud-path
  "https://mvst-my.sharepoint.cn/personal/corkine_one_mazhangjing_com/_layouts/15/onedrive.aspx?id=%2Fpersonal%2Fcorkine%5Fone%5Fmazhangjing%5Fcom%2FDocuments%2Fcalibre")

(def basic-book-cloud-download-path
  "https://mvst-my.sharepoint.cn/personal/corkine_one_mazhangjing_com/Documents/calibre")

(def book-dou-ban-path
  "https://search.douban.com/book/subject_search?search_text=")

(def basic-cloud-search-url
  "https://mvst-my.sharepoint.cn/personal/corkine_one_mazhangjing_com/_layouts/15/onedrive.aspx?q=%s&view=7")

(def basic-share-search-url
  "https://share.mazhangjing.com/zh-CN/")

(def file-cn->k {"书籍"     :book
                 "磁盘"     :disk
                 "短链接"   :short
                 "人人影视" :yyets
                 "私有云"   :onedrive-cn
                 "公有云"   :onedrive})

(def all-kind (keys file-cn->k))

(def file-k->cn (apply assoc {} (flatten (mapv (fn [[k v]] [v k]) file-cn->k))))

(def default-search-type "书籍")

(def default-all-disk "所有磁盘")

(rf/reg-event-db
  :file/trigger-url-search!
  (fn [db _]
    (let [search-hint-first
          (apply assoc {} (flatten (mapv (fn [[k v]] [k (first v)])
                                         @(rf/subscribe [:file/search-hint]))))
          real-search-obj
          (apply assoc {}
                 (flatten (filter (fn [[_ v]] (not (nil? v)))
                                  (merge search-hint-first
                                         @(rf/subscribe [:file/search-obj])))))]
      (reitit.frontend.easy/replace-state :file nil real-search-obj)
      (rf/dispatch [:file/search-clean])                    ;搜索时残留上一次结果，这里清除，后期可以加上 loading 状态
      db)))

(rf/reg-event-db
  :file/search-obj-set!
  (fn [db [_ [key value]]]
    (if (:file/search-obj db)
      (assoc-in db [:file/search-obj key] value)
      (assoc db :file/search-obj {key value}))))

(rf/reg-event-db
  :file/reset-search-obj-if-outdated!
  (fn [db [_ obj]]
    ;来自搜索栏搜索 or 来自 URL 直接访问
    (when (or (:clean obj) (empty? (:file/search-obj db)))
      (assoc db :file/search-obj obj))))

(rf/reg-event-db
  :file/drop-search-obj!
  (fn [db _]
    (if-let [obj-q (get (:file/search-obj db) :q)]
      (-> (dissoc db :file/search-obj)
          (assoc-in [:file/search-obj :q] obj-q))
      (dissoc db :file/search-obj))))

(rf/reg-sub
  :file/search-obj
  (fn [db _]
    (get db :file/search-obj {})))

(rf/reg-sub
  :file/search-hint
  (fn [db _]
    (let [type (get file-cn->k (or (-> db :file/search-obj :type) default-search-type))]
      {:kind    (if (= type :disk) (:kind file-query-range) [])
       :size    (if (= type :disk) (:size file-query-range) [])
       :sort    (if (or (= type :disk) (= type :book)) (:sort file-query-range) [])
       :range-x (if (or (= type :disk)) (:range-x file-query-range) [])
       :range-y (if (or (= type :disk))
                  (let [disk-res (get-in db [:disk/search-data :data] [])
                        all-disk (set (filter (comp not nil?)
                                              (map #(-> % :info :disk) disk-res)))]
                    (if (empty? all-disk) [default-all-disk] (into [default-all-disk] (vec all-disk))))
                  [])})))

;;;;;;;;;;;;;;;;; real search action ;;;;;;;;;;;;;;;;;
;统一搜索
(rf/reg-event-db
  :file/search
  (fn [db [_ {:keys [type q] :as search-obj}]]
    (when q
      (rf/dispatch (case (get file-cn->k type :book)
                     :book [:book/search search-obj]
                     :disk [:disk/search search-obj]
                     :yyets [:yyets/search search-obj]
                     :short [:short/search search-obj]
                     :onedrive-cn [:file/open-url [basic-cloud-search-url q]]
                     :onedrive [:file/open-url [basic-share-search-url nil]]
                     [:book/search search-obj])))
    db))

;书籍搜索
(ajax-flow {:call           :book/search
            :uri-fn         #(str "/cyber/books/search?q=" (:q %) "&sort=" (:sort %))
            :data           :book/search-data
            :clean          :book/search-clean
            :failure-notice true})

;文件搜索
(ajax-flow {:call           :disk/search
            :uri-fn         #(str "/cyber/disks/search?q=" (:q %) "&sort=" (:sort %)
                                  "&kind=" (:kind %) "&size=" (:size %)
                                  "&range-x=" (:range-x %)
                                  "&range-y=" (if (= default-all-disk (:range-y %)) nil (:range-y %)))
            :data           :disk/search-data
            :clean          :disk/search-clean
            :failure-notice true})

;人人影视搜索
(ajax-flow {:call           :yyets/search
            :is-post        true
            :uri-fn         #(str "/cyber/movie/yyets/search/" (:q %))
            :data           :yyets/search-data
            :clean          :yyets/search-clean
            :failure-notice true})

;短链搜索
(ajax-flow {:call           :short/search
            :uri-fn         #(str "/cyber/short/search/" (:q %))
            :data           :short/search-data
            :clean          :short/search-clean
            :failure-notice true})

(rf/reg-event-db
  :file/open-url
  (fn [db [_ [basic-url query]]]
    (.open js/window (if query (gstring/format basic-url query) basic-url) "_blank")
    db))

(rf/reg-event-db
  :file/search-clean
  (fn [db _]
    (rf/dispatch [:disk/search-clean])
    (rf/dispatch [:book/search-clean])
    (rf/dispatch [:short/search-clean])
    (rf/dispatch [:yyets/search-clean])
    db))

(defn file-main []
  (let [params (-> (js/URL. (.-location js/window)) (.-searchParams))
        search-in-bar (or (.get params "q") "")
        _ (when-let [ele (.getElementById js/document "search-input")]
            (set! (.-value ele) search-in-bar))]
    [:div#search-div
     [:div.hero.is-full-height.is-info
      [:div.hero-body.has-text-centered
       [:div.title.is-family-code.is-unselectable
        [:span.ml-6.mr-0 "Marvin Library"]
        [:div.file.is-info {:style {:display "inline-block"}
                            :title "点击上传本地 Calibre 元数据文件（叠加数据）"}
         [:label.file-label {:style {:width "1em" :height "1em"}}
          [:input.file-input
           {:type "file" :name "file"
            :on-change
            (fn [e] (let [file-name (.. e -target -value)
                          simple-file-name (last (str/split file-name "\\"))
                          file (aget (.. e -target -files) 0)]
                      (upload/upload-db-file simple-file-name file false
                                             #(rf/dispatch [:global/notice
                                                            {:message (:message %)}]))))}]
          [:span.file-cta
           [:span.file-icon
            [:i.fa.fa-upload]]]]]
        [:div.file.is-info {:style {:display "inline-block"}
                            :title "点击上传本地 Calibre 元数据文件（清空数据库）"}
         [:label.file-label {:style {:width "1em" :height "1em"}}
          [:input.file-input
           {:type "file" :name "file"
            :on-change
            (fn [e] (let [file-name (.. e -target -value)
                          simple-file-name (last (str/split file-name "\\"))
                          file (aget (.. e -target -files) 0)]
                      (upload/upload-db-file simple-file-name file true
                                             #(rf/dispatch [:global/notice
                                                            {:message (:message %)}]))))}]
          [:span.file-cta
           [:span.file-icon
            [:i.fa.fa-upload]]]]]]
       [:div.control.has-icons-right.container
        [:input#search-input.input.is-rounded
         {:type      "text" :placeholder "键入内容，回车搜索"
          :defaultValue search-in-bar
          :on-key-up (fn [e]
                       (let [search-input (.-value (.-target e))]
                         ;按下 Enter 键，有输入内容且和当前数据不同，则执行搜索
                         (when (and (= 13 (.-keyCode e))
                                    search-input
                                    (not= search-in-bar search-input))
                           (rf/dispatch [:file/search-obj-set! [:q search-input]])
                           (rf/dispatch [:file/trigger-url-search!]))))}]
        [:span.icon.is-small.is-right
         [:i.fa.fa-search.mr-3]]
        (let [hint @(rf/subscribe [:file/search-hint])
              search-obj-now @(rf/subscribe [:file/search-obj])
              basic-style {:style {:background-color "#3298dc" :color "white"
                                   :border-radius    "20px" :box-shadow "none"}}]
          [:div {:style {:text-align "center" :margin-top "30px"}}
           [:div.select.is-light.is-small.mr-1.mt-1
            [:select (merge basic-style
                            {:on-change
                             #(do (rf/dispatch [:file/drop-search-obj!])
                                  (rf/dispatch [:file/search-obj-set!
                                                [:type (.. % -target -value)]])
                                  (rf/dispatch [:file/trigger-url-search!]))
                             :value (or (:type search-obj-now) (first all-kind))})
             (for [type all-kind] ^{:key type} [:option type])]]
           (for [search-opinion [:range-y :size :range-x :kind :sort]]
             ^{:key (str (random-uuid) search-opinion)}
             [:<>
              (if (not-empty (get hint search-opinion))
                [:div.select.is-white.is-small.mr-1.mt-1
                 [:select (merge basic-style
                                 {:on-change
                                  #(do (rf/dispatch [:file/search-obj-set!
                                                     [search-opinion (.. % -target -value)]])
                                       (rf/dispatch [:file/trigger-url-search!]))
                                  :default-value (get search-obj-now search-opinion)})
                  (for [type (get hint search-opinion)] ^{:key type} [:option type])]])])])]]]
     [:div.container.mt-3.mb-5
      (let [{search-type :type} @(rf/subscribe [:file/search-obj])
            search-type (get file-cn->k search-type :book)
            {:keys [data]} @(rf/subscribe [(case search-type
                                             :short :short/search-data
                                             :yyets :yyets/search-data
                                             :disk :disk/search-data
                                             :book/search-data)])]
        (if (empty? data)
          [:div.ml-2.pt-3 (if (nil? data) "" "没有相关的搜索结果 (；′⌒`)。")]
          [:<>
           (case search-type
             :yyets
             (for [{:keys [resource_id cnname enname aliasname]} data]
               ^{:key resource_id}
               [:<>
                [:div.box {:style {:box-shadow "0 .5em 1em -.125em rgba(10,10,10,.05),0 0 0 1px rgba(10,10,10,.01)"
                                   :margin     "5px 0 5px 0"}}
                 [:div {:style {:float "right" :margin "1em 1em 0 0"}}
                  [:i.fa.fa-film.mr-2 {:aria-hidden "true"}]]
                 [:p.ml-1
                  [:span.tag.is-light.mr-2 resource_id]
                  [:span.mr-1.is-clickable
                   {:on-click #(rf/dispatch [:common/navigate! :yyets-resource {:id resource_id} nil])}
                   cnname]]
                 (cond (and (not (str/blank? enname))
                            (not (str/blank? aliasname)))
                       [:p.mt-1.ml-1.is-size-7.has-text-grey
                        [:span aliasname]
                        [:span " - "]
                        [:span enname]]
                       (str/blank? enname)
                       [:p.mt-1.ml-1.is-size-7.has-text-grey
                        [:span aliasname]]
                       (str/blank? aliasname)
                       [:p.mt-1.ml-1.is-size-7.has-text-grey
                        [:span enname]])]])
             :short
             (for [{:keys [keyword redirectURL note updateTime id]} data]
               ^{:key (str keyword id)}
               [:<>
                [:div.box {:style {:box-shadow "0 .5em 1em -.125em rgba(10,10,10,.05),0 0 0 1px rgba(10,10,10,.01)"
                                   :margin     "5px 0 5px 0"}}
                 [:div {:style {:float "right" :margin "1em 1em 0 0"}}
                  [:i.fa.fa-sticky-note-o.mr-2 {:aria-hidden "true"}]]
                 [:p.ml-1
                  [:span.tag.is-info.is-light.mr-2 id]
                  [:span.mr-1.is-clickable
                   {:title    (str "点击查看日志\n" "记录添加时间：" updateTime)
                    :on-click #(.open js/window (str "https://go.mazhangjing.com/logsId/" id) "_black")}
                   keyword]]
                 [:p.mt-1.ml-1.is-size-7.has-text-grey
                  [:a {:title redirectURL
                       :href  redirectURL}
                   (if (> (count redirectURL) 120)
                     (str (.substr redirectURL 0 120) "...") redirectURL)]]
                 [:p.mt-0.ml-1.is-size-7.has-text-grey
                  [:span note]]]])
             :disk
             (for [{:keys [path name size create_at info]} data]
               ^{:key path}
               [:<>
                (let [{:keys [disk type last-modified]} info]
                  [:div.box {:style {:box-shadow "0 .5em 1em -.125em rgba(10,10,10,.05),0 0 0 1px rgba(10,10,10,.01)"
                                     :margin     "5px 0 5px 0"}}
                   [:div {:style {:float "right" :margin "1em 1em 0 0"}}
                    (case type
                      "FOLDER" [:i.fa.fa-folder-open-o.mr-2 {:aria-hidden "true"}]
                      "FILE" [:i.fa.fa-file-o.mr-2 {:aria-hidden "true"}])]
                   [:p.ml-1
                    [:span.tag.is-info.is-light.mr-2 disk]
                    (let [last-mod (try
                                     (let [date (js/Date. last-modified)]
                                       (gstring/format "%04d-%02d-%02d %02d:%02d:%02d"
                                                       (.getFullYear date) (+ (.getMonth date) 1) (.getDate date)
                                                       (.getHours date) (.getMinutes date) (.getSeconds date)))
                                     (catch js/Error _ "未知日期"))]
                      [:span.mr-1.is-clickable
                       {:title (str "文件系统记录最后修改：" last-mod
                                    "\n" "数据库更新日期：" create_at)} name])]
                   [:p.mt-1.ml-1.is-size-7.has-text-grey
                    [:span path]
                    [:span " - " (if (< size 100000)
                                   (gstring/format "%.2f KB" (/ size 1024))
                                   (gstring/format "%.2f MB" (/ size 1048576)))]]])])
             (for [{:keys [uuid title author info]} data]
               ^{:key uuid}
               [:div.box {:style {:box-shadow "0 .5em 1em -.125em rgba(10,10,10,.05),0 0 0 1px rgba(10,10,10,.01)"
                                  :margin     "5px 0 5px 0"}}
                (when (and (= "PDF" (:format info)) (:resource info))
                  [:div {:style {:float "right" :margin "1em 1em 0 0"}}
                   [:a {:href   (str basic-book-cloud-download-path "/" (get info :path "")
                                     "/" (get info :resource "") "."
                                     (str/lower-case (get info :format "PDF")))
                        :target :_black
                        :title  (str "点击在线预览 PDF 文件\n大小："
                                     (gstring/format "%.2f MB" (/ (js/parseInt (or (:size info) "0")) 1048576)))}
                    [:i.fa.fa-file-pdf-o]]])
                [:div {:style {:float "right" :margin "1em 1em 0 0"}}
                 [:a {:href   (str basic-book-cloud-path "/" (get info :path ""))
                      :target :_black
                      :title  "点击在 OneDrive 中查看资源"} [:i.fa.fa-cloud]]]
                [:div {:style {:float "right" :margin "1em 1em 0 0"}}
                 [:a {:href   (str book-dou-ban-path title)
                      :target :_black
                      :title  "点击跳转豆瓣图书搜索"} [:i.fa.fa-search]]]
                [:p.ml-1
                 [:span.mr-1.is-clickable title]
                 (let [date (first (str/split (or (-> info :last_modified) "") " "))]
                   [:span.tag.is-white.is-rounded.is-small.is-family-code.has-text-grey.is-clickable
                    {:title (str "Calibre 书库更新日期：" date)} date])]
                [:p.mt-1.ml-1.is-size-6.has-text-grey author]]))]))]]))

