(ns cyberme.psych.exp1.main
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [cljs-time.core :as t]
            [cyberme.util.request :refer [ajax-flow] :as req]))

"实验设计：第一屏收集用户信息，之后点击按钮开始，接着呈现前测问卷、题目和后测问卷，最后是结束语。
对于题目而言，依次作答，如果正确则继续，如果错误则按照实验条件不同进行不同反馈，包括给予纠错提示、正确答案、作答次数 1/2 等。"

(defonce is-debug (atom false))

(defn questionnaire-1 [len title]
  [:div {:style {:margin-top :10em
                 :background "rgb(249, 249, 249)"}}
   (if @is-debug [:div {:style {:position :absolute :right :20px :bottom :10px}}
                  [:span.is-clickable {:on-click #(rf/dispatch [:go-step -1])} "<  "]
                  [:span.is-clickable {:on-click #(rf/dispatch [:go-step 1])} "  >"]])
   (r/with-let
     [answer (r/atom {})]
     [:<>
      [:table.table.is-hoverable {:style {:margin :auto
                                          :border "15px white solid"}}
       [:thead
        [:tr
         [:th "编号"]
         [:th "题目"]
         [:th [:abbr {:title "非常好"} "非常好"]]
         [:th [:abbr {:title "比较好"} "比较好"]]
         [:th [:abbr {:title "好"} "好"]]
         [:th [:abbr {:title "一般"} "一般"]]
         [:th [:abbr {:title "差"} "差"]]]]
       [:tfoot
        [:tr
         [:th "编号"]
         [:th "题目"]
         [:th [:abbr {:title "非常好"} "非常好"]]
         [:th [:abbr {:title "比较好"} "比较好"]]
         [:th [:abbr {:title "好"} "好"]]
         [:th [:abbr {:title "一般"} "一般"]]
         [:th [:abbr {:title "差"} "差"]]]]
       [:tbody
        (for [index (range 1 (+ len 1))]
          ^{:key index}
          [:tr
           [:th index]
           [:td "Leicester City Leicester City Leicester City Leicester City"]
           [:td [:label.radio
                 {:on-click #(swap! answer assoc index "非常好")}
                 [:input {:type "radio" :name (str "q" index)}] " 非常好"]]
           [:td [:label.radio #_{:disabled "true"}
                 {:on-click #(swap! answer assoc index "比较好")}
                 [:input {:type "radio" :name (str "q" index) #_:disabled #_"true"}] " 比较好"]]
           [:td [:label.radio
                 {:on-click #(swap! answer assoc index "好")}
                 [:input {:type "radio" :name (str "q" index)}] " 好"]]
           [:td [:label.radio
                 {:on-click #(swap! answer assoc index "一般")}
                 [:input {:type "radio" :name (str "q" index)}] " 一般"]]
           [:td [:label.radio
                 {:on-click #(swap! answer assoc index "差")}
                 [:input {:type "radio" :name (str "q" index)}] " 差"]]])]]
      [:div.is-flex.is-justify-content-center
       [:button.button.is-info.is-large.is-fullwidth
        {:style    {:margin-top :100px :margin-bottom :100px
                    :max-width  :30em}
         :on-click (fn []
                     (println @answer)
                     (if (< (count @answer) len)
                       (js/alert "请完成所有题目后再提交！")
                       (do
                         (rf/dispatch [:save-answer [title @answer]])
                         (reset! answer {})
                         (rf/dispatch [:go-step 1]))))}
        "完成问卷"]]])])

(defn hint
  ([title sub-title]
   (hint title sub-title nil nil))
  ([title sub-title btn-title]
   (hint title sub-title btn-title #(rf/dispatch [:go-step 1])))
  ([title sub-title btn-title btn-action]
   [:div {:style {:margin-top :20% :text-align :center}}
    (if @is-debug
      [:p.title {:on-click #(rf/dispatch [:go-step -1])} title]
      [:p.title title])
    (if @is-debug
      [:p.subtitle.is-6.mt-1 {:style                   {:margin :auto :width :50em}
                              :on-click                #(rf/dispatch [:go-step -1])
                              :dangerouslySetInnerHTML {:__html sub-title}}]
      [:p.subtitle.is-6.mt-1 {:style                   {:margin :auto :width :50em}
                              :dangerouslySetInnerHTML {:__html sub-title}}])
    (if btn-title
      [:button.button.is-info.mt-5
       {:on-click btn-action} btn-title])]))

(defn upload []
  (r/create-class
    {:component-did-mount
     (fn [this]
       (let [data @(rf/subscribe [:get-answer])
             _ (println "all result is" data)]
         (if data
           (rf/dispatch [:psy-exp-data-upload (merge data {:upload_at (t/time-now)})]))))
     :reagent-render
     (fn [title sub-title]
       (let [data @(rf/subscribe [:get-answer])
             callback @(rf/subscribe [:psy-exp-data-callback])]
         [:div {:style {:margin-top :20% :text-align :center}}
          (if @is-debug
            [:p.title {:on-click #(rf/dispatch [:go-step -1])} title]
            [:p.title title])
          (if callback
            [:p.subtitle.is-6.mt-1 {:style {:margin :auto :width :30em}} (:message callback)]
            [:p.subtitle.is-6.mt-1 {:style {:margin :auto :width :30em}} sub-title])
          (if callback
            [:button.button.is-danger.mt-5
             {:on-click #(js/alert "实验结束，请联系主试人员！")} "实验已结束"]
            [:button.button.is-info.mt-5
             {:on-click #(if data
                           (rf/dispatch [:psy-exp-data-upload (merge data {:upload_at (t/time-now)})]))}
             "重新上传数据"])]))}))

(defn collect []
  (r/with-let
    ;UUID 为了防止最后因为网络问题提交多次
    [answer (r/atom {:gender "男" :grade "初中一年级" :uuid (str (random-uuid))})
     ;name (r/cursor answer [:name])
     gender (r/cursor answer [:gender])
     grade (r/cursor answer [:grade])
     school (r/cursor answer [:school])]
    [:div {:style {:max-width :60%
                   :margin    "20% auto"}}
     (if @is-debug [:div {:style {:position :absolute :right :20px :bottom :10px}}
                    [:span.is-clickable {:on-click #(rf/dispatch [:go-step -1])} "<  "]
                    [:span.is-clickable {:on-click #(rf/dispatch [:go-step 1])} "  >"]])
     #_[:div.field.is-horizontal
        [:div.field-label.is-normal
         [:label.label "姓名"]]
        [:div.field-body
         [:div.field
          [:p.control
           [:input.input {:type      "text" :placeholder "请输入你的姓名"
                          :value     (or @name "")
                          :on-change #(reset! name (.. % -target -value))}]]]]]
     [:div.field.is-horizontal
      [:div.field-label.is-normal
       [:label.label "性别"]]
      [:div.field-body
       [:div.select
        [:select {:value     (or @gender "男")
                  :on-change #(reset! gender (.. % -target -value))}
         [:option "男"]
         [:option "女"]]]]]
     [:div.field.is-horizontal
      [:div.field-label.is-normal
       [:label.label "年级"]]
      [:div.field-body
       [:div.select
        [:select {:value     (or @grade "初中一年级")
                  :on-change #(reset! grade (.. % -target -value))}
         [:option "初中一年级"]
         [:option "初中二年级"]
         [:option "初中三年级"]
         [:option "高中一年级"]
         [:option "高中二年级"]
         [:option "高中三年级"]
         [:option "其他"]]]]]
     [:div.field.is-horizontal
      [:div.field-label.is-normal
       [:label.label "学校"]]
      [:div.field-body
       [:div.field
        [:p.control
         [:input.input {:type      "text" :placeholder "请输入你的学校"
                        :value     (or @school "")
                        :on-change #(reset! school (.. % -target -value))}]]]]]
     [:div.field.is-horizontal
      [:div.field-label.is-normal
       [:label.label ""]]
      [:div.field-body
       [:div.field
        [:div.is-flex.is-justify-content-center
         [:div.button.is-info.is-fullwidth.field-body
          {:style    {:margin-top :50px :margin-bottom :30px}
           :on-click (fn []
                       (println @answer)
                       (let [is-ok (and #_@name
                                     @gender
                                     @grade
                                     @school)]
                         (if is-ok
                           (do
                             (rf/dispatch [:save-answer ["被试收集" @answer]])
                             (reset! answer {})
                             (rf/dispatch [:go-step 1]))
                           (js/alert "请完成表单！"))))}
          "确定"]]]]]]))

(defn problem
  "题目展示，包括题目题干、几个选择项、提示和详解，根据实验条件不同，可能需要
  两次分别显示提示和详解、
  两次其一不给提示，其二给详解、
  一次，给详解
  一次，给提示和详解"
  [{:keys [body answers right-answer hint explain
           show-once-hint show-once-explain
           show-twice show-twice-first-hint show-twice-next-explain]}]
  (r/with-let
    [first-answer (r/atom nil)
     is-second (r/atom false)]
    (let [id (random-uuid)]
      [:div {:style {:margin "20% auto"}}
       [:div {:style {:margin :auto :max-width :50em}}
        [:div body]
        [:div.mt-1
         (doall
           (for [index (range 0 (count answers))]
             ^{:key (str id index)}
             [:<>
              (let [can-select (nil? @first-answer)
                    checked? (and @first-answer (= @first-answer index))]
                [:label.radio
                 {:on-click (fn []
                              (when can-select
                                (let [right? (= index right-answer)]
                                  (println "select" (get answers index) ", is right?" right?)
                                  (reset! first-answer index))))}
                 [:input (merge (if can-select
                                  {:type "radio" :name id}
                                  {:type "radio" :name id :disabled "disabled"})
                                (if checked?
                                  {:checked "checked"} {}))] " " (get answers index)])]))]
        (let [have-answer (or @first-answer)
              answer-right (and @first-answer (= @first-answer right-answer))]
          [:<>
           (if have-answer
             (if answer-right
               [:div.mt-3.has-text-success "回答正确"]
               [:div.mt-3.has-text-danger "回答错误"]))
           (if (and have-answer
                    (or (and (not show-twice) show-once-hint)
                        (and show-twice show-twice-first-hint (not @is-second))))
             [:div.mt-3 hint])
           (if (and have-answer
                    (or (and (not show-twice) show-once-explain)
                        (and show-twice show-twice-next-explain @is-second)))
             [:div.mt-3 explain])
           [:div.mt-5
            (cond (and have-answer
                       (not show-twice))
                  [:button.button.is-info
                   {:on-click #(rf/dispatch [:go-step 1])} "继续下一题"]
                  (and have-answer
                       show-twice
                       @is-second)
                  [:button.button.is-info
                   {:on-click #(rf/dispatch [:go-step 1])} "继续下一题"]
                  (and have-answer
                       answer-right
                       show-twice
                       (not @is-second))
                  [:button.button.is-info
                   {:on-click #(rf/dispatch [:go-step 1])} "继续下一题"]
                  (and have-answer
                       (not answer-right)
                       show-twice
                       (not @is-second))
                  [:button.button.is-info
                   {:on-click #(do (reset! first-answer nil)
                                   (reset! is-second true))} "重做此题"]
                  :else [:div])]])]])))

(def data
  [
   ;被试信息收集
   {:type   :collect
    :widget [collect]}
   ;前测知识 - 指导语
   {:type   :hint
    :widget [hint "欢迎参加心理学实验！"
             "同学你好，欢迎进入《二次根式》学习系统。<br><br>
              该系统的目的在于通过反馈学习来提高你对《二次根式》的掌握程度。<br><br>
              首先，为了测试你当前的知识水平，我们准备了10道题目。请认真作答。"
             "开始作答"
             #(rf/dispatch [:go-step 1])]}
   ;前测知识 - 10 道题目 TODO
   ;https://static2.mazhangjing.com/cyber/202209/69f036b7_图片.png
   ;反馈学习 - 练习指导语 KR+正确解答步骤 一次反馈
   {:type   :hint
    :widget [hint ""
             "接下来，正式进入反馈学习阶段。<br><br>
             这一阶段会有10道题目，每答完一道，会有相应的反馈。<br><br>
             若答错：会给与正确解答步骤。<br><br>
             若答对：只显示“回答正确”。<br><br>
             我们准备了一道练习题，帮助你熟悉学习系统。"
             "开始练习"
             #(rf/dispatch [:go-step 1])]}
   ;反馈学习 - 练习指导语 错误线索+正确解答步骤 一次反馈
   {:type   :hint
    :widget [hint ""
             "接下来，正式进入反馈学习阶段。<br><br>
             这一阶段会有10道题目，每答完一道，会有相应的反馈。<br><br>
             若答错：会给与错误线索和正确解答步骤。<br><br>
             若答对：只显示“回答正确”。<br><br>
             我们准备了一道练习题，帮助你熟悉学习系统。"
             "开始练习"
             #(rf/dispatch [:go-step 1])]}
   ;反馈学习 - 练习指导语 KR+正确解答步骤 二次反馈
   {:type   :hint
    :widget [hint ""
             "接下来，正式进入反馈学习阶段。<br><br>
             这一阶段会有10道题目，每答完一道，会有相应的反馈。<br><br>
             若答错：系统会告知正确与否并要求你再答一次，之后给予第二次反馈。<br><br>
             若答对：只显示“回答正确”。<br><br>
             我们准备了一道练习题，帮助你熟悉学习系统。"
             "开始练习"
             #(rf/dispatch [:go-step 1])]}
   ;反馈学习 - 练习指导语 错误线索+正确解答步骤 二次反馈
   {:type   :hint
    :widget [hint ""
             "接下来，正式进入反馈学习阶段。<br><br>
             这一阶段会有10道题目，每答完一道，会有相应的反馈。<br><br>
             若答错：系统会提供关于你可能错误的线索并要求你再答一次，之后给予第二次反馈。<br><br>
             若答对：只显示“回答正确”。<br><br>
             我们准备了一道练习题，帮助你熟悉学习系统。"
             "开始练习"
             #(rf/dispatch [:go-step 1])]}
   ;反馈学习 - 练习 TODO
   ;反馈学习 - 正式指导语
   {:type   :hint
    :widget [hint ""
             "练习部分结束，进入正式学习阶段。"
             "开始学习"
             #(rf/dispatch [:go-step 1])]}
   ;反馈学习 - 正式 TODO
   {:type   :problem
    :widget [problem #_{:body         "求解 2^32 的值。"
                        :answers      [100 200 300 400]
                        :right-answer 1
                        :hint         "应该这样想..."
                        :explain      "详细的步骤是这样的......"
                        :show-twice   false
                        :show-hint    true
                        :show-explain true}
             {:body                    "求解 2^32 的值。"
              :answers                 [100 200 300 400]
              :right-answer            1
              :hint                    "应该这样想..."
              :explain                 "详细的步骤是这样的......"
              :show-twice              true
              :show-twice-first-hint   true
              :show-twice-next-explain true}]}
   ;问卷和测验 - 指导语
   {:type   :hint
    :widget [hint ""
             "接下来进入问卷和知识测验阶段。"
             "开始作答"
             #(rf/dispatch [:go-step 1])]}
   ;问卷正文
   {:type   :questionnaire
    :widget [questionnaire-1 4 "后测问卷"]}
   ;后测正文 TODO
   ;数据上传
   {:type   :upload
    :widget [upload "上传数据" "正在上传数据，请勿关闭此页面！"]}])

(defn simple-question
  [id image right-answer]
  [:div {:style {:margin-top :10em
                 :background "rgb(249, 249, 249)"}}
   (if @is-debug [:div {:style {:position :absolute :right :20px :bottom :10px}}
                  [:span.is-clickable {:on-click #(rf/dispatch [:go-step -1])} "<  "]
                  [:span.is-clickable {:on-click #(rf/dispatch [:go-step 1])} "  >"]])
   (r/with-let
     [answer (r/atom nil)]
     [:div {:style {:text-align "center"}}
      [:img {:src image}]
      [:p.mt-4
       [:label.radio.mr-4 {:on-click #(reset! answer :A) :style {:font-size :2em}}
        [:input {:type "radio" :name (str "q" 1)
                 :checked (= :A @answer)
                 :style {:width :2em :height :2em}}] " A"]
       [:label.radio.mr-4
        {:on-click #(reset! answer :B) :style {:font-size :2em}}
        [:input {:type "radio" :name (str "q" 1)
                 :checked (= :B @answer)
                 :style {:width :2em :height :2em}}] " B"]
       [:label.radio.mr-4
        {:on-click #(reset! answer :C) :style {:font-size :2em}}
        [:input {:type "radio" :name (str "q" 1)
                 :checked (= :C @answer)
                 :style {:width :2em :height :2em}}] " C"]
       [:label.radio.mr-4
        {:on-click #(reset! answer :D) :style {:font-size :2em}}
        [:input {:type "radio" :name (str "q" 1)
                 :checked (= :D @answer)
                 :style {:width :2em :height :2em}}] " D"]]
      [:button.button.is-info.is-medium
       {:style {:min-width :10em :margin-top :5em}
        :on-click (fn [_]
                    (if (nil? @answer)
                      (js/alert "请选择选项后再继续！")
                      (do (rf/dispatch [:save-answer [id {:image image
                                                          :answer @answer
                                                          :right-answer right-answer}]])
                          (reset! answer nil)
                          (rf/dispatch [:go-step 1]))))}
       "下一题"]])])

(def fake-data
  [{:type   :collect
    :widget [simple-question (str "前测知识" 1)
             "https://static2.mazhangjing.com/cyber/202209/69f036b7_图片.png" :A]}
   {:type   :collect
    :widget [simple-question (str "前测知识" 2)
             "https://static2.mazhangjing.com/cyber/202209/0ccd2cea_图片.png" :B]}])

(defn root []
  [:div {:style {:position :absolute :top :0px :left :0px :right :0px :bottom :0px :background :#f9f9f9}}
   (r/with-let
     []
     (let [index @(rf/subscribe [:current-exp-index])]
       (-> fake-data (get index) :widget)))])

(rf/reg-event-db
  :go-to
  (fn [db [index]]
    (assoc db :current-exp-index index)))

(rf/reg-event-db
  :go-step
  (fn [db [_ step]]
    (let [now (or (:current-exp-index db) 0)
          result (+ now step)
          result (max 0 result)]
      (assoc db :current-exp-index result))))

(rf/reg-sub
  :current-exp-index
  (fn [db _]
    (or (:current-exp-index db) 0)))

(rf/reg-event-db
  :save-answer
  (fn [db [_ [key value]]]
    (assoc-in db [:psy-exp-answer key] value)))

(rf/reg-sub
  :get-answer
  (fn [db _]
    (:psy-exp-answer db)))

(rf/reg-event-db
  :clean-all-answer
  (fn [db _]
    (dissoc db :psy-exp-answer)))

(ajax-flow {:call           :psy-exp-data-upload
            :uri-fn         #(str "/cyber/dashboard/psych-data-upload")
            :is-post        true
            :data           :psy-exp-data-callback
            :clean          :psy-exp-data-clean
            :failure-notice true})