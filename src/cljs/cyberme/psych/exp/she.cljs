(ns cyberme.psych.exp.she
  (:require [cyberme.psych.widget :as w]
            [cyberme.psych.data :as d]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(def data
  (flatten
    [
     ;被试信息收集
     {:type   :collect
      :widget [w/collect]}
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
     (d/adapt1-questions)
     (d/adapt2-questions)
     (d/mentality-questions)
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
     ;反馈学习 - 练习指导语 错误线索+正确解答步骤 二次反馈
     {:type   :hint
      :widget [w/hint ""
               "接下来，正式进入反馈学习阶段。<br><br>
               这一阶段会有10道题目，每答完一道，会有相应的反馈。<br><br>
               若答错：系统会提供关于你可能错误的线索并要求你再答一次，之后给予第二次反馈。<br><br>
               若答对：只显示“回答正确”。<br><br>
               我们准备了一道练习题，帮助你熟悉学习系统。"
               "开始练习"
               w/go-next]}
     ;反馈学习 - 练习
     ;TODO
     ;反馈学习 - 正式指导语
     {:type   :hint
      :widget [w/hint ""
               "练习部分结束，进入正式学习阶段。"
               "开始学习"
               w/go-next]}
     ;反馈学习 - 正式
     ;TODO
     {:type   :problem
      :widget [w/problem #_{:body         "求解 2^32 的值。"
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
      :widget [w/upload "上传数据" "正在上传数据，请勿关闭此页面！"]}]))