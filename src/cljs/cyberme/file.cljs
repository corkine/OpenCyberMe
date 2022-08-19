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
            [cyberme.file-share :refer
             [file-cn->k file-k->cn
              file-query-range]]
            [goog.string :as gstring]
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

;书籍，磁盘，私有云，共享云，正则 or 简单，最早 or 最晚，磁盘 A or 磁盘 B，文件名 or 完整路径
(def all-kind ["书籍" "磁盘" "私有云" "公有云"])

(def default-search-type (first all-kind))

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
      #_(rf/dispatch [:file/search-clean])
      db)))

(rf/reg-event-db
  :file/search-obj-set!
  (fn [db [_ [key value]]]
    (if (:file/search-obj db)
      (assoc-in db [:file/search-obj key] value)
      (assoc db :file/search-obj {key value}))))

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
    (let [type (get file-cn->k (or (-> db :file/search-obj :search-type)
                                   default-search-type))]
      {:search-kind    (if (= type :disk) (:kind file-query-range) [])
       :search-size    (if (= type :disk) (:size file-query-range) [])
       :search-sort    (if (or (= type :disk) (= type :book)) (:sort file-query-range) [])
       :search-range-x (if (or (= type :disk)) (:range-x file-query-range) [])
       :search-range-y (if (or (= type :disk))
                         (let [disk-res (get-in db [:disk/search-data :data] [])
                               all-disk (set (filter (comp not nil?)
                                                     (map #(-> % :info :disk) disk-res)))]
                           (if (empty? all-disk) [] (vec all-disk)))
                         [])})))

;;;;;;;;;;;;;;;;; real search action ;;;;;;;;;;;;;;;;;
;统一搜索
(rf/reg-event-db
  :file/search
  (fn [db [_ {:keys [search-type q] :as search-obj}]]
    (when q
      (rf/dispatch (case (get file-cn->k search-type :book)
                     :book [:book/search search-obj]
                     :disk [:disk/search search-obj]
                     :onedrive-cn [:file/open-url [basic-cloud-search-url q]]
                     :onedrive [:file/open-url [basic-share-search-url nil]]
                     [:book/search search-obj])))
    db))

;书籍搜索
(ajax-flow {:call           :book/search
            :uri-fn         #(str "/cyber/books/search?q=" (:q %) "&sort=" (:search-sort %))
            :data           :book/search-data
            :clean          :book/search-clean
            :failure-notice true})

;文件搜索
(ajax-flow {:call           :disk/search
            :uri-fn         #(str "/cyber/disks/search?q=" (:q %) "&sort=" (:search-sort %)
                                  "&kind=" (:search-kind %) "&size=" (:search-size %)
                                  "&range-x=" (:search-range-x %)
                                  "&range-y=" (:search-range-y %))
            :data           :disk/search-data
            :clean          :disk/search-clean
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
    db))

(defn file-main []
  (let [params (-> (js/URL. (.-location js/window)) (.-searchParams))
        ;type (js/parseInt (or (.get params "type") "0"))
        search-in-bar (or (.get params "q") "")]
    [:div
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
        [:input.input.is-rounded
         {:type         "text" :placeholder "键入内容，回车搜索"
          :defaultValue search-in-bar
          :on-key-up    (fn [e]
                          (if (= 13 (.-keyCode e))
                            (when-let [search (.-value (.-target e))]
                              (rf/dispatch [:file/search-obj-set! [:q search]])
                              (rf/dispatch [:file/trigger-url-search!])
                              #_(rf/dispatch [:file/trigger-url-search [type search]]))))}]
        [:span.icon.is-small.is-right
         [:i.fa.fa-search.mr-3]]
        #_[:div.control {:style {#_:float #_"left" :margin "10px 0 0 0px"}}
           [:label.radio
            [:input {:type     "radio" :name "type" :default-checked (when (= type 0) "checked")
                     :on-click #(rf/dispatch [:file/trigger-url-search [0 search-in-bar]])}] " 书籍"]
           [:label.radio
            [:input {:type     "radio" :name "type" :default-checked (when (= type 1) "checked")
                     :on-click #(rf/dispatch [:file/trigger-url-search [1 search-in-bar]])}] " 磁盘文件(文件名)"]
           [:label.radio
            [:input {:type     "radio" :name "type" :default-checked (when (= type 2) "checked")
                     :on-click #(rf/dispatch [:file/trigger-url-search [2 search-in-bar]])}] " 磁盘文件(路径)"]
           [:label.radio
            [:input {:type     "radio" :name "type" :default-checked (when (= type 3) "checked")
                     :on-click #(rf/dispatch [:file/trigger-url-search [3 search-in-bar]])}] " 磁盘文件和文件夹"]]
        (let [{:keys [search-kind search-size search-sort search-range-x search-range-y]}
              @(rf/subscribe [:file/search-hint])
              search-obj-now @(rf/subscribe [:file/search-obj])
              basic-style {:style {:background-color "#3298dc" :color "white"
                                   :border-radius    "20px" :box-shadow "none"}}]
          [:div {:style {:text-align "center" :margin-top "30px"}}
           [:div.select.is-light.is-small.mr-1
            [:select (merge basic-style
                            {:on-change
                             #(do (rf/dispatch [:file/drop-search-obj!])
                                  (rf/dispatch [:file/search-obj-set!
                                                [:search-type (.. % -target -value)]])
                                  (rf/dispatch [:file/trigger-url-search!]))
                             :default-value (:search-type search-obj-now)})
             (for [type all-kind] ^{:key type} [:option type])]]
           (when (not-empty search-range-y)
             [:div.select.is-white.is-small.mr-1
              [:select (merge basic-style
                              {:on-change
                               #(do (rf/dispatch [:file/search-obj-set!
                                                  [:search-range-y (.. % -target -value)]])
                                    (rf/dispatch [:file/trigger-url-search!]))
                               :default-value (:search-range-y search-obj-now)})
               (for [type search-range-y] ^{:key type} [:option type])]])
           (when (not-empty search-size)
             [:div.select.is-white.is-small.mr-1
              [:select (merge basic-style
                              {:on-change
                               #(do (rf/dispatch [:file/search-obj-set!
                                                  [:search-size (.. % -target -value)]])
                                    (rf/dispatch [:file/trigger-url-search!]))
                               :default-value (:search-size search-obj-now)})
               (for [type search-size] ^{:key type} [:option type])]])
           (when (not-empty search-range-x)
             [:div.select.is-white.is-small.mr-1
              [:select (merge basic-style
                              {:on-change
                               #(do (rf/dispatch [:file/search-obj-set!
                                                  [:search-range-x (.. % -target -value)]])
                                    (rf/dispatch [:file/trigger-url-search!]))
                               :default-value (:search-range-x search-obj-now)})
               (for [type search-range-x] ^{:key type} [:option type])]])
           (when (not-empty search-kind)
             [:div.select.is-white.is-small.mr-1
              [:select (merge basic-style
                              {:on-change
                               #(do (rf/dispatch [:file/search-obj-set!
                                                  [:search-kind (.. % -target -value)]])
                                    (rf/dispatch [:file/trigger-url-search!]))
                               :default-value (:search-kind search-obj-now)})
               (for [type search-kind] ^{:key type} [:option type])]])
           (when (not-empty search-sort)
             [:div.select.is-white.is-small.mr-1
              [:select (merge basic-style
                              {:on-change
                               #(do (rf/dispatch [:file/search-obj-set!
                                                  [:search-sort (.. % -target -value)]])
                                    (rf/dispatch [:file/trigger-url-search!]))
                               :default-value (:search-sort search-obj-now)})
               (for [type search-sort] ^{:key type} [:option type])]])])]]]
     [:div.container.mt-5.mb-5
      (let [{search-type :search-type} @(rf/subscribe [:file/search-obj])
            search-type (get file-cn->k search-type :book)
            is-searching-book? (= :book search-type)
            {:keys [data]} @(rf/subscribe [(if is-searching-book?
                                             :book/search-data :disk/search-data)])]
        (if (empty? data)
          [:div.ml-2.pt-3 (if (nil? data) "" "没有相关的搜索结果 (；′⌒`)。")]
          [:<>
           (if is-searching-book?
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
                [:p.mt-1.ml-1.is-size-6.has-text-grey author]])
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
                                   (gstring/format "%.2f MB" (/ size 1048576)))]]])]))]))]]))

