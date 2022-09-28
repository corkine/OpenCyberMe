(ns cyberme.psych.exp.jiang
  (:require [cyberme.psych.widget :as w]
            [cyberme.psych.data :as d]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn problem-jiang
  "展示题目，被试做出选择根据实验条件给与提示
  如果回答正确，只显示回答正确文字和下一题按钮。
  如果回答错误：
    实验条件 1 简单提示 feedback + 再答一次 -> 额外出现详尽提示 explain
    实验条件 2 正确、错误说明 + 再答一次 -> 额外出现详尽提示 explain
    实验条件 3 简单提示 feedback + 详尽提示 explain
    实验条件 4 正确、错误说明 + 详尽提示 explain"
  [{:keys [id subject-url right-answer exp-cond is-demo
           feedback-a feedback-b feedback-c feedback-d explain]}]
  (r/with-let
    [answer (r/atom nil)
     is-second (r/atom false)
     first-answer (r/atom nil)]
    (let [in-cond-12 (or (= exp-cond 1) (= exp-cond 2))
          have-answer? (not (nil? @answer))
          answer-right? (= right-answer @answer)
          is-second? @is-second
          second-wrong? (and is-second? have-answer? (not answer-right?))
          after-first-click? (or is-second? have-answer?)
          freeze-choose? (if is-demo false have-answer?)
          show-details? (and is-second? have-answer?)
          need-try-again? (and in-cond-12
                               have-answer?
                               (not answer-right?)
                               (not is-second?))
          can-go? (or (and in-cond-12
                           (or (and have-answer? answer-right?)
                               (and have-answer? second-wrong?)))
                      (and (not in-cond-12)
                           have-answer?))]
      [:div {:style {:margin "10% auto"}}
       [:div {:style {:text-align "center"}}
        [:img {:src subject-url :style {:max-width :50em}}]
        [:p.mt-4
         [:label.radio.mr-4 {:style {:font-size :2em}}
          [:input {:type      "radio" :name (str "q" 1)
                   :checked   (= :A @answer)
                   :disabled freeze-choose?
                   :on-change #(do (reset! answer :A)
                                   (when-not is-second? (reset! first-answer :A)))
                   :style     {:width :2em :height :2em}}] " A"]
         [:label.radio.mr-4 {:style {:font-size :2em}}
          [:input {:type      "radio" :name (str "q" 1)
                   :checked   (= :B @answer)
                   :disabled freeze-choose?
                   :on-change #(do (reset! answer :B)
                                   (when-not is-second? (reset! first-answer :B)))
                   :style     {:width :2em :height :2em}}] " B"]
         [:label.radio.mr-4 {:style {:font-size :2em}}
          [:input {:type      "radio" :name (str "q" 1)
                   :checked   (= :C @answer)
                   :disabled freeze-choose?
                   :on-change #(do (reset! answer :C)
                                   (when-not is-second? (reset! first-answer :C)))
                   :style     {:width :2em :height :2em}}] " C"]
         [:label.radio.mr-4 {:style {:font-size :2em}}
          [:input {:type      "radio" :name (str "q" 1)
                   :checked   (= :D @answer)
                   :disabled freeze-choose?
                   :on-change #(do (reset! answer :D)
                                   (when-not is-second? (reset! first-answer :D)))
                   :style     {:width :2em :height :2em}}] " D"]]
        (when after-first-click?
          [:div.mt-4
           (if is-demo
             [:p {:style {:font-size           "1.3em"
                          :background-color    "#ffffa1"
                          :padding             "15px"
                          :border              "2px black"
                          :border-bottom-style "dashed"
                          :max-width           "40em"
                          :margin              "auto"}} "↑↑↑ 请依次点击 A、B、C、D 四个按钮，感受
             不同作答选项时的效果。 ↑↑↑"])
           (if answer-right?
             [:div.mt-3.has-text-success {:style {:font-size "1.3em"}} "回答正确"]
             [:div.mt-3.has-text-danger {:style {:font-size "1.3em"}} "回答错误"])
           (when-not answer-right?
             (case exp-cond
               1 [:div.is-flex.is-justify-content-center.is-flex-direction-column
                  [:p {:style {:font-size "1.3em"}} "以下是你可能出错的原因，请尝试纠正并再次作答。"]
                  [:img {:src   (let [ans @first-answer]
                                  (cond (= :A ans) feedback-a (= :B ans) feedback-b
                                        (= :C ans) feedback-c (= :D ans) feedback-d))
                         :style {:margin-top "0px" :max-width :50em :align-self "center"}}]
                  (when show-details?
                    [:img {:src   explain
                           :style {:margin-top "15px" :max-width :50em :align-self "center"}}])]
               2 [:div
                  (when show-details?
                    [:img {:src   explain
                           :style {:margin-top "0px" :max-width :50em :align-self "center"}}])]
               3 [:div.is-flex.is-justify-content-center.is-flex-direction-column
                  [:img {:src   (let [ans @answer]
                                  (cond (= :A ans) feedback-a (= :B ans) feedback-b
                                        (= :C ans) feedback-c (= :D ans) feedback-d))
                         :style {:margin-top "0px" :max-width :50em :align-self "center"}}]
                  [:img {:src   explain
                         :style {:margin-top "15px" :max-width :50em :align-self "center"}}]]
               4 [:div
                  [:img {:src   explain
                         :style {:margin-top "0px" :max-width :50em :align-self "center"}}]]))
           [:div {:style {:display "flex" :flex-direction "column" :margin-top "50px"}}
            ;当为实验条件 1 和 2 时，且被试回答过一次后，且回答错误时展示再答一次按钮
            ;此按钮当被试第二次回答后消失
            (when need-try-again?
              [:button.button.is-info.is-large
               {:style    {:align-self :center :max-width :25em}
                :on-click #(do
                             (reset! first-answer @answer)
                             (reset! answer nil)
                             (reset! is-second true))} "再答一次"])
            ;当被试在任意条件下回答完毕且正确，或处于实验 1 和 2 条件时第二次回答错误
            (when can-go?
              [:button.button.is-info.is-large
               {:style    {:align-self :center :max-width :25em}
                :on-click #(do
                             (when-not is-demo
                               (rf/dispatch [:save-answer
                                             [id {:right-answer right-answer
                                                  :user-answer  @answer
                                                  :user-first-answer @first-answer
                                                  :exp-cond     exp-cond
                                                  :record-time  (.getTime (js/Date.))}]]))
                             (reset! answer nil)
                             (reset! is-second false)
                             (reset! first-answer nil)
                             (w/go-next))} "下一题"])]])]])))

(defn problem-data []
  (let [data                                                ;问题 ID、题干、选项 A - D 提示、正确答案、详细解释
        [[:q1
          "https://static2.mazhangjing.com/cyber/202209/920a2d73_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/6fd91477_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/cc8187ec_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/7cff4d57_图片.png"
          ""
          :D
          "https://static2.mazhangjing.com/cyber/202209/d0b9bfe3_图片.png"]
         [:q2
          "https://static2.mazhangjing.com/cyber/202209/c9401387_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/d3a56c29_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/0e5c020c_图片.png"
          ""
          "https://static2.mazhangjing.com/cyber/202209/0b1448b0_图片.png"
          :C
          "https://static2.mazhangjing.com/cyber/202209/331c7862_图片.png"]
         [:q3
          "https://static2.mazhangjing.com/cyber/202209/60da217e_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/664da060_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/664da060_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/664da060_图片.png"
          ""
          :D
          "https://static2.mazhangjing.com/cyber/202209/2f5524f8_图片.png"]
         [:q4
          "https://static2.mazhangjing.com/cyber/202209/2d1bac16_图片.png"
          ""
          "https://static2.mazhangjing.com/cyber/202209/8b0084ec_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/6a0cc2b3_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/1f919807_图片.png"
          :A
          "https://static2.mazhangjing.com/cyber/202209/8d3c3d55_图片.png"]
         [:q5
          "https://static2.mazhangjing.com/cyber/202209/9a072bd3_图片.png"
          ""
          "https://static2.mazhangjing.com/cyber/202209/7f8d22dc_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/6adeb132_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/f4633427_图片.png"
          :A
          "https://static2.mazhangjing.com/cyber/202209/f22f967e_图片.png"]
         [:q6
          "https://static2.mazhangjing.com/cyber/202209/2b0dae53_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/1ed5d881_图片.png"
          ""
          "https://static2.mazhangjing.com/cyber/202209/d1b5a1de_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/cbff2481_图片.png"
          :B
          "https://static2.mazhangjing.com/cyber/202209/c0897262_图片.png"]
         [:q7
          "https://static2.mazhangjing.com/cyber/202209/ae3dbd3c_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/b48e46b3_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/069831dd_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/f29b9940_图片.png"
          ""
          :D
          "https://static2.mazhangjing.com/cyber/202209/598c2a1b_图片.png"]
         [:q8
          "https://static2.mazhangjing.com/cyber/202209/9008bdb7_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/565b3c42_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/3741358c_图片.png"
          ""
          "https://static2.mazhangjing.com/cyber/202209/cfcae2f5_图片.png"
          :D
          "https://static2.mazhangjing.com/cyber/202209/b60483e0_图片.png"]
         [:q9
          "https://static2.mazhangjing.com/cyber/202209/d657a93e_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/8af9fc97_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/35f35895_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/35f35895_图片.png"
          ""
          :D
          "https://static2.mazhangjing.com/cyber/202209/98d4aded_图片.png"]
         [:q10
          "https://static2.mazhangjing.com/cyber/202209/64024208_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/7190ab09_图片.png"
          ""
          "https://static2.mazhangjing.com/cyber/202209/bafcd4d3_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/bafcd4d3_图片.png"
          :B
          "https://static2.mazhangjing.com/cyber/202209/0d984652_图片.png"]]]
    (if @w/is-debug (vec (take 2 data)) data)))

(defn problem-demo [exp-cond]
  [{:type   :problem-demo
    :widget [problem-jiang
             {:id           "demo"
              :is-demo      true
              :exp-cond     exp-cond
              :subject-url  "https://static2.mazhangjing.com/cyber/202209/bc23d80a_图片.png"
              :right-answer :C
              :feedback-a   "https://static2.mazhangjing.com/cyber/202209/fb4bd730_图片.png"
              :feedback-b   "https://static2.mazhangjing.com/cyber/202209/3804e62d_图片.png"
              :feedback-d   "https://static2.mazhangjing.com/cyber/202209/dc4d565f_图片.png"
              :explain      "https://static2.mazhangjing.com/cyber/202209/ff7f7af9_图片.png"}]}])

(defn problem [exp-cond [id subject feed-a feed-b feed-c feed-d right-ans explain]]
  [{:type   :problem
    :widget [problem-jiang
             {:id           id
              :is-demo      false
              :exp-cond     exp-cond
              :subject-url  subject
              :right-answer right-ans
              :feedback-a   feed-a
              :feedback-b   feed-b
              :feedback-c   feed-c
              :feedback-d   feed-d
              :explain      explain}]}])

(defn data []
  (let [is-exp-2? (= "true" (w/get-config :exp-2))
        exp-cond (case (w/get-config :exp-cond) "1" 1 "2" 2 "3" 3 "4" 4 1)]
    (filterv
      (comp not nil?)
      (flatten
        [;被试信息收集
         {:type   :collect
          :widget [w/collect-guo]}
         ;前测知识 - 指导语
         {:type   :hint
          :widget [w/hint "欢迎参加心理学实验！"
                   "同学你好，欢迎进入《二次根式》学习系统。<br><br>
                    该系统的目的在于通过反馈学习来提高你对《二次根式》的掌握程度。<br><br>
                    首先，为了测试你当前的知识水平，我们准备了10道题目。请认真作答。"
                   "开始作答"
                   w/go-next]}
         ;前测知识 - 10 道题目
         (d/front-questions)
         ;实验二前测问卷
         (when is-exp-2?
           [(d/adapt1-questions)
            (d/adapt2-questions)
            (d/mentality-questions)])
         ;练习指导语
         (cond (= exp-cond 4)
               ;反馈学习 - 练习指导语 KR+正确解答步骤 一次反馈
               {:type   :hint
                :widget [w/hint ""
                         "接下来，正式进入反馈学习阶段。<br><br>
                         这一阶段会有10道题目，每答完一道，会有相应的反馈。<br><br>
                         若答错：会给与正确解答步骤。<br><br>
                         若答对：只显示“回答正确”。<br><br>
                         我们准备了一道练习题，帮助你熟悉学习系统。"
                         "开始练习"
                         w/go-next]}
               (= exp-cond 3)
               ;反馈学习 - 练习指导语 错误线索+正确解答步骤 一次反馈
               {:type   :hint
                :widget [w/hint ""
                         "接下来，正式进入反馈学习阶段。<br><br>
                         这一阶段会有10道题目，每答完一道，会有相应的反馈。<br><br>
                         若答错：会给与错误线索和正确解答步骤。<br><br>
                         若答对：只显示“回答正确”。<br><br>
                         我们准备了一道练习题，帮助你熟悉学习系统。"
                         "开始练习"
                         w/go-next]}
               (= exp-cond 2)
               ;反馈学习 - 练习指导语 KR+正确解答步骤 二次反馈
               {:type   :hint
                :widget [w/hint ""
                         "接下来，正式进入反馈学习阶段。<br><br>
                         这一阶段会有10道题目，每答完一道，会有相应的反馈。<br><br>
                         若答错：系统会告知正确与否并要求你再答一次，之后给予第二次反馈。<br><br>
                         若答对：只显示“回答正确”。<br><br>
                         我们准备了一道练习题，帮助你熟悉学习系统。"
                         "开始练习"
                         w/go-next]}
               (= exp-cond 1)
               ;反馈学习 - 练习指导语 错误线索+正确解答步骤 二次反馈
               {:type   :hint
                :widget [w/hint ""
                         "接下来，正式进入反馈学习阶段。<br><br>
                         这一阶段会有10道题目，每答完一道，会有相应的反馈。<br><br>
                         若答错：系统会提供关于你可能错误的线索并要求你再答一次，之后给予第二次反馈。<br><br>
                         若答对：只显示“回答正确”。<br><br>
                         我们准备了一道练习题，帮助你熟悉学习系统。"
                         "开始练习"
                         w/go-next]})
         ;练习开始
         (problem-demo exp-cond)
         ;反馈学习 - 正式指导语
         {:type   :hint
          :widget [w/hint ""
                   "练习部分结束，进入正式学习阶段。"
                   "开始学习"
                   w/go-next]}
         ;反馈学习 - 正式
         (mapv #(problem exp-cond %) (problem-data))
         ;问卷和测验 - 指导语
         {:type   :hint
          :widget [w/hint ""
                   "接下来进入问卷和知识测验阶段。"
                   "开始作答"
                   w/go-next]}
         ;问卷正文 - 动机、情绪、认知负荷
         (d/motivation-questions)
         (d/emotion-questions)
         (d/cong-questions)
         ;后测正文
         (d/back-questions)
         ;数据上传
         {:type   :upload
          :widget [w/upload "上传数据" "正在上传数据，请勿关闭此页面！"]}]))))