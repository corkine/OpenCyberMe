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
      tags-in (r/atom (libs/l->s (:labels info)))]
     [:section.section.pt-5>div.container>div.content
      (let [title-str @title
            content-str @content
            tags-in-str @tags-in
            preview? @show-preview]
        [:<>
         [:div.subtitle.is-flex.mb-2
          [:div.mr-1.ml-2.is-size-5.is-family-code
           {:style {:white-space :nowrap
                    :align-self  :center}}
           ;TODO 新建这里收集日期并 POST，更新也收集日期并 POST
           (if id (diary-date-str old) (libs/today-str)) " /"]
          [:input.input.mr-3.is-light
           (merge
             {:value     title-str
              :on-change #(reset! title (.. % -target -value))
              :style     {:box-shadow    "none"
                          :border-radius 0
                          :border-color  :transparent
                          :margin-bottom :3px}}
             (if preview? {:readOnly "readOnly"} {}))]
          [(if preview?
             :button.button.is-info.is-pulled-right.mr-3.is-align-self-flex-end
             :button.button.is-link.is-pulled-right.mr-3.is-align-self-flex-end)
           {:on-click #(reset! show-preview (not preview?))}
           (if preview? "编辑" "预览")]
          (if (nil? id)
            [:button.button.is-danger.is-pulled-right.is-align-self-flex-end
             {:on-click #(rf/dispatch [:diary/new
                                       {:title   @title
                                        :content @content
                                        :info    {:day    (libs/today-str)
                                                  :labels (libs/s->l @tags-in)}}])}
             "新建"]
            [:button.button.is-danger.is-pulled-right.is-align-self-flex-end
             {:on-click (fn [_]
                          (rf/dispatch
                            [:diary/update-current
                             (merge (dissoc old :id :create_at :update_at)
                                    {:id      id
                                     :title   @title
                                     :content @content
                                     :info    (assoc info :labels (libs/s->l @tags-in))})]))}
             "保存"])]
         [:div.control.has-icons-left.mb-4
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
           [:i.fa.fa-tags]]]
         [:div.is-size-6.markdown-body {:style {:margin :7px}}
          (if preview?
            [md/mark-down content-str]
            [:textarea.textarea.is-light
             {:type      :textarea
              :style     {:box-shadow    "none"
                          :border-radius 0
                          :border-color  "lightgrey transparent lightgrey transparent"
                          :border-style  "double none solid none"
                          :border-width  :3px
                          :padding       "10px 1px 10px 1px"}
              :rows      15
              :value     content-str
              :on-change #(reset! content (.. % -target -value))}])]
         (when (and (not (nil? id)) preview?)
           [:a.has-text-danger.ml-2.pt-6
            {:on-click #(rf/dispatch [:global/notice
                                      {:message  "确定删除此日记吗，此操作不可恢复！"
                                       :callback [[:diary/delete-current id]
                                                  [:common/navigate! :diary]]}])}
            "删除此日记"])])])])