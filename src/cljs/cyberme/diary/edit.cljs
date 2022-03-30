(ns cyberme.diary.edit
  (:require ["react-markdown" :default ReactMarkdown]
            ["remark-gfm" :default remarkGfm]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [clojure.string :as string]
            [cyberme.util.tool :as libs]
            [cyberme.diary.util :refer [diary-date-str]]
            [cyberme.util.markdown :as md]))

(defn edit-page [{:keys [id title content info]
                  :or   {title "未命名日记" content ""}
                  :as   old} first-show-preview]
  [:div
   (r/with-let
     [show-preview (r/atom (or first-show-preview false))
      title (r/atom title)
      content (r/atom content)
      score (r/atom (:score info))
      date (r/atom (if id (diary-date-str old) (libs/today-str)))
      tags-in (r/atom (libs/l->s (:labels info)))]
     [:section.section.pt-5>div.container>div.content
      (let [title-str @title
            content-str @content
            date-str @date
            score-str @score
            tags-in-str @tags-in
            preview? @show-preview]
        [:<>
         [:div.columns.mb-5
          [:div.column.is-2.py-0
           ;日期框
           [:input.input.is-light.is-size-5.is-family-code
            (merge
              {:style     {:box-shadow    "none"
                           :border-radius 0
                           :border-color  :transparent
                           :margin-left   "-3px"
                           :margin-top    "3px"
                           :white-space   :nowrap}
               :value     date-str
               :on-change #(reset! date (.. % -target -value))}
              (if preview? {:readOnly "readOnly"} {}))]]
          [:div.column.is-narrow.is-hidden-touch.py-0
           ;间隔框
           [:div.is-size-5.is-family-code
            {:style {:margin-top "12px"}} " / "]]
          [:div.column.is-7.py-0
           ;标题框
           [:input.input.mr-3.is-light.is-size-5
            (merge
              {:style     {:box-shadow    "none"
                           :border-radius 0
                           :border-color  :transparent}
               :value     title-str
               :on-change #(reset! title (.. % -target -value))}
              (if preview? {:readOnly "readOnly"} {}))]]
          [:div.column.is-3.pr-5.py-0.mt-2
           ;编辑/预览和新建/保存框
           (if (nil? id)
             [:button.button.is-danger.is-pulled-right
              {:on-click #(rf/dispatch [:diary/new
                                        {:title   @title
                                         :content @content
                                         :info    {:day    @date
                                                   :labels (libs/s->l @tags-in)
                                                   :score  (js/parseInt @score)}}])}
              "新建"]
             [:button.button.is-danger.is-pulled-right
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
              "保存"])
           [(if preview?
              :button.button.is-info.is-pulled-right.mr-2.ml-2.is-align-self-flex-end
              :button.button.is-link.is-pulled-right.mr-2.ml-2.is-align-self-flex-end)
            {:on-click #(reset! show-preview (not preview?))}
            (if preview? "编辑" "预览")]]]
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
           [:div.control.has-icons-left
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
         [:div.is-size-6.markdown-body {:style {:margin :12px}}
          (if preview?
            [md/mark-down content-str]
            [:textarea.textarea.is-light
             {:type      :textarea
              :style     {:box-shadow    "none"
                          :border-radius 0
                          :border-color  "lightgrey transparent lightgrey transparent"
                          :border-style  "double none solid none"
                          :border-width  :3px
                          :padding       "10px 3px 10px 3px"}
              :rows      15
              :value     content-str
              :on-change #(reset! content (.. % -target -value))}])]
         (when (and (not (nil? id)) preview?)
           [:div.has-text-danger.is-clickable
            {:on-click #(rf/dispatch [:global/notice
                                      {:message  "确定删除此日记吗，此操作不可恢复！"
                                       :callback [[:diary/delete-current id]
                                                  [:common/navigate! :diary]]}])
             :style    {:opacity     0.9
                        :padding-top "30px"
                        :margin-left "12px"
                        :width       "5em"}}
            "删除此日记"])])])])