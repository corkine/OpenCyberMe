(ns cyberme.diary.edit
  (:require ["react-markdown" :default ReactMarkdown]
            ["remark-gfm" :default remarkGfm]
            [clojure.string :as string]
            [clojure.string]
            [cyberme.dashboard.week-plan :as week]
            [cyberme.diary.util :refer [diary-date diary-date-str is-diary-this-week? oss-process]]
            [cyberme.util.markdown :as md]
            [cyberme.util.tool :as libs]
            [cyberme.util.upload :as up]
            [goog.string :as gstring]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn edit-page [{:keys [id title content info]
                  :or   {title "未命名日记" content ""}
                  :as   old} first-show-preview]
  [:div
   ;只有从 Dashboard/Diary Home 进来的新建 or 本周日记才有此数据
   (let [week-items @(rf/subscribe [:dashboard/week-plan])
         week-items (when (or (nil? id)
                              (is-diary-this-week? (diary-date old)))
                      week-items)]
     (r/with-let
       [show-preview (r/atom (or first-show-preview false))
        title (r/atom title)
        content (r/atom content)
        score (r/atom (:score info))
        date (r/atom (if id (diary-date-str old) (libs/today-str)))
        tags-in (r/atom (libs/l->s (:labels info)))
        is-loading (r/atom false)]
       [:section.section.pt-5>div.container>div.content
        {:style {:margin :auto :max-width :60em}}
        (let [title-str @title
              content-str @content
              date-str @date
              score-str @score
              tags-in-str @tags-in
              preview? @show-preview]
          [:<>
           [:div.mb-5 {:style {:display   :flex :justify-content :space-between
                               :flex-wrap :wrap}}
            [:div
             ;日期框
             [:input.input.is-light.is-family-code
              (merge
                {:style     {:box-shadow    "none"
                             :border-radius 0
                             :border-color  :transparent
                             :margin-left   "-3px"
                             :margin-top    "3.5px"
                             :white-space   :nowrap
                             :font-size     "1.25em"
                             :max-width     "7em"}
                 :value     date-str
                 :on-change #(do (reset! date (.. % -target -value))
                                 )}
                (if preview? {:readOnly "readOnly"} {}))]]
            [:div.is-narrow.is-hidden-touch.py-0
             ;间隔框
             [:div.is-size-5.is-family-code
              {:style {:margin-top "12px" :text-align "center"}} " / "]]
            [:div.py-0.is-flex-grow-5
             ;标题框
             [:input.input.mr-3.is-light.is-size-5
              (merge
                {:style     {:box-shadow    "none"
                             :border-radius 0
                             :border-color  :transparent}
                 :value     title-str
                 :on-change #(reset! title (.. % -target -value))}
                (if preview? {:readOnly "readOnly"} {}))]]
            [:div.py-0.mt-2 {:style {:text-align    "right"
                                     :padding-right "5px"
                                     :padding-left  "14px"}}
             ;编辑/预览和新建/保存框
             [(if preview?
                :button.button.is-info.mr-2
                :button.button.is-link.mr-2)
              {:on-click #(reset! show-preview (not preview?))}
              (if preview? "编辑" "预览")]
             (if (nil? id)
               [:button.button.is-danger.mr-2
                {:on-click #(rf/dispatch [:diary/new
                                          {:title   @title
                                           :content @content
                                           :info    {:day    @date
                                                     :labels (libs/s->l @tags-in)
                                                     :score  (js/parseInt @score)}}])}
                "新建"]
               [:button.button.is-danger.mr-2
                {:on-click (fn [_]
                             (rf/dispatch
                               [:diary/update-current
                                (merge (dissoc old :id :create_at :update_at)
                                       {:id      id
                                        :title   @title
                                        :content @content
                                        :info    (assoc info :labels (libs/s->l @tags-in)
                                                             :day @date
                                                             :score (js/parseInt @score))})]))}
                "保存"])]]
           ;标签和评分框
           [:div.columns {:style {:margin-left "-10px" :margin-bottom "20px"}}
            [:div.column.py-0
             [:div.control.has-icons-left
              [:input.input.is-light
               (merge
                 {:value       tags-in-str
                  :on-change   #(reset! tags-in (.. % -target -value))
                  :style       {:box-shadow    "none"
                                :border-radius 0
                                :border-color  :transparent}
                  :placeholder (if preview? "没有标签" "输入标签，逗号隔开")}
                 (if preview? {:readOnly "readOnly"} {}))]
              [:span.icon.is-left {:style {:height :2.7em}}
               [:i.fa.fa-tags]]]]
            [:div.column.py-0
             #_[:div.control.has-icons-left
                [:input.input.is-light
                 (merge
                   {:value       score-str
                    :on-change   #(reset! score (.. % -target -value))
                    :style       {:box-shadow    "none"
                                  :border-radius 0
                                  :border-color  :transparent}
                    :placeholder (if preview? "没有评分" "输入评分")}
                   (if preview? {:readOnly "readOnly"} {}))]
                [:span.icon.is-left {:style {:height :2.7em}}
                 [:i.fa.fa-star]]]]]
           [week/week-plan-add-dialog]
           [week/week-plan-log-add-dialog]
           [week/week-plan-modify-item-dialog]
           (when week-items
             [:div {:style {:margin "-10px 0px 20px 12px"}}
              [week/plan-widget week-items {:go-diary-add-log false :show-todo true}]])
           [:div.is-size-6.markdown-body {:style {:margin "12px 12px 12px 12px"}}
            (if preview?
              [md/mark-down (string/replace content-str #"(https://static2.mazhangjing.com/.*?\.\w+)\)" (str "$1" oss-process ")"))]
              [:textarea.textarea.is-light
               {:type          :textarea
                :style         (merge {:box-shadow    "none"
                                       :border-radius 0
                                       :border-color  "lightgrey transparent lightgrey transparent"
                                       :border-style  "double none solid none"
                                       :border-width  :3px
                                       :padding       "10px 3px 10px 3px"}
                                      (if @is-loading {:background "lightyellow"} {}))
                :rows          15
                :value         content-str
                :on-change     #(reset! content (.. % -target -value))
                :on-drop       (fn [e]
                                 (let [files (-> e .-dataTransfer .-files)]
                                   (up/upload-file
                                     files
                                     #(let [{:keys [message data status]} %]
                                        (set! (.. e -target -style -background) "")
                                        (if (= status 1)
                                          (swap! content str (gstring/format "\n![](%s)\n" data))
                                          (rf/dispatch [:global/notice {:message (or message "上传图片失败！")}])))))
                                 (.preventDefault e)
                                 (.stopPropagation e))
                :on-drag-over  (fn [e] (.preventDefault e))
                :on-drag       (fn [e])
                :on-drag-start (fn [e] (set! (.. e -target -style -opacity) 0.2))
                :on-drag-enter (fn [e] (set! (.. e -target -style -background) "lightyellow"))}])]
           (if (and (not (nil? id)) preview?)
             [:div.is-clickable.is-size-7
              {:on-click #(rf/dispatch [:global/notice
                                        {:message  "确定删除此日记吗，此操作不可恢复！"
                                         :callback [[:diary/delete-current id]
                                                    [:common/navigate! :diary]]}])
               :style    {:opacity     0.9
                          :padding-top "30px"
                          :margin-left "12px"
                          :width       "5em"}}
              [:i.fa.fa-trash-o] " 删除"]
             [:p.file.is-light.is-small {:style {:margin-top :0em :margin-left :1em}}
              [:label.file-label
               [:input.file-input
                {:type "file" :name "file"
                 :on-change
                 (fn [e] (let [files (.. e -target -files)]
                           (reset! is-loading true)
                           (up/upload-file
                             files
                             #(let [{:keys [message data status]} %]
                                (reset! is-loading false)
                                (if (= status 1)
                                  (swap! content str (gstring/format "\n![](%s)\n" (js/encodeURI data)))
                                  (rf/dispatch [:global/notice {:message (or message "上传图片失败！")}]))))))}]
               [:span.file-cta.mb-3
                [:span.file-label "上传图片"]]]])])]))])