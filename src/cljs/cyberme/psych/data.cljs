(ns cyberme.psych.data
  (:require [cyberme.psych.widget :refer
             [collect hint problem questionnaire upload] :as w]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn front-questions []
  (mapv
    (fn [[id url right]]
      {:type   :knowledge
       :widget [w/simple-question (str "前测知识" id)
                (second (re-find #"!\[\]\((.*?)\)" url)) right]})
    [[1 "![](https://static2.mazhangjing.com/cyber/202209/016fdea5_图片.png)" :A]
     [2 "![](https://static2.mazhangjing.com/cyber/202209/0824dc8b_图片.png)" :D]
     [3 "![](https://static2.mazhangjing.com/cyber/202209/3495fa4c_图片.png)" :A]
     [4 "![](https://static2.mazhangjing.com/cyber/202209/a3e19dd2_图片.png)" :B]
     [5 "![](https://static2.mazhangjing.com/cyber/202209/cc853f27_图片.png)" :C]
     [6 "![](https://static2.mazhangjing.com/cyber/202209/f9debc7b_图片.png)" :D]
     [7 "![](https://static2.mazhangjing.com/cyber/202209/e7e3b888_图片.png)" :B]
     [8 "![](https://static2.mazhangjing.com/cyber/202209/5357057f_图片.png)" :D]
     [9 "![](https://static2.mazhangjing.com/cyber/202209/24ec77c6_图片.png)" :C]
     [10 "![](https://static2.mazhangjing.com/cyber/202209/017ba4b1_图片.png)" :A]]))

(defn back-questions []
  (mapv
    (fn [[id url right]]
      {:type   :knowledge
       :widget [w/simple-question (str "后测知识" id)
                (second (re-find #"!\[\]\((.*?)\)" url)) right]})
    [[1 "![](https://static2.mazhangjing.com/cyber/202209/47601869_图片.png)" :A]
     [2 "![](https://static2.mazhangjing.com/cyber/202209/bb60e394_图片.png)" :B]
     [3 "![](https://static2.mazhangjing.com/cyber/202209/836c5026_图片.png)" :A]
     [4 "![](https://static2.mazhangjing.com/cyber/202209/58ec2289_图片.png)" :D]
     [5 "![](https://static2.mazhangjing.com/cyber/202209/e039b34e_图片.png)" :D]
     [6 "![](https://static2.mazhangjing.com/cyber/202209/398ceff0_图片.png)" :C]
     [7 "![](https://static2.mazhangjing.com/cyber/202209/47ee7600_图片.png)" :A]
     [8 "![](https://static2.mazhangjing.com/cyber/202209/577e3809_图片.png)" :B]
     [9 "![](https://static2.mazhangjing.com/cyber/202209/b88099a3_图片.png)" :A]
     [10 "![](https://static2.mazhangjing.com/cyber/202209/ea10e3e6_图片.png)" :D]]))

(defn emotion-questions []
  {:type   :question
   :widget [questionnaire
            {:id        "情绪问卷"
             :leading   "当我在学习测验中收到这种反馈时，我感到：(0 完全不同意，10 完全同意)"
             :questions ["1.满意的" "2.自信的" "3.有成就感的"
                         "4.困惑的" "5.愤怒的" "6.沮丧的"]
             :content-style {:padding-right "5em"}
             :answers [0 1 2 3 4 5 6 7 8 9 10]}]})

(defn emotion-questions-guo []
  {:type   :question
   :widget [questionnaire
            {:id        "情绪问卷-前测"
             :leading   "请根据你此时此刻的情绪状态，选择符合你的选项。1代表完全不符合，5代表完全符合。"
             :questions ["1.困惑" "2.无聊" "3.沮丧" "4.愉快" "5.满意"]
             :content-style {:padding-right "5em"}
             :answers ["完全不符合" "比较不符合" "不确定" "比较符合" "完全符合"]}]})

(defn emotion-questions-guo-2 []
  {:type   :question
   :widget [questionnaire
            {:id        "情绪问卷-后测"
             :leading   "请根据你此时此刻的情绪状态，选择符合你的选项。1代表完全不符合，5代表完全符合。"
             :questions ["1.困惑" "2.无聊" "3.沮丧" "4.愉快" "5.满意"]
             :content-style {:padding-right "5em"}
             :answers ["完全不符合" "比较不符合" "不确定" "比较符合" "完全符合"]}]})

(defn control-questions []
  {:type   :question
   :widget [questionnaire
            {:id        "控制感问卷"
             :leading   "请根据你刚刚学习过程中的感受，仔细阅读每一项，然后根据自己的实际情况，选择相应的选项。"
             :questions ["1.我认为我可以在很大程度上控制自己的学业成绩"
                         "2.我在学习上付出的努力越多，我的学习成绩就会越好。"
                         "3.不管我做什么，我的成绩就是提高不了。"
                         "4.在学校生涯中，我认为自己应该为学业成绩负最大的责任。"
                         "5.我的学习成绩怎么样大都靠运气。"
                         "6.对于我在学校的学业成绩，我认为自己不能做什么。"
                         "7.当我成绩不好的时候，通常都是因为我自己在学习上没有足够努力。"
                         "8.我的学习成绩通常都是由我所不能控制的因素决定，我很少能够去改变它。"]
             :answers ["完全不同意" "比较不同意" "不确定" "比较同意" "完全同意"]}]})

(defn liberty-questions []
  {:type   :question
   :widget [questionnaire
            {:id        "自主感问卷"
             :leading   "请根据你刚刚学习过程中的感受，仔细阅读每一项，然后根据自己的实际情况，选择相应的选项。"
             :questions ["1.我可以自己决定我在学习过程中执行哪些操作。"
                         "2.我在学习过程中有作出选择的自由。"
                         "3.我可以自己决定我在学习过程中做什么。"
                         "4.回想起来，我认为参与（这次学习）挺好的。"
                         "5.这对我来说是一次有价值的经历。"
                         "6.这是一次值得进行的学习。"]
             :answers ["完全不同意" "比较不同意" "不确定" "比较同意" "完全同意"]}]})

(defn motivation-questions []
  {:type   :question
   :widget [questionnaire
            {:id      "情境学习动机问卷"
             :leading "请对以下题目中的说法,选出最符合你实际情况的数字。"
             :answers ["极不符合" "不符合" "不确定" "符合" "完全符合"]
             :questions
             ["1.我觉得刚才的学习很容易。"
              "2.我非常喜欢通过这个系统来学习。"
              "3.在我阅读了这个学习系统的介绍后，我很清楚自己将要学习什么。"
              "4.完成刚才的学习任务让我很有成就感。"
              "5.通过刚才的学习,我有信心在后面学习中取得好成绩。"
              "6.能够成功地完成这次学习任务让我感觉不错。"
              "7.通过刚才的学习，我有信心能掌握所学内容。"
              "8.我喜欢这个学习系统中的内容，我想知道更多与这些内容有关的知识。"
              "9.这个学习系统中的内容很好，让我有信心学好这个材料。"
              "10.系统中给予的解释或反馈语言让我感到这是对自己努力的奖励。"
              "11.这个系统设计得很好，我很乐意使用它。"]}]})

(defn motivation-questions-guo []
  {:type   :question
   :widget [questionnaire
            {:id      "动机问卷"
             :leading "请根据你刚刚学习过程中的感受，仔细阅读每一项，然后根据自己的实际情况，选择相应的选项。"
             :answers ["极不符合" "不符合" "不确定" "符合" "完全符合"]
             :questions
             ["1.它激发了我的好奇心。"
              "2.它令人感兴趣。"
              "3.它很有趣。"
              "4.我想继续研究它。"
              "5.它让我感到好奇。"
              "6.它令人愉悦。"
              "7.这让我想进一步探索它。"
              "8.我愿意回来继续参加与它相关的研究。"]}]})

(defn cong-questions []
  {:type   :question
   :widget [w/cong-questionnaire
            {:id      "认知负荷问卷"
             :leading "请针对刚才的学习内容，从以下9个数字中选择你认为合适的数字:"
             :questions
             ["1.你认为刚才的学习内容难度如何?"
              "2.在刚才的学习过程中，你投入了多少努力?"
              "3.你认为利用这套学习系统学习起来是否方便?"]
             :answers [1 2 3 4 5 6 7 8 9]}]})

(defn meta-cong-questions []
  {:type   :question
   :widget [w/questionnaire
            {:id      "元认知问卷"
             :leading "下面是你在学习数学时可能出现的一些做法或想法。请你根据自己的实际情况比较你与这些做法或想法之间的相像程度，并选择最适合你的数字选项。"
             :questions
             ["1．做数学题时，我喜欢先做容易的，再做稍难的，最后做难题。"
              "2.证明数学问题时，我一般是先将条件和结论用明确的数学符号表示出来，然后再寻找沟通条件和结论的桥梁。"
              "3．数学学习中，老师怎么说我就怎么做"
              "4.在解答数学问题的过程中，我对自己采取的解题方法的有效性是心中有数的."
              "5.在解答数学问题的过程中，我会经常问自己：“这一解题方法正确吗？”"
              "6.在解答数学问题的过程中，我能清晰地感觉到自己所采用的解题方法的优劣程度."
              "7.在解答数学问题的过程中，我经常提醒自己要注意问题的关键。"
              "8.如果对某一个数学概念不理解，我会分析一下概念的一个实际例子."
              "9.在解答数学问题的过程中，我会问自己：“要获得结论，现在还缺少哪些条件？”"
              "10．在分析题意时，我会问自己：“哪些知识是与本题有关系的？”"
              "11．如果感到用一种方法难以理解某一数学内容时，我会尝试换一种方法来学习它."
              "12．如果解题发生困难，我就考虑这个问题的特例或最简单的情况．"
              "13．学完某一数学知识后，我没有想过要用自己的语言来表述它。"
              "14．看数学书时，我读完一段就用自己的语言重新叙述一下所读的内容."
              "15．学习了数学概念以后，我的脑子里就会有一些概念的具体例子．"
              "16．学完一节数学课后，我会写出这节课的重点内容，并与课本进行对照，以确定自己是否已经理解相应的内容."
              "17．如果解不出某个数学题目，我一般不会怀疑题目本身的错误。"
              "18．学完新的数学知识后，我以用它来解决一些相关问题的方法检验自己是否已经理解所学内容."
              "19.解完数学题后，我会想：“还有更好的解法吗?”"
              "20.解完数学题后，我一般不去总结解题的关键"
              "21.解完数学题后，我会考虑：“这个解题方法能够用来解决类似的问题吗?”"
              "22.解完数学题后，我会问自己：“这个问题能够进行推广吗?”"
              "23.解完数学题后，我一般会采用另一种解题方法来检验答案的正确性."]
             :answers ["从不这样" "很少这样" "有时这样" "经常这样" "总是这样"]
             :word-no-break true}]})

(defn adapt1-questions []
  {:type   :question
   :widget [questionnaire
            {:id      "对错误反应的适应性问卷1"
             :leading "对错误反应的情感-动机适应性（从 1 非常不同意到 7 非常同意）"
             :questions
             ["1.就我而言，当我在数学课上说错话时，这门课就被毁了。"
              "2.当我在数学课上说错话时，这门课对我来说仍然像往常一样有趣。"
              "3.当我不能在数学方面做某事时，未来的课程对我来说仍然会像往常一样有趣。"
              "4.当我不能解决数学问题时，下次我就没有动力了。"
              "5.当我在数学课上出错时，我以后在数学课上的乐趣就会减少。"
              "6.当我在数学方面做不到某事时，我仍然想继续努力。"]
             :answers [1 2 3 4 5 6 7]}]})

(defn adapt2-questions []
  {:type   :question
   :widget [questionnaire
            {:id      "对错误反应的适应性问卷2"
             :leading "对错误反应的行为适应性（从 1 非常不同意到 7 非常同意）"
             :questions
             ["1.当我在数学方面做不到某事时，下一次我会更加努力。"
              "2.当数学的一些地方对我来说太难时，很明显我需要为上课做更好的准备。"
              "3.当我在数学题上犯错时，我会设定一个目标来努力提高自己。"
              "4.当我在数学题上犯错时，我就知道下次我必须把精力集中在哪里。"
              "5.当我在数学题上犯错时，我会专门尝试解决它。"
              "6.当我无法解决数学问题时，这有助于我知道我可以在哪里提高自己。"
              "7.当我无法解决数学问题时，我会自己练习这些类型的题目。"]
             :answers [1 2 3 4 5 6 7]}]})

(defn mentality-questions []
  {:type   :question
   :widget [questionnaire
            {:id      "成长心态问卷"
             :leading "对错误反应的情感-动机适应性（从 1 完全不同意到 6 完全同意）"
             :questions
             ["1.你有一定的智力，但你无法真正改变它。（反向）"
              "2.你的智力是一些你无法改变的东西。（反向）"
              "3.无论你是谁，你都可以显著改变你的智力水平。"
              "4.老实说，你无法真正改变自己的聪明程度。（反向）"
              "5.你总是可以从根本上改变你的聪明程度。"
              "6.你可以学习新事物，但你不能真正改变你的基本智力。（反向）"
              "7.不管你有多聪明，你总是可以改变很多。"
              "8.你甚至可以大大改变你的基本智力水平。"]
             :answers [1 2 3 4 5 6]}]})

(defn interest-questions []
  {:type   :question
   :widget [w/interest-questionnaire
            {:id      "兴趣问卷"
             :leading "根据你自己的感受，从数字1（非常不感兴趣）到9（非常感兴趣）中选择你觉得适合的数字。"
             :questions
             ["1. 你对数学学习的兴趣程度如何？ "
              "2. 你对《二次根式》感兴趣程度如何？ "]
             :answers [1 2 3 4 5 6 7 8 9]}]})