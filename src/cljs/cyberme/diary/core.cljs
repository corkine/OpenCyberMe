(ns cyberme.diary.core
  (:require [cyberme.diary.edit :as edit]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.string :as string]
            [cyberme.util.storage :as storage]))

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
                                (rf/dispatch [:daily/set-filter mg])
                                (storage/set-item "daily_filter" mg)
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
                                (reitit.frontend.easy/replace-state :diary nil mg)))
           :value     (or (:contains filter) "")}
          [:option "所有时间"]
          [:option {:value "week"} "一周以内"]
          [:option {:value "month"} "一月以内"]
          [:option {:value "session"} "一季以内"]
          [:option {:value "year"} "一年以内"]]]]]]]))

(defn diary-card [{:keys [id title content info create_at update_at]}
                  {:keys [with-footer with-description with-big-pic with-edit]}]
  (let [description (or (first (string/split-lines (or content ""))) "暂无描述")
        filter-now @(rf/subscribe [:blog/filter])]
    [(if with-footer :div.box.columns.mt-5
                     :div.columns.mt-5)
     [:div.column {:style {:z-index :2}}
      [:p.title
       [:span {:on-click #(rf/dispatch [:common/navigate! :blog-view {:id id}])
               :style    {:cursor       :pointer
                          :margin-right (if-not with-edit :10px :0px)}}
        title]
       (when with-edit
         [:span [:a {:on-click #(rf/dispatch [:common/navigate! :blog-edit {:id id}])
                     :style    {:cursor         :pointer
                                :font-size      :8px
                                :margin-left    :7px
                                :margin-right   :10px
                                :vertical-align :80%}}
                 [:i.material-icons {:style {:font-size :15px
                                             :color     :grey}} "border_color"]]])]
      (let [{labels :labels} info
            labels (or labels [])]
        [:<>
         [:p {:style {:margin-left :-7px :margin-top :-20px}}
          (for [label labels]
            ^{:key label}
            [:a.ml-1 {:on-click #(js/alert "此处应该更新 URL 并过滤")}
             [:span.tag.is-rounded (str "#" label)]])]
         [:p {:style {:margin-top :30px}}
          (let [author-in-db (string/join " " (get info :authors []))]
            (if (string/blank? author-in-db) "佚名" author-in-db))]])
      [:div.tags.mt-0.mb-0
       (for [tag (get info :tags [])]
         ^{:key tag}
         [:span.tag.is-rounded.is-small.is-light.is-success.is-clickable
          {:on-click (fn [_]
                       (let [filter-after (assoc filter-now :labels tag)]
                         (rf/dispatch [:blog/set-filter filter-after])
                         (reitit.frontend.easy/push-state :blog-list nil filter-after)))}
          "# " tag])]
      [:p [:i.material-icons {:style {:vertical-align :-18%
                                      :padding-left   :1px
                                      :padding-right  :4px
                                      :font-size      :18px}} "query_builder"] update_at]]
     (when with-description
       [:div.tile.notification.column
        (when (not with-footer)
          {:style {:color            :white
                   :background-color "rgba(159, 219, 180, 0.19)"}})
        [:div.card-content
         [:div.content (if (clojure.string/blank? description)
                         "尚无介绍.." description)]]])]))

(defn diary-page
  "Diary 主页展示"
  []
  [:<>
   [diary-filter]
   (let [datas @(rf/subscribe [:diary/list-data-filtered])]
     (if-not (empty? datas)
       [:section.section>div.container>div.content.mx-3 {:style {:margin-top "-40px"}}
        (for [data datas]
          ^{:key (:id data)}
          [diary-card data {:with-footer      true
                            :with-description true
                            :with-edit        false}])]
       [:div.hero.is-small.pl-0.pr-0
        [:div.hero-body
         [:div.container.has-text-centered
          [:h3.subtitle.mt-6
           "Oops... 暂无符合条件的日记"]]]]))]
  #_[:div.container>div.content.mt-5
     [edit/edit-page]])