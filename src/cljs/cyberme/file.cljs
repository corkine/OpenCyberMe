(ns cyberme.file
  (:require [clojure.string :as str]
            [cyberme.util.upload :as upload]
            [goog.string :as gstring]
            [re-frame.core :as rf]
            [cyberme.util.request :refer [ajax-flow] :as req]))

;书籍搜索
(ajax-flow {:call           :book/search
            :uri-fn         #(str "/cyber/books/search?q=" %)
            :data           :book/search-data
            :clean          :book/search-clean
            :failure-notice true})

;文件搜索
(ajax-flow {:call           :disk/search
            :uri-fn         #(str "/cyber/disks/search?q=" (second %) "&type=" (first %))
            :data           :disk/search-data
            :clean          :disk/search-clean
            :failure-notice true})

;统一搜索
(rf/reg-event-db
  :file/search
  (fn [db [_ [type query]]]
    (rf/dispatch (case type "0" [:book/search query]
                            "1" [:disk/search [1 query]]
                            "2" [:disk/search [2 query]]
                            "3" [:disk/search [3 query]]
                            [:book/search query]))
    (assoc db :file/search-type type)))

(rf/reg-event-db
  :file/search-clean
  (fn [db _]
    (rf/dispatch [:disk/search-clean])
    (rf/dispatch [:book/search-clean])
    db))

;触发 URL 变更，在 cljc 中触发事件 file/search
(rf/reg-event-db
  :file/trigger-url-search
  (fn [db [_ [type search]]]
    (reitit.frontend.easy/replace-state :file nil {:q search :type type})
    (rf/dispatch [:file/search-clean])
    db))

(rf/reg-sub
  :file/search-type
  (fn [db _]
    (:file/search-type db)))

(def basic-cloud-path
  "https://mvst-my.sharepoint.cn/personal/corkine_one_mazhangjing_com/_layouts/15/onedrive.aspx?id=%2Fpersonal%2Fcorkine%5Fone%5Fmazhangjing%5Fcom%2FDocuments%2Fcalibre")

(def basic-cloud-download-path
  "https://mvst-my.sharepoint.cn/personal/corkine_one_mazhangjing_com/Documents/calibre")

(def dou-ban-path
  "https://search.douban.com/book/subject_search?search_text=")

(defn file-main []
  (let [params (-> (js/URL. (.-location js/window)) (.-searchParams))
        type (js/parseInt (or (.get params "type") "0"))
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
         {:type "text" :placeholder "输入书籍名/作者名"
          :defaultValue search-in-bar
          :on-key-up (fn [e]
                       (if (= 13 (.-keyCode e))
                         (if-let [search (.-value (.-target e))]
                           (rf/dispatch [:file/trigger-url-search [type search]]))))}]
        [:span.icon.is-small.is-right
         [:i.fa.fa-search.mr-3]]
        [:div.control {:style {#_:float #_"left" :margin "10px 0 0 0px"}}
         [:label.radio
          [:input {:type "radio" :name "type" :default-checked (when (= type 0) "checked")
                   :on-click #(rf/dispatch [:file/trigger-url-search [0 search-in-bar]])}] " 书籍"]
         [:label.radio
          [:input {:type "radio" :name "type" :default-checked (when (= type 1) "checked")
                   :on-click #(rf/dispatch [:file/trigger-url-search [1 search-in-bar]])}] " 磁盘文件(文件名)"]
         [:label.radio
          [:input {:type "radio" :name "type" :default-checked (when (= type 2) "checked")
                   :on-click #(rf/dispatch [:file/trigger-url-search [2 search-in-bar]])}] " 磁盘文件(路径)"]
         [:label.radio
          [:input {:type "radio" :name "type" :default-checked (when (= type 3) "checked")
                   :on-click #(rf/dispatch [:file/trigger-url-search [3 search-in-bar]])}] " 磁盘文件和文件夹"]]]]]
     [:div.container.mt-2
      (let [search-type @(rf/subscribe [:file/search-type])
            is-searching-book? (= "0" search-type)
            {:keys [data]} @(rf/subscribe [(if is-searching-book? :book/search-data :disk/search-data)])]
        (if (empty? data)
          [:div.ml-2.pt-3 (if (nil? data) "" "没有相关的搜索结果 (；′⌒`)。")]
          [:<>
           (if is-searching-book?
             (for [{:keys [uuid title author info]} data]
               ^{:key uuid}
               [:div.box {:style {:box-shadow "0 .5em 1em -.125em rgba(10,10,10,.05),0 0 0 1px rgba(10,10,10,.01)"
                                  :margin "5px 0 5px 0"}}
                (when (and (= "PDF" (:format info)) (:resource info))
                  [:div {:style {:float "right" :margin "1em 1em 0 0"}}
                   [:a {:href (str basic-cloud-download-path "/" (get info :path "")
                                   "/" (get info :resource "") "."
                                   (str/lower-case (get info :format "PDF")))
                        :target :_black
                        :title (str "点击在线预览 PDF 文件\n大小："
                                    (gstring/format "%.2f MB" (/ (js/parseInt (or (:size info) "0")) 1048576)))}
                    [:i.fa.fa-file-pdf-o]]])
                [:div {:style {:float "right" :margin "1em 1em 0 0"}}
                 [:a {:href (str basic-cloud-path "/" (get info :path ""))
                      :target :_black
                      :title "点击在 OneDrive 中查看资源"} [:i.fa.fa-cloud]]]
                [:div {:style {:float "right" :margin "1em 1em 0 0"}}
                 [:a {:href (str dou-ban-path title)
                      :target :_black
                      :title "点击跳转豆瓣图书搜索"} [:i.fa.fa-search]]]
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
                                     :margin "5px 0 5px 0"}}
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
                                   (gstring/format "%.2f MB" (/ size 1048576)))]]])]))
           [:div.mt-5]]))]]))

