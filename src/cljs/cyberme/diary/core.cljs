(ns cyberme.diary.core
  "整体设计：
  搜索关键词 --> 搜索关键词翻译 --> 更新 URL --> :diary/list 触发 HTTP 查询 --> 展示结果
                   翻页 ------>
  "
  (:require [clojure.string :as string]
            [clojure.string :as str]
            [cyberme.diary.edit :as edit]
            [cyberme.diary.util :refer [diary-date-str oss-process]]
            [goog.string :as gstring]
            [re-frame.core :as rf]))

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

(def search-message "搜索语法：
2022
2022.03
2021-2022
2021.03-2022.03
#abc #def
keyword1 keyword2")

(defn search-unit [input]
  (condp re-matches input
    #"(\d{4})" :>> (fn [[_ year]] {:year (js/parseInt year)})
    #"(\d{4})\.(\d+)" :>> (fn [[_ year month]] {:year  (js/parseInt year)
                                                :month (js/parseInt month)})
    #"(\d{4})-(\d{4})" :>> (fn [[_ from-year to-year]] {:from-year (js/parseInt from-year)
                                                        :to-year   (js/parseInt to-year)})
    #"(\d{4})\.(\d+)-(\d{4})\.(\d+)" :>> (fn [[_ from-year from-month
                                               to-year to-month]]
                                           {:from-year  (js/parseInt from-year)
                                            :from-month (js/parseInt from-month)
                                            :to-year    (js/parseInt to-year)
                                            :to-month   (js/parseInt to-month)})
    #"(#[A-Za-z0-9\u4e00-\u9fa5-_]+)" :>> (fn [[_ tag]] {:tag tag})
    {:search input}))

(defn search-input [input]
  (assoc
    (reduce (fn [agg new]
              ;只允许 search 和 tag 有多个，其余全部取最后一个
              (let [{:keys [year month from-year from-month
                            to-year to-month tag search]}
                    (search-unit new)]
                {:year       year
                 :month      month
                 :from-year  from-year
                 :from-month from-month
                 :to-year    to-year
                 :to-month   to-month
                 :tag        (if tag (conj (or (:tag agg) []) tag)
                                     (:tag agg))
                 :search     (if search (conj (or (:search agg) []) search)
                                        (:search agg))}))
            {}
            (str/split input #" "))
    :origin input))

(defn clean-search-input [input]
  (let [search-result (search-input input)
        non-empty-keys (reduce (fn [agg [k v]]
                                 (if-not (nil? v) (conj agg k) agg))
                               [] search-result)
        clean-result (select-keys search-result non-empty-keys)]
    clean-result))

(rf/reg-event-db
  :diary/trigger-url-search!
  (fn [db [_ push-state?]]
    (let [real-search-obj (:diary/search-obj db)]
      (if push-state?
        (reitit.frontend.easy/push-state :diary nil real-search-obj)
        (reitit.frontend.easy/replace-state :diary nil real-search-obj))
      db)))

(rf/reg-event-db
  :diary/search-obj-set!
  (fn [db [_ [key value]]]
    (if (:diary/search-obj db)
      (assoc-in db [:diary/search-obj key] value)
      (assoc db :diary/search-obj {key value}))))

(rf/reg-event-db
  :diary/search-obj-merge!
  (fn [db [_ new-map]]
    (if-let [old (:diary/search-obj db)]
      (assoc db :diary/search-obj (merge old new-map))
      (assoc db :diary/search-obj new-map))))

(rf/reg-event-db
  :diary/search-obj-reset!
  (fn [db [_ maps]]
    (assoc db :diary/search-obj maps)))

(rf/reg-sub
  :diary/current-range
  (fn [db _] (let [obj (:diary/search-obj db)]
               [(:from obj) (:to obj)])))

(defn diary-header []
  "日记页标题、描述和新建按钮、过滤器选项"
  [:div {:style {:background-color "#48c774"}}
   [:div.hero.is-small.container.is-success
    [:div.hero-body
     [:div.columns
      [:div.column.is-6.is-family-code
       [:p.mb-4.mt-2
        [:span.title.is-clickable
         {:on-click #(do
                       (rf/dispatch [:diary/search-obj-reset! {}])
                       (rf/dispatch [:diary/trigger-url-search! true]))} "日记"]
        [:span.dui-tips
         {:data-tooltip "语法帮助"}
         [:a {:on-click #(rf/dispatch [:global/notice
                                       {:pre-message help-message}])

              :style    {:cursor       :pointer
                         :font-size    :10px
                         :margin-left  :5px
                         :margin-right :10px}}
          [:i.fa.fa-question-circle-o {:style {:font-size      :20px
                                               :vertical-align :50%
                                               :color          :white}}]]]
        [:span.dui-tips.mr-3
         {:data-tooltip "写新日记"}
         [:a {:on-click #(rf/dispatch [:common/navigate! :diary-new])}
          [:i.fa.fa-sticky-note {:style {:font-size      :20px
                                         :vertical-align :50%
                                         :color          :white}}]]]
        [:span.subtitle.is-clickable
         {:on-click #(do
                       (rf/dispatch [:diary/search-obj-reset! {:draft true}])
                       (rf/dispatch [:diary/trigger-url-search! true]))} "草稿"]
        [:span.dui-tips
         {:data-tooltip "写新想法"}
         [:a {:on-click #(rf/dispatch [:common/navigate! :diary-draft-new])}
          [:i.fa.fa-thumb-tack {:style {:font-size      :15px
                                        :margin-left    :5px
                                        :vertical-align :30%
                                        :color          :white}}]]]]
       [:p
        [:span
         {:style {:padding-right "5px"
                  :border-right  ".3em solid transparent"
                  :animation     "cursor 1.5s infinite"}}
         "Life is a struggle"]]]
      (let [params (-> (js/URL. (.-location js/window)) (.-searchParams))
            search-in-bar (or (.get params "origin") "")]
        [:div.column.is-6.is-align-self-center
         [:div.float-reactive
          [:p.control.has-icons-left
           {:style {:width :258px} :title search-message}
           [:input.input.is-success.is-small.is-rounded
            {:type          "text" :placeholder "搜索日记"
             :default-value search-in-bar
             :on-key-up     (fn [e]
                              (if (= 13 (.-keyCode e))
                                (when-let [search (.-value (.-target e))]
                                  (do
                                    (rf/dispatch [:diary/search-obj-reset!
                                                  (merge (clean-search-input search)
                                                         {:from 1 :to 10})])
                                    ;每次搜索都可以返回到上一次结果
                                    (rf/dispatch [:diary/trigger-url-search! true])))))}]
           [:span.icon.is-left
            [:i.fa.fa-search {:aria-hidden "true"}]]]]])]]]])

(defn diary-card [{:keys [id title content info create_at update_at] :as diary}]
  (let [description (or (first (string/split-lines (or content ""))) "暂无描述")
        ;文章中的第一个图片 URL 或 nil
        first-content-url (second (re-find #"!\[.*?\]\((.*?)\)" (or content "")))
        first-content-url (if (and first-content-url
                                   (str/includes? first-content-url "static2.mazhangjing.com"))
                            (str first-content-url oss-process)
                            first-content-url)
        is-draft? (:is-draft? info)]
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
           [:a.ml-1 [:span.tag.is-rounded (str "# " label)]])]
        [:p {:style {:margin-left :-7px :margin-top :10px}}
         [:a.ml-1]])
      (if is-draft?
        [:div.is-clickable.is-size-7
         {:on-click #(rf/dispatch [:global/notice
                                   {:message  "确定删除此日记草稿吗，此操作不可恢复！"
                                    :callback [[:diary/delete-current-refresh-draft id]]}])
          :style    {:opacity 0.5
                     :margin  "-40px 0 -20px 0"
                     :color   "red"
                     :width   "5em"}}
         [:i.fa.fa-trash-o] " 删除"])]]))

(defn diary-page
  "Diary 主页展示"
  []
  [:<>
   [diary-header]
   (let [{datas :data} @(rf/subscribe [:diary/list-data])]
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
                                           :with-edit        false}])]])
          [:nav.pagination.is-centered.is-justify-content-end.pt-5.mt-5
           {:role "navigation" :aria-label "pagination"}
           [:a.pagination-previous
            {:on-click (fn [_]
                         (let [[f t] @(rf/subscribe [:diary/current-range])]
                           (if (<= f 10)
                             (do
                               (rf/dispatch [:diary/search-obj-merge!
                                             {:from 1 :to 10}])
                               (rf/dispatch [:diary/trigger-url-search! true]))
                             (do
                               (rf/dispatch [:diary/search-obj-merge!
                                             {:from (- f 10) :to (- t 10)}])
                               (rf/dispatch [:diary/trigger-url-search! true])
                               (.scrollTo js/window 0 0)))))}
            "上一页"]
           [:a.pagination-next
            {:on-click (fn [_]
                         (let [[f t] @(rf/subscribe [:diary/current-range])]
                           (rf/dispatch [:diary/search-obj-merge!
                                         {:from (+ f 10) :to (+ t 10)}])
                           (rf/dispatch [:diary/trigger-url-search! true])
                           (.scrollTo js/window 0 0)))}
            "下一页"]]])
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

(defn diary-draft-new-page
  "Diary 新建草稿页面"
  []
  [:div.container>div.content.mt-5
   [edit/edit-page {:title "未命名草稿" :is-draft? true}]])

