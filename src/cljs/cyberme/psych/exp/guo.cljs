(ns cyberme.psych.exp.guo
  (:require [cyberme.psych.widget :as w]
            [cyberme.psych.data :as d]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn data-1 []                                             ;TODO 允许通过参数直接跳到前测情绪量表
  (flatten
    [;被试信息收集
     {:type :collect :widget [w/collect-guo]}
     ;兴趣量表
     (d/interest-questions)
     ;元认知量表
     (d/meta-cong-questions)
     ;前测知识指导语
     {:type   :hint
      :widget [w/hint ""
               "接下来我们要完成10道《二次根式》的选择题，以了解你在《二次根式》方面的基础水平如何。"
               "开始作答"
               w/go-next]}
     ;前测十道题
     (d/front-questions)
     ;前测情绪列表
     (d/emotion-questions-guo)
     ;练习指导语
     {:type   :hint
      :widget [w/hint ""
               "这是一个练习题目，不记录正误，帮助你了解作答的流程。"
               "开始练习"
               w/go-next]}
     ;TODO
     ;后测情绪量表
     (d/emotion-questions-guo-2)
     ;后测控制感量表
     (d/control-questions)
     ;后测自主感量表
     (d/liberty-questions)
     ;后测动机量表
     (d/motivation-questions-guo)
     ;认知负荷量表
     (d/control-questions)
     ;迁移表现指导语
     {:type   :hint
      :widget [w/hint ""
               "最后我们再做10道选择题，测试一下你在刚刚的学习中进步了多少。"
               "开始作答"
               w/go-next]}
     ;后测十道题
     (d/back-questions)
     ;上传数据页面
     {:type   :upload
      :widget [w/upload "上传数据" "正在上传数据，请勿关闭此页面！"]}]))

;条件 1-4 问题 + 提示
(def demo-step1 "https://static2.mazhangjing.com/cyber/202209/d2bf98aa_图片.png")
;条件 1-3 答案
(def demo-cond1-step2 "https://static2.mazhangjing.com/cyber/202209/23f75009_图片.png")
(def demo-cond2-step2 "https://static2.mazhangjing.com/cyber/202209/9ef02917_图片.png")
(def demo-cond3-step2 "https://static2.mazhangjing.com/cyber/202209/1a133193_图片.png")
;条件 1-3 答案提示
(def demo-hint-step2 "https://static2.mazhangjing.com/cyber/202209/c4f53267_图片.png")
;条件 4 左右提示
(def demo-hint-step2-left "https://static2.mazhangjing.com/cyber/202209/c3c8f495_图片.png")
(def demo-hint-step2-right "https://static2.mazhangjing.com/cyber/202209/b6d237ab_图片.png")
;完成按钮提示
(def demo-hint-step3 "https://static2.mazhangjing.com/cyber/202209/67593e8f_图片.png")

(def step23
  [["![](https://static2.mazhangjing.com/cyber/202209/d4f5a420_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/eab2302f_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/4e2398db_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/a13882cb_图片.png)"]
   ["![](https://static2.mazhangjing.com/cyber/202209/b9c5318a_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/b13e1f5d_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/d4def612_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/44df2f6a_图片.png)"]
   ["![](https://static2.mazhangjing.com/cyber/202209/37a58f45_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/cab05d56_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/d12a23c9_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/e04484e9_图片.png)"]
   ["![](https://static2.mazhangjing.com/cyber/202209/366ef18f_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/e71d996a_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/bd53b0b2_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/3316c8b5_图片.png)"]
   ["![](https://static2.mazhangjing.com/cyber/202209/a9574b6a_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/2d4ec785_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/d8499e60_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/3c3ef32a_图片.png)"]
   ["![](https://static2.mazhangjing.com/cyber/202209/57f3c98c_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/2acd6821_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/ebf06992_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/bcbfb74a_图片.png)"]
   ["![](https://static2.mazhangjing.com/cyber/202209/4bb40621_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/96640b98_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/76fad390_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/ecd66eea_图片.png)"]
   ["![](https://static2.mazhangjing.com/cyber/202209/0a4d4a09_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/6774eafd_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/10de7878_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/11c6fafd_图片.png)"]
   ["![](https://static2.mazhangjing.com/cyber/202209/de6aab47_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/9e7123ec_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/e226c198_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/478ceea5_图片.png)"]
   ["![](https://static2.mazhangjing.com/cyber/202209/7a725f77_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/728d442c_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/b8a6c244_图片.png)"
    "![](https://static2.mazhangjing.com/cyber/202209/42f53340_图片.png)"]])

(defn data []
  [{:type   :problem
    :widget [w/problem-cond4-guo
             {:step-1                demo-step1
              :right-answer          :B
              :step2-each            [demo-cond1-step2 demo-cond2-step2 demo-cond3-step2]
              :is-demo               true
              :demo-step2-hint-left  demo-hint-step2-left
              :demo-step2-hint-right demo-hint-step2-right
              :demo-step3-hint       demo-hint-step3}]
    #_[w/problem-guo
       {:step-1          demo-step1
        :right-answer    :B
        :step-2          demo-cond2-step2
        :exp-cond        2
        :is-demo         true
        :demo-step2-hint demo-hint-step2
        :demo-step3-hint demo-hint-step3}]
    #_[w/problem-guo
       {:step-1       demo-step1
        :right-answer :B
        :step-2       demo-cond2-step2
        :exp-cond     2}]
    #_[w/problem-cond4-guo
       {:step-1       demo-step1
        :right-answer :B
        :step2-each   [demo-cond1-step2 demo-cond2-step2 demo-cond3-step2]}]}
   {:type   :hint
    :widget [w/hint ""
             "接下来我们要完成10道《二次根式》的选择题，以了解你在《二次根式》方面的基础水平如何。"
             "开始作答"
             w/go-next]}
   {:type   :1
    :widget [:p "HE"]}])