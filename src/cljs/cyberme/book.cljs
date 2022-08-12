(ns cyberme.book
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

(def basic-cloud-path
  "https://mvst-my.sharepoint.cn/personal/corkine_one_mazhangjing_com/_layouts/15/onedrive.aspx?id=%2Fpersonal%2Fcorkine%5Fone%5Fmazhangjing%5Fcom%2FDocuments%2Fcalibre")

(def basic-cloud-download-path
  "https://mvst-my.sharepoint.cn/personal/corkine_one_mazhangjing_com/Documents/calibre")

(def dou-ban-path
  "https://search.douban.com/book/subject_search?search_text=")

(defn book-main []
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
        :defaultValue (or (-> (js/URL. (.-location js/window)) (.-searchParams) (.get "q")) "")
        :on-key-up (fn [e]
                     (if (= 13 (.-keyCode e))
                       (if-let [search (.-value (.-target e))]
                         #_(rf/dispatch [:book/search search])
                         (reitit.frontend.easy/replace-state :book nil {:q search}))))}]
      [:span.icon.is-small.is-right
       [:i.fa.fa-search.mr-3]]]]]
   [:div.container.mt-5
    (let [{:keys [data]} @(rf/subscribe [:book/search-data])]
      (if (empty? data)
        [:div.ml-2.pt-3 (if (nil? data) "" "没有相关的搜索结果 (；′⌒`)。")]
        [:<>
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
         [:div.mt-5]]))]])

