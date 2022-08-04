(ns cyberme.diary.core
  (:require [cyberme.diary.edit :as edit]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.string :as string]
            [cyberme.util.storage :as storage]
            [cyberme.util.tool :as tool]
            [cljs-time.format :as format]
            [goog.string :as gstring]
            [cyberme.diary.util :refer [diary-date-str oss-process]]
            [cljs-time.core :as t]
            [clojure.string :as str]))

(def help-message "日记支持通用 Markdown 语法、链接高亮以及如下特殊语法：
TODO 标记：
  *[] 1
  *[x] 2
Footer 标记：
  some words[^1]
  [^1]: note here
Table 标记：
  | a | b |
  | - | - |
  | 1 | 2 |
Grammar 高亮：
  ```clojure
  (println \"HELLO\")
  ```
Image 宽高：
  ![alt](url)
  ![alt::300px*100px](url)
  ![alt::300px*](url)
  ![alt::*100px](url)
  ![::300px*100px](url)
  ![::300px*](url)
  ![::*100px](url)
")

(defn diary-filter []
  "日记页标题、描述和新建按钮、过滤器选项"
  (let [labels @(rf/subscribe [:diary/all-labels])
        filter @(rf/subscribe [:diary/filter])]
    [:div {:style {:background-color "#48c774"}}
     [:div.hero.is-small.container.is-success
      [:div.hero-body
       [:p.mb-4.mt-2 [:span.title "起居注"]
        [:span.dui-tips
         {:data-tooltip "点击查看语法帮助"}
         [:a {:on-click #(rf/dispatch [:global/notice
                                       {:pre-message help-message}])

              :style    {:cursor         :pointer
                         :font-size      :10px
                         :margin-left    :5px
                         :margin-right   :10px
                         :vertical-align :80%}}
          [:i.material-icons {:style {:font-size :15px
                                      :color     :white}} "help_outline"]]]
        [:span.dui-tips
         {:data-tooltip "写一篇新日记"}
         [:a {:on-click #(rf/dispatch [:common/navigate! :diary-new])}
          [:i.material-icons {:style {:font-size :30px
                                      :color     :white}} "fiber_new"]]]]
       [:div.columns
        [:div.column.is-6.is-family-code
         [:p
          [:span
           {:style {:padding-right "5px"
                    :border-right  ".3em solid transparent"
                    :animation     "cursor 1.5s infinite"}}
           "Life is a struggle"]]]
        [:div.column.is-6.has-text-right.has-text-left-mobile
         {:style {:margin-top "-8px"}}
         [:div.select.is-small.is-success.mr-2.mt-1>select
          {:on-change (fn [e] (let [sel-o (-> e .-target .-value)
                                    mg (if (= sel-o "所有标签")
                                         (dissoc filter :labels)
                                         (assoc filter :labels sel-o))]
                                (rf/dispatch [:diary/set-filter mg])
                                (storage/set-item "diary_filter" mg)
                                (reitit.frontend.easy/replace-state :diary nil mg)))
           :value     (or (:labels filter) "")}
          [:option "所有标签"]
          (for [label labels]
            ^{:key label}
            [:option {:value label} label])]
         [:div.select.is-small.is-success.mr-2.mt-1>select
          {:on-change (fn [e] (let [sel-o (-> e .-target .-value)
                                    mg (if (= sel-o "所有时间")
                                         (dissoc filter :contains)
                                         (assoc filter :contains sel-o))]
                                (rf/dispatch [:diary/set-filter mg])
                                (storage/set-item "diary_filter" mg)
                                (reitit.frontend.easy/replace-state :diary nil mg)))
           :value     (or (:contains filter) "")}
          [:option "所有时间"]
          [:option {:value "week"} "一周以内"]
          [:option {:value "month"} "一月以内"]
          [:option {:value "month2"} "两月以内"]
          [:option {:value "year"} "一年以内"]]]]]]]))

(defn diary-card [{:keys [id title content info create_at update_at] :as diary}]
  (let [description (or (first (string/split-lines (or content ""))) "暂无描述")
        ;文章中的第一个图片 URL 或 nil
        first-content-url (second (re-find #"!\[.*?\]\((.*?)\)" (or content "")))
        first-content-url (if (and first-content-url
                                   (str/includes? first-content-url "static2.mazhangjing.com"))
                            (str first-content-url oss-process)
                            first-content-url)
        ;用于过滤特定标签和日期的日记
        filter-now @(rf/subscribe [:diary/filter])]
    [:div.box.columns.mt-5 {:style (if first-content-url
                                     {:background
                                      (gstring/format "%s,url(%s)"
                                                      "linear-gradient(rgba(255,255,255,0.98),rgba(255,255,255,0.85))"
                                                      first-content-url)
                                      :background-size "cover"} {})}
     [:div.column {:style {:z-index :2
                           :opacity 1}}
      [:p.subtitle.is-family-code {:style {:margin-top    "-7px"
                                           :margin-bottom "10px"}}
       (diary-date-str diary)]
      [:p.is-size-4.mb-0
       [:span.is-clickable {:on-click #(rf/dispatch [:common/navigate! :diary-view {:id id}])}
        title]
       [:span [:a {:on-click #(rf/dispatch [:common/navigate! :diary-edit {:id id}])
                   :style    {:cursor         :pointer
                              :font-size      :8px
                              :margin-left    :10px
                              :margin-right   :10px
                              :vertical-align :10%}}
               [:i.material-icons {:style {:font-size :15px
                                           :color     :lightgray
                                           :opacity   0.5}} "border_color"]]]]
      [:p {:style {:border-left "3px solid #dbdbdb"
                   :padding     "0 0 0 0.8em"
                   :margin      "0.5em 0 1em 0"
                   :max-height  "3em"
                   :min-height  "3em"
                   :font-size   "0.9em"
                   :color       "#a8a8a8"
                   :overflow    :hidden}}
       (if (> (count description) 63)
         (str (.slice description 0 60) "...")
         description)]
      (if (> (count (:labels info)) 0)
        [:p {:style {:margin-left :-7px :margin-top :10px :margin-bottom :-5px}}
         (for [label (:labels info)]
           ^{:key label}
           [:a.ml-1 {:on-click (fn [_]
                                 (let [filter-after (assoc filter-now :labels label)]
                                   (rf/dispatch [:diary/set-filter filter-after])
                                   (reitit.frontend.easy/push-state :diary nil filter-after)))}
            [:span.tag.is-rounded (str "# " label)]])]
        [:p {:style {:margin-left :-7px :margin-top :10px}}
         [:a.ml-1]])]]))

(defn diary-page
  "Diary 主页展示"
  []
  [:<>
   [diary-filter]
   (let [datas @(rf/subscribe [:diary/list-data-filtered])]
     (if-not (empty? datas)
       (let [datas (vec (partition-all 2 datas))]
         [:section.section>div.container>div.content {:style {:margin-top "-50px"}}
          (for [pair datas]
            ^{:key (:id (first pair))}
            [:div.columns
             [:div.column {:style {:margin "-10px 10px"}}
              [diary-card (first pair) {:with-footer      true
                                        :with-description true
                                        :with-edit        false}]]
             [:div.column {:style {:margin "-10px 10px"}}
              (when (second pair)
                [diary-card (second pair) {:with-footer      true
                                           :with-description true
                                           :with-edit        false}])]])])
       [:div.hero.is-small.pl-0.pr-0
        [:div.hero-body
         [:div.container.has-text-centered
          [:h3.subtitle.mt-6
           "Oops... 暂无符合条件的日记"]]]]))])

(defn diary-edit-page
  "Diary 修改页面"
  []
  [:div.container>div.content.mt-5
   (if-let [data @(rf/subscribe [:diary/current-data])]
     [edit/edit-page data]
     [:p.ml-6.mt-6 "正在加载..."])])

(defn diary-view-page
  "Diary 展示页面"
  []
  [:div.container>div.content.mt-5
   (if-let [data @(rf/subscribe [:diary/current-data])]
     [edit/edit-page data true]
     [:p.ml-6.mt-6 "正在加载..."])])

(defn diary-new-page
  "Diary 新建页面"
  []
  [:div.container>div.content.mt-5
   [edit/edit-page]])

