(ns cyberme.psych.exp.guo
  (:require [cyberme.psych.widget :as w]
            [cyberme.psych.data :as d]
            [re-frame.core :as rf]
            [reagent.core :as r]))

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
;条件 4 左下提示
(def demo-cond1-step2-hint "https://static2.mazhangjing.com/cyber/202209/6fd81157_图片.png")
(def demo-cond2-step2-hint "https://static2.mazhangjing.com/cyber/202209/0039021c_图片.png")
(def demo-cond3-step2-hint "https://static2.mazhangjing.com/cyber/202209/b9b39eb8_图片.png")
;完成按钮提示
(def demo-hint-step3 "https://static2.mazhangjing.com/cyber/202209/67593e8f_图片.png")

(defn step23 []
  (let [data
        [[:q1 "https://static2.mazhangjing.com/cyber/202209/d4f5a420_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/eab2302f_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/4e2398db_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/a13882cb_图片.png" :D]
         [:q2 "https://static2.mazhangjing.com/cyber/202209/b9c5318a_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/b13e1f5d_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/d4def612_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/44df2f6a_图片.png" :C]
         [:q3 "https://static2.mazhangjing.com/cyber/202209/37a58f45_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/cab05d56_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/d12a23c9_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/e04484e9_图片.png" :D]
         [:q4 "https://static2.mazhangjing.com/cyber/202209/366ef18f_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/e71d996a_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/bd53b0b2_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/3316c8b5_图片.png" :A]
         [:q5 "https://static2.mazhangjing.com/cyber/202209/a9574b6a_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/2d4ec785_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/d8499e60_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/3c3ef32a_图片.png" :A]
         [:q6 "https://static2.mazhangjing.com/cyber/202209/57f3c98c_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/2acd6821_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/ebf06992_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/bcbfb74a_图片.png" :B]
         [:q7 "https://static2.mazhangjing.com/cyber/202209/4bb40621_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/96640b98_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/76fad390_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/ecd66eea_图片.png" :D]
         [:q8 "https://static2.mazhangjing.com/cyber/202209/0a4d4a09_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/6774eafd_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/10de7878_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/11c6fafd_图片.png" :C]
         [:q9 "https://static2.mazhangjing.com/cyber/202209/de6aab47_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/9e7123ec_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/e226c198_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/478ceea5_图片.png" :D]
         [:q10 "https://static2.mazhangjing.com/cyber/202209/7a725f77_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/728d442c_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/b8a6c244_图片.png"
          "https://static2.mazhangjing.com/cyber/202209/42f53340_图片.png" :A]]]
    (if @w/is-debug (vec (take 2 data)) data)))

(defn problem-demo [exp-cond]
  (if-not (= exp-cond 4)
    {:type   :problem-demo
     :widget [w/problem-guo
              {:step-1          demo-step1
               :right-answer    :B
               :step-2          (case exp-cond
                                  1 demo-cond1-step2
                                  2 demo-cond2-step2
                                  3 demo-cond3-step2
                                  demo-cond1-step2)
               :exp-cond        exp-cond
               :is-demo         true
               :demo-step2-hint demo-hint-step2
               :demo-step3-hint demo-hint-step3}]}
    {:type   :problem-demo
     :widget [w/problem-cond4-guo
              {:step-1                demo-step1
               :right-answer          :B
               :step2-each            [demo-cond1-step2 demo-cond2-step2 demo-cond3-step2]
               :step2-each-hint       [demo-cond1-step2-hint demo-cond2-step2-hint demo-cond3-step2-hint]
               :is-demo               true
               :demo-step2-hint-left  demo-hint-step2-left
               :demo-step2-hint-right demo-hint-step2-right
               :demo-step3-hint       demo-hint-step3}]}))

(defn problem [exp-cond [id step-1 step2cond1 step2cond2 step2cond3 right-answer]]
  (if-not (= 4 exp-cond)
    {:type   :problem
     :widget [w/problem-guo
              {:id           id
               :step-1       step-1
               :right-answer right-answer
               :step-2       (case exp-cond
                               1 step2cond1
                               2 step2cond2
                               3 step2cond3
                               step2cond1)
               :exp-cond     exp-cond}]}
    {:type   :problem
     :widget [w/problem-cond4-guo
              {:id           id
               :step-1       step-1
               :right-answer right-answer
               :step2-each   [step2cond1 step2cond2 step2cond3]}]}))

(defn wrap-header [origin index]
  {:type :wrapped
   :widget [:<> [w/top-info ["1.学习前调查" "2.基础水平测验" "3.正式反馈学习"
                             "4.学习后调查" "5.学习后测验"] (- index 1)]
            (-> origin :widget)]})

(defn data []
  (let [skip-front (= "true" (w/get-config :skip-front))
        exp-cond (case (w/get-config :exp-cond) "1" 1 "2" 2 "3" 3 "4" 4 1)]
    (filterv
      (comp not nil?)
      (flatten
        [;被试信息收集
         {:type :intro
          :widget
          [w/image "https://static2.mazhangjing.com/cyber/202210/48432fdb_图片.png"]}
         (wrap-header {:type :collect :widget [w/collect-guo]} 1)
         (when-not skip-front                               ;允许跳过前测量表和前测知识
           [;兴趣量表
            (wrap-header (d/interest-questions) 1)
            ;元认知量表
            (wrap-header (d/meta-cong-questions) 1)
            ;前测知识指导语
            {:type :intro
             :widget
             [w/image "https://static2.mazhangjing.com/cyber/202210/b0d840ac_图片.png"]}
            ;前测十道题
            (mapv #(wrap-header % 2) (d/front-questions))])
         ;情绪前测、练习和正文指导语
         {:type :intro
          :widget
          [w/image "https://static2.mazhangjing.com/cyber/202210/7fafd7da_图片.png"]}
         ;前测情绪列表
         (wrap-header (d/emotion-questions-guo) 3)
         ;练习展示
         (wrap-header {:type   :hint
                       :widget [w/hint-jiang ""
                                "这是一个练习题目，不记录正误，帮助你了解作答的流程。" "开始练习" w/go-next]}
                      3)
         (wrap-header (problem-demo exp-cond) 3)
         ;正文指导语
         (wrap-header {:type   :hint
                       :widget [w/hint-jiang "" "下面开始正式反馈学习。" "开始学习"
                                #(do (w/go-next)
                                     (rf/dispatch [:save-answer ["开始时间" (.getTime (js/Date.))]]))]}
                      3)
         ;学习展示
         (mapv #(wrap-header % 3) (mapv #(problem exp-cond %) (step23)))
         ;后测指导语
         {:type :intro
          :widget
          [w/image "https://static2.mazhangjing.com/cyber/202210/5a80c004_图片.png"]}
         ;后测情绪量表
         (wrap-header (d/emotion-questions-guo-2) 4)
         ;后测控制感量表
         (wrap-header (d/control-questions) 4)
         ;后测自主感量表
         (wrap-header (d/liberty-questions) 4)
         ;后测动机量表
         (wrap-header (d/motivation-questions-guo) 4)
         ;认知负荷量表
         (wrap-header (d/control-questions) 4)
         ;迁移表现指导语
         {:type :intro
          :widget
          [w/image "https://static2.mazhangjing.com/cyber/202210/399cb8e4_图片.png"]}
         ;后测十道题
         (mapv #(wrap-header % 5) (d/back-questions))
         ;上传数据页面
         {:type   :upload
          :widget [w/upload "学习和实验结束！" "感谢你的认真学习~"]}]))))