(ns cyberme.psych.widget
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [cljs-time.core :as t]
            [cyberme.util.request :refer [ajax-flow] :as req]
            [clojure.string :as str]))

(defonce is-debug (atom false))

(defonce exp-id (atom nil))

(defonce config (atom {}))

(defn set-config!
  ([k v]
   (swap! config assoc k v))
  ([m]
   (swap! config merge m)))

(defn get-config [k]
  (get @config k))

(defn go-next [] (rf/dispatch [:go-step 1]))

(defn go-back [] (rf/dispatch [:go-step -1]))

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

(defn hint-jiang
  ([title sub-title]
   (hint title sub-title nil nil))
  ([title sub-title btn-title]
   (hint title sub-title btn-title #(rf/dispatch [:go-step 1])))
  ([title sub-title btn-title btn-action]
   [:div {:style {:margin-top :15% :text-align :center}}
    (when (not (or (nil? title) (str/blank? title)))
      (if @is-debug
        [:p.title {:on-click #(rf/dispatch [:go-step -1])} title]
        [:p.title title]))
    (if @is-debug
      [:p {:style                   {:margin      "auto"
                                     :width       "50em"
                                     :line-height "3rem"
                                     :font-size   "1.5rem"}
           :on-click                #(rf/dispatch [:go-step -1])
           :dangerouslySetInnerHTML {:__html sub-title}}]
      [:p {:style                   {:margin      "auto"
                                     :width       "50em"
                                     :line-height "3rem"
                                     :font-size   "1.5rem"}
           :dangerouslySetInnerHTML {:__html sub-title}}])
    (if btn-title
      [:button.button.is-info
       {:style    {:margin-top "5rem"}
        :on-click btn-action} btn-title])]))

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
             {:on-click #(js/alert "???????????????????????????????????????")} "???????????????"]
            [:button.button.is-info.mt-5
             {:on-click #(if data
                           (rf/dispatch [:psy-exp-data-upload (merge data {:upload_at (t/time-now)})]))}
             "??????????????????"])]))}))

(defn top-info [item-list current-index]
  [:div {:style {:position :fixed :top 0 :left 0 :right 0}}
   (let [each-width (/ 100 (count item-list))]
     [:<>
      (for [item item-list]
        ^{:key item}
        [:span {:style {:display "inline-block" :background-color
                        (if (= current-index (.indexOf item-list item))
                          "#1563cb" "#8eafd7") :color "white"
                        :padding "5px 0 5px 20px" :width (str each-width "%")}}
         item])])])

(defn image [src]
  [:<>
   [:div [:img {:src src :style
                {:display :block
                 :margin  "5% auto 0 auto"}}]
    [:button.button.is-info.mt-3
     {:on-click #(go-next)
      :style {:display "block" :margin "auto"}} "??????"]]])

(defn collect-guo []
  (r/with-let
    ;UUID ????????????????????????????????????????????????
    [answer (r/atom {:gender "???" :grade "???????????????" :uuid (str (random-uuid))})
     name (r/cursor answer [:name])
     age (r/cursor answer [:age])
     gender (r/cursor answer [:gender])
     grade (r/cursor answer [:grade])
     school (r/cursor answer [:school-id])]
    [:div {:style {:max-width :60%
                   :margin    "20% auto"}}
     (if @is-debug [:div {:style {:position :absolute :right :30px :bottom :10px}}
                    [:span.is-clickable {:on-click #(rf/dispatch [:go-step -1])} "<  "]
                    [:span.is-clickable {:on-click #(rf/dispatch [:go-step 1])} "  >"]])
     [:div.field.is-horizontal
      [:div.field-label.is-normal
       [:label.label "??????"]]
      [:div.field-body
       [:div.select
        [:select {:value     (or @gender "???")
                  :on-change #(reset! gender (.. % -target -value))}
         [:option "???"]
         [:option "???"]]]]]
     [:div.field.is-horizontal
      [:div.field-label.is-normal
       [:label.label "??????"]]
      [:div.field-body
       [:div.field
        [:p.control
         [:input.input {:type      "text" :placeholder "?????????????????????"
                        :value     (or @age "")
                        :on-change #(reset! age (.. % -target -value))}]]]]]
     [:div.field.is-horizontal
      [:div.field-label.is-normal
       [:label.label "??????"]]
      [:div.field-body
       [:div.select
        [:select {:value     (or @grade "???????????????")
                  :on-change #(reset! grade (.. % -target -value))}
         [:option "???????????????"]
         [:option "???????????????"]
         [:option "???????????????"]
         [:option "??????"]]]]]
     [:div.field.is-horizontal
      [:div.field-label.is-normal
       [:label.label "??????"]]
      [:div.field-body
       [:div.field
        [:p.control
         [:input.input {:type      "text" :placeholder "?????????????????????"
                        :value     (or @name "")
                        :on-change #(reset! name (.. % -target -value))}]]]]]
     [:div.field.is-horizontal
      [:div.field-label.is-normal
       [:label.label "??????"]]
      [:div.field-body
       [:div.field
        [:p.control
         [:input.input {:type      "text" :placeholder "?????????????????????"
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
                       (cond (nil? (re-matches #"\d\d" (or @age "")))
                             (js/alert "???????????????????????????????????????")
                             (nil? (re-matches #"\d+" (or @school "")))
                             (js/alert "????????????????????????????????????")
                             :else
                             (let [is-ok (and @name
                                              @age
                                              @gender
                                              @grade
                                              @school)]
                               (if is-ok
                                 (do
                                   (rf/dispatch [:save-answer ["????????????" @answer]])
                                   (reset! answer {})
                                   (rf/dispatch [:go-step 1]))
                                 (js/alert "??????????????????")))))}
          "??????"]]]]]]))

(defn collect []
  (r/with-let
    ;UUID ????????????????????????????????????????????????
    [answer (r/atom {:gender "???" :grade "???????????????" :uuid (str (random-uuid))})
     ;name (r/cursor answer [:name])
     gender (r/cursor answer [:gender])
     grade (r/cursor answer [:grade])
     school (r/cursor answer [:school])]
    [:div {:style {:max-width :60%
                   :margin    "20% auto"}}
     (if @is-debug [:div {:style {:position :absolute :right :20px :bottom :10px}}
                    [:span.is-clickable {:on-click #(rf/dispatch [:go-step -1])} "<  "]
                    [:span.is-clickable {:on-click #(rf/dispatch [:go-step 1])} "  >"]])
     [:div.field.is-horizontal
      [:div.field-label.is-normal
       [:label.label "??????"]]
      [:div.field-body
       [:div.select
        [:select {:value     (or @gender "???")
                  :on-change #(reset! gender (.. % -target -value))}
         [:option "???"]
         [:option "???"]]]]]
     [:div.field.is-horizontal
      [:div.field-label.is-normal
       [:label.label "??????"]]
      [:div.field-body
       [:div.select
        [:select {:value     (or @grade "???????????????")
                  :on-change #(reset! grade (.. % -target -value))}
         [:option "???????????????"]
         [:option "???????????????"]
         [:option "???????????????"]
         [:option "???????????????"]
         [:option "???????????????"]
         [:option "???????????????"]
         [:option "??????"]]]]]
     [:div.field.is-horizontal
      [:div.field-label.is-normal
       [:label.label "??????"]]
      [:div.field-body
       [:div.field
        [:p.control
         [:input.input {:type      "text" :placeholder "?????????????????????"
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
                             (rf/dispatch [:save-answer ["????????????" @answer]])
                             (reset! answer {})
                             (rf/dispatch [:go-step 1]))
                           (js/alert "??????????????????"))))}
          "??????"]]]]]]))

(defn problem
  "???????????????????????????????????????????????????????????????????????????????????????????????????????????????
  ????????????????????????????????????
  ?????????????????????????????????????????????
  ??????????????????
  ???????????????????????????"
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
               [:div.mt-3.has-text-success "????????????"]
               [:div.mt-3.has-text-danger "????????????"]))
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
                   {:on-click #(rf/dispatch [:go-step 1])} "???????????????"]
                  (and have-answer
                       show-twice
                       @is-second)
                  [:button.button.is-info
                   {:on-click #(rf/dispatch [:go-step 1])} "???????????????"]
                  (and have-answer
                       answer-right
                       show-twice
                       (not @is-second))
                  [:button.button.is-info
                   {:on-click #(rf/dispatch [:go-step 1])} "???????????????"]
                  (and have-answer
                       (not answer-right)
                       show-twice
                       (not @is-second))
                  [:button.button.is-info
                   {:on-click #(do (reset! first-answer nil)
                                   (reset! is-second true))} "????????????"]
                  :else [:div])]])]])))

(defn simple-question
  "???????????????"
  [id image right-answer]
  [:div {:style {:margin-top :10em
                 :background "rgb(255, 255, 255)"}}
   (if @is-debug [:div {:style {:position :absolute :right :20px :bottom :10px}}
                  [:span.is-clickable {:on-click go-back} "<  "]
                  [:span.is-clickable {:on-click go-next} "  >"]])
   (r/with-let
     [answer (r/atom nil)]
     [:div {:style {:text-align "center"}}
      [:img {:src image :style {:max-width :50em}}]
      [:p.mt-4
       [:label.radio.mr-4 {:style {:font-size :2em}}
        [:input {:type      "radio" :name (str "q" 1)
                 :checked   (= :A @answer)
                 :on-change #(reset! answer :A)
                 :style     {:width :2em :height :2em}}] " A"]
       [:label.radio.mr-4 {:style {:font-size :2em}}
        [:input {:type      "radio" :name (str "q" 1)
                 :checked   (= :B @answer)
                 :on-change #(reset! answer :B)
                 :style     {:width :2em :height :2em}}] " B"]
       [:label.radio.mr-4 {:style {:font-size :2em}}
        [:input {:type      "radio" :name (str "q" 1)
                 :checked   (= :C @answer)
                 :on-change #(reset! answer :C)
                 :style     {:width :2em :height :2em}}] " C"]
       [:label.radio.mr-4 {:style {:font-size :2em}}
        [:input {:type      "radio" :name (str "q" 1)
                 :checked   (= :D @answer)
                 :on-change #(reset! answer :D)
                 :style     {:width :2em :height :2em}}] " D"]]
      [:button.button.is-info.is-medium
       {:style    {:min-width :10em :margin-top :5em}
        :on-click (fn [_]
                    (if (nil? @answer)
                      (js/alert "??????????????????????????????")
                      (do (rf/dispatch [:save-answer [id {:image        image
                                                          :answer       @answer
                                                          :right-answer right-answer}]])
                          (reset! answer nil)
                          (go-next))))}
       "?????????"]])])

(defn questionnaire
  "?????????????????? id ??? key???value ??? k-v ??????????????? k ??????????????????????????? 1 ????????????v ????????????
  ?????? answers ??????????????????????????? v ?????????????????? answer ?????????
  ?????? answers ??????????????????????????? v ?????????????????? answers ???????????? 1 ????????????"
  [{:keys [id leading answers questions
           number-each-answer word-no-break
           content-style]}]
  [:div {:style {:margin-top :10em
                 :background "white"}}
   (if @is-debug [:div {:style {:position :absolute :right :20px :bottom :10px}}
                  [:span.is-clickable {:on-click #(rf/dispatch [:go-step -1])} "<  "]
                  [:span.is-clickable {:on-click #(rf/dispatch [:go-step 1])} "  >"]])
   (r/with-let
     [answer (r/atom {})]
     (let [questions (if @is-debug (vec (take 2 questions)) questions)]
       [:div.is-flex.is-justify-content-center.is-flex-direction-column
        [:div {:style {:max-width :75em :align-self :center}}
         (when leading [:p.mb-2 leading])
         [:table.table.is-hoverable
          [:thead {:style (if word-no-break
                            {:text-align :center :word-break :keep-all}
                            {:text-align :center})}
           (into [:tr [:th ""]] (mapv (fn [item] [:th item]) answers))]
          [:tbody
           (doall
             (for [index (range 1 (+ (count questions) 1))]
               ^{:key index}
               [:<>
                (into
                  [:tr [:td
                        {:style (if content-style content-style {})}
                        (get questions (- index 1) "???")]]
                  (mapv
                    (fn [each]
                      [:td {:style {:text-align :center}}
                       [:label.radio
                        {:style (if word-no-break {:word-break :keep-all} {})}
                        [:input {:type      "radio"
                                 :name      (str "q" index)
                                 :checked   (= (get @answer index -1) each)
                                 :on-change #(swap! answer assoc index each)}]
                        (str " " (if number-each-answer (+ (-indexOf answers each) 1) each))]])
                    answers))]))]]]
        [:div.is-flex.is-justify-content-center
         [:button.button.is-info.is-large.is-fullwidth
          {:style    {:margin-top :100px :margin-bottom :100px
                      :max-width  :30em}
           :on-click (fn []
                       (let []
                         (if (< (count @answer) (count questions))
                           (js/alert "????????????????????????????????????")
                           (let [num-answer
                                 (into {}
                                       (mapv (fn [[k v]] (if-not (number? v)
                                                           [k (+ (-indexOf answers v) 1)]
                                                           [k v]))
                                             @answer))]
                             (println @answer num-answer)
                             (rf/dispatch [:save-answer [id num-answer]])
                             (reset! answer {})
                             (rf/dispatch [:go-step 1])))))}
          "????????????"]]]))])

(defn cong-questionnaire
  "??????????????????
  ?????????????????? id ??? key???value ??? k-v ??????????????? k ??????????????????????????? 1 ????????????v ????????????
  ?????? answers ??????????????????????????? v ?????????????????? answer ?????????
  ?????? answers ??????????????????????????? v ?????????????????? answers ???????????? 1 ?????????"
  [{:keys [id leading answers questions]}]
  [:div {:style {:margin-top :10em
                 :background "white"}}
   (if @is-debug [:div {:style {:position :absolute :right :20px :bottom :10px}}
                  [:span.is-clickable {:on-click #(rf/dispatch [:go-step -1])} "<  "]
                  [:span.is-clickable {:on-click #(rf/dispatch [:go-step 1])} "  >"]])
   (r/with-let
     [answer (r/atom {})]
     [:div.is-flex.is-justify-content-center.is-flex-direction-column
      [:div {:style {:max-width :70em :align-self :center}}
       (when leading [:p.mb-2 leading])
       [:table.table.is-hoverable
        [:thead {:style {:text-align :center}}
         (into [:tr [:th ""]] (mapv (fn [item] [:th item]) answers))]
        [:tbody
         (for [index (range 1 (+ (count questions) 1))]
           ^{:key index}
           [:<>
            (into
              [:tr [:td (get questions (- index 1) "???")]]
              (mapv
                (fn [each]
                  [:td {:style {:text-align :center}}
                   [:label.radio
                    {:on-click #(swap! answer assoc index each)}
                    [:input {:type "radio" :name (str "q" index)}]
                    (str " " each)]
                   (cond (and (= index 1) (= each 1))
                         [:p {:style {:min-width :4em}} "????????????"]
                         (and (= index 1) (= each 5))
                         [:p {:style {:min-width :4em}} "????????????"]
                         (and (= index 1) (= each 9))
                         [:p {:style {:min-width :4em}} "????????????"]
                         (and (= index 2) (= each 1))
                         [:p {:style {:min-width :4em}} "????????????"]
                         (and (= index 2) (= each 5))
                         [:p {:style {:min-width :4em}} "????????????"]
                         (and (= index 2) (= each 9))
                         [:p {:style {:min-width :4em}} "????????????"]
                         (and (= index 3) (= each 1))
                         [:p {:style {:min-width :4em}} "????????????"]
                         (and (= index 3) (= each 5))
                         [:p {:style {:min-width :4em}} "????????????"]
                         (and (= index 3) (= each 9))
                         [:p {:style {:min-width :4em}} "????????????"])])
                answers))])]]]
      [:div.is-flex.is-justify-content-center
       [:button.button.is-info.is-large.is-fullwidth
        {:style    {:margin-top :100px :margin-bottom :100px
                    :max-width  :30em}
         :on-click (fn []
                     (if (< (count @answer) (count questions))
                       (js/alert "????????????????????????????????????")
                       (let [num-answer
                             (into {}
                                   (mapv (fn [[k v]] (if-not (number? v)
                                                       [k (+ (-indexOf answers v) 1)]
                                                       [k v]))
                                         @answer))]
                         (println @answer num-answer)
                         (rf/dispatch [:save-answer [id num-answer]])
                         (reset! answer {})
                         (rf/dispatch [:go-step 1]))))}
        "????????????"]]])])

(defn interest-questionnaire
  "????????????
  ?????????????????? id ??? key???value ??? k-v ??????????????? k ??????????????????????????? 1 ????????????v ????????????
  ?????? answers ??????????????????????????? v ?????????????????? answer ?????????
  ?????? answers ??????????????????????????? v ?????????????????? answers ???????????? 1 ????????????"
  [{:keys [id leading answers questions]}]
  [:div {:style {:margin-top :10em
                 :background "white"}}
   (if @is-debug [:div {:style {:position :absolute :right :20px :bottom :10px}}
                  [:span.is-clickable {:on-click #(rf/dispatch [:go-step -1])} "<  "]
                  [:span.is-clickable {:on-click #(rf/dispatch [:go-step 1])} "  >"]])
   (r/with-let
     [answer (r/atom {})]
     [:div.is-flex.is-justify-content-center.is-flex-direction-column
      [:div {:style {:max-width :70em :align-self :center}}
       (when leading [:p.mb-2 leading])
       [:<>
        (for [index (range 1 (+ (count questions) 1))]
          ^{:key index}
          [:div.mb-5.mt-5
           [:p.mb-4 (get questions (- index 1) "???")]
           [:div.is-flex.pl-3.pr-3
            (for [each answers]
              ^{:key each}
              [:div {:style {:text-align :center}}
               [:label.radio
                {:on-click #(swap! answer assoc index each)}
                [:input {:type "radio" :name (str "q" index)}]
                (str " " each)]
               (cond (= each 1)
                     [:p {:style {:min-width :6em}} "??????????????????"]
                     (= each 5)
                     [:p {:style {:min-width :6em}} "????????????"]
                     (= each 9)
                     [:p {:style {:min-width :6em}} "???????????????"]
                     :else
                     [:p {:style {:min-width :6em}} ""])])]])]]
      [:div.is-flex.is-justify-content-center
       [:button.button.is-info.is-large.is-fullwidth
        {:style    {:margin-top :100px :margin-bottom :100px
                    :max-width  :30em}
         :on-click (fn []
                     (if (< (count @answer) (count questions))
                       (js/alert "????????????????????????????????????")
                       (let [num-answer
                             (into {}
                                   (mapv (fn [[k v]] (if-not (number? v)
                                                       [k (+ (-indexOf answers v) 1)]
                                                       [k v]))
                                         @answer))]
                         (println @answer num-answer)
                         (rf/dispatch [:save-answer [id num-answer]])
                         (reset! answer {})
                         (rf/dispatch [:go-step 1]))))}
        "????????????"]]])])

(defn interest-questionnaire-jiang
  "????????????
  ?????????????????? id ??? key???value ??? k-v ??????????????? k ??????????????????????????? 1 ????????????v ????????????
  ?????? answers ??????????????????????????? v ?????????????????? answer ?????????
  ?????? answers ??????????????????????????? v ?????????????????? answers ???????????? 1 ????????????"
  [{:keys [id leading answers questions]}]
  [:div {:style {:margin-top :10em
                 :background "white"}}
   (if @is-debug [:div {:style {:position :absolute :right :20px :bottom :10px}}
                  [:span.is-clickable {:on-click #(rf/dispatch [:go-step -1])} "<  "]
                  [:span.is-clickable {:on-click #(rf/dispatch [:go-step 1])} "  >"]])
   (r/with-let
     [answer (r/atom {})]
     [:div.is-flex.is-justify-content-center.is-flex-direction-column
      [:div {:style {:max-width :70em :align-self :center}}
       (when leading [:p.mb-2 leading])
       [:<>
        (for [index (range 1 (+ (count questions) 1))]
          ^{:key index}
          [:div.mb-5.mt-5
           [:p.mb-4 (get questions (- index 1) "???")]
           [:div.is-flex.pl-3.pr-3
            (for [each answers]
              ^{:key each}
              [:div {:style {:text-align :center}}
               [:label.radio
                {:on-click #(swap! answer assoc index each)}
                [:input {:type "radio" :name (str "q" index)}]
                (str " " each)]
               (cond (= each 1)
                     [:p {:style {:min-width :6em}} "????????????"]
                     (= each 10)
                     [:p {:style {:min-width :6em}} "????????????"]
                     :else
                     [:p {:style {:min-width :6em}} ""])])]])]]
      [:div.is-flex.is-justify-content-center
       [:button.button.is-info.is-large.is-fullwidth
        {:style    {:margin-top :100px :margin-bottom :100px
                    :max-width  :30em}
         :on-click (fn []
                     (if (< (count @answer) (count questions))
                       (js/alert "????????????????????????????????????")
                       (let [num-answer
                             (into {}
                                   (mapv (fn [[k v]] (if-not (number? v)
                                                       [k (+ (-indexOf answers v) 1)]
                                                       [k v]))
                                         @answer))]
                         (println @answer num-answer)
                         (rf/dispatch [:save-answer [id num-answer]])
                         (reset! answer {})
                         (rf/dispatch [:go-step 1]))))}
        "????????????"]]])])

(defn problem-guo
  "???????????????????????????????????????????????????????????????"
  [{:keys [id step-1 step-2 right-answer exp-cond is-demo demo-step2-hint demo-step3-hint]}]
  (r/with-let
    [select (r/atom nil)
     answer (r/atom nil)]
    [:div {:style {:margin "10% auto"}}
     [:div {:style {:text-align "center"}}
      [:img {:src step-1 :style {:max-width :50em}}]
      [:p.mt-4
       [:label.radio.mr-4 {:style {:font-size :2em}}
        [:input {:type      "radio" :name (str "q" 1)
                 :checked   (= :A @select)
                 :disabled  (when @answer "disabled")
                 :on-change #(reset! select :A)
                 :style     {:width :2em :height :2em}}] " A"]
       [:label.radio.mr-4 {:style {:font-size :2em}}
        [:input {:type      "radio" :name (str "q" 1)
                 :checked   (= :B @select)
                 :disabled  (when @answer "disabled")
                 :on-change #(reset! select :B)
                 :style     {:width :2em :height :2em}}] " B"]
       [:label.radio.mr-4 {:style {:font-size :2em}}
        [:input {:type      "radio" :name (str "q" 1)
                 :checked   (= :C @select)
                 :disabled  (when @answer "disabled")
                 :on-change #(reset! select :C)
                 :style     {:width :2em :height :2em}}] " C"]
       [:label.radio.mr-4 {:style {:font-size :2em}}
        [:input {:type      "radio" :name (str "q" 1)
                 :checked   (= :D @select)
                 :disabled  (when @answer "disabled")
                 :on-change #(reset! select :D)
                 :style     {:width :2em :height :2em}}] " D"]]
      [:button.button.is-info.is-large.mt-5.mb-2
       {:style    {:align-self :center :max-width :25em}
        :disabled (when @answer "disabled")
        :on-click #(do
                     (if (nil? @select)
                       (js/alert "??????????????????")
                       (reset! answer @select)))} "????????????"]
      (when (not (nil? @answer))
        [:div
         (if (= @answer right-answer)
           [:div.mt-3.has-text-success {:style {:font-size "1.3em"}} "????????????"]
           [:div.mt-3.has-text-danger {:style {:font-size "1.3em"}} "????????????"])
         (if is-demo
           [:img {:src   demo-step2-hint
                  :style {:margin-top "0px" :max-width :25em
                          :align-self "center"}}])
         (case exp-cond
           1 [:p {:style {:font-size "1.3em"}}
              "??????????????????" [:span.has-text-weight-bold "????????????"] "??????????????????"]
           2 [:p {:style {:font-size "1.3em"}}
              "??????????????????" [:span.has-text-weight-bold "????????????"] "??????????????????"]
           3 [:p {:style {:font-size "1.3em"}}
              "??????????????????" [:span.has-text-weight-bold "????????????"] "??????????????????"])
         [:img {:src   step-2
                :style {
                        ;:max-width  :25em
                        :border "3px solid"
                        :margin-top "20px"}}]
         [:div {:style {:display "flex" :flex-direction "column" :margin-top "20px"}}
          (when is-demo
            [:img {:src   demo-step3-hint
                   :style {:align-self :center :max-width :25em}}])
          [:button.button.is-info.is-large
           {:style    {:align-self :center :max-width :25em}
            :on-click #(do
                         (when-not is-demo
                           (rf/dispatch [:save-answer
                                         [id {:right-answer right-answer
                                              :user-answer  @answer
                                              :exp-cond     exp-cond
                                              :record-time  (.getTime (js/Date.))}]]))
                         (reset! select nil)
                         (reset! answer nil)
                         (go-next))} "?????????"]]])]]))

(defn problem-cond4-guo
  "???????????????????????????????????????????????????????????????"
  [{:keys [id step-1 step2-each step2-each-hint right-answer
           is-demo demo-step2-hint-left demo-step2-hint-right demo-step3-hint]}]
  (r/with-let
    [answer (r/atom nil)
     select (r/atom nil)
     select-style (r/atom -1)
     selected-style (r/atom #{-1})]
    [:div {:style {:margin "10% auto"}}
     [:div {:style {:text-align "center"}}
      [:img {:src step-1 :style {:max-width :50em}}]
      [:p.mt-4
       [:label.radio.mr-4 {:style {:font-size :2em}}
        [:input {:type      "radio" :name (str "q" 1)
                 :checked   (= :A @select)
                 :disabled  (when @answer "disabled")
                 :on-change #(reset! select :A)
                 :style     {:width :2em :height :2em}}] " A"]
       [:label.radio.mr-4 {:style {:font-size :2em}}
        [:input {:type      "radio" :name (str "q" 1)
                 :checked   (= :B @select)
                 :disabled  (when @answer "disabled")
                 :on-change #(reset! select :B)
                 :style     {:width :2em :height :2em}}] " B"]
       [:label.radio.mr-4 {:style {:font-size :2em}}
        [:input {:type      "radio" :name (str "q" 1)
                 :checked   (= :C @select)
                 :disabled  (when @answer "disabled")
                 :on-change #(reset! select :C)
                 :style     {:width :2em :height :2em}}] " C"]
       [:label.radio.mr-4 {:style {:font-size :2em}}
        [:input {:type      "radio" :name (str "q" 1)
                 :checked   (= :D @select)
                 :disabled  (when @answer "disabled")
                 :on-change #(reset! select :D)
                 :style     {:width :2em :height :2em}}] " D"]]
      [:button.button.is-info.is-large.mt-5.mb-2
       {:style    {:align-self :center :max-width :25em}
        :disabled (when @answer "disabled")
        :on-click #(do
                     (if (nil? @select)
                       (js/alert "??????????????????")
                       (reset! answer @select)))} "????????????"]
      (when (not (nil? @answer))
        [:div
         (if (= @answer right-answer)
           [:div.mt-3.has-text-success {:style {:font-size "1.3em"}} "????????????"]
           [:div.mt-3.has-text-danger {:style {:font-size "1.3em"}} "????????????"])
         [:div.is-flex.is-justify-content-space-around
          (if is-demo
            [:div {:style {:width "25em"}}
             [:img {:src   demo-step2-hint-left
                    :style {:margin-top "0px" :max-width :25em}}]
             [:img {:src   (get step2-each-hint @select-style)
                    :style {:margin-top "10px" :max-width :25em}}]]
            [:img {:src   demo-step2-hint-left
                   :style {:margin-top "0px" :max-width :25em :align-self "start"}}])
          [:div
           [:p {:style {:font-size :1.5em :margin "15px 0 10px 0"}} "?????????"]
           [:div.mb-3
            [(if (= 0 @select-style)
               :button.button.is-info.mr-2
               :button.button.mr-2)
             {:on-click (fn [_]
                          (if is-demo
                            (do (reset! select-style 0)
                                (swap! selected-style conj 0))
                            (if (= -1 @select-style)
                              (do (reset! select-style 0)
                                  (swap! selected-style conj 0)))))} "????????????"]
            [(if (= 1 @select-style)
               :button.button.is-info.mr-2
               :button.button.mr-2)
             {:on-click (fn [_]
                          (if is-demo
                            (do (reset! select-style 1)
                                (swap! selected-style conj 1))
                            (if (= -1 @select-style)
                              (do (reset! select-style 1)
                                  (swap! selected-style conj 1)))))} "????????????"]
            [(if (= 2 @select-style)
               :button.button.is-info
               :button.button)
             {:on-click (fn [_]
                          (if is-demo
                            (do (reset! select-style 2)
                                (swap! selected-style conj 2))
                            (if (= -1 @select-style)
                              (do (reset! select-style 2)
                                  (swap! selected-style conj 2)))))} "????????????"]]
           (when-not (= -1 @select-style)
             [:<>
              (case @select-style
                0 [:p {:style {:font-size "1.3em"}}
                   "??????????????????" [:span.has-text-weight-bold "????????????"] "??????????????????"]
                1 [:p {:style {:font-size "1.3em"}}
                   "??????????????????" [:span.has-text-weight-bold "????????????"] "??????????????????"]
                2 [:p {:style {:font-size "1.3em"}}
                   "??????????????????" [:span.has-text-weight-bold "????????????"] "??????????????????"])
              [:img {:src   (get step2-each @select-style)
                     :style {
                             ;:max-width  :25em
                             :border "3px solid"
                             :margin-top "20px"}}]])]
          [:img {:src   demo-step2-hint-right
                 :style {:margin-top "0px" :max-width :25em :align-self "start"}}]]
         (if is-demo
           (if (= #{-1 0 1 2} @selected-style)
             [:div {:style {:display "flex" :flex-direction "column" :margin-top "20px"}}
              [:img {:src   demo-step3-hint
                     :style {:align-self :center :max-width :25em}}]
              [:button.button.is-info.is-large
               {:style    {:align-self :center :max-width :25em}
                :on-click #(do (reset! answer nil)
                               (reset! select-style -1)
                               (reset! selected-style #{-1})
                               (go-next))} "?????????"]])
           (if-not (= -1 @select-style)
             [:div {:style {:display "flex" :flex-direction "column" :margin-top "20px"}}
              [:button.button.is-info.is-large
               {:style    {:align-self :center :max-width :25em}
                :on-click #(do (rf/dispatch
                                 [:save-answer [id {:right-answer right-answer
                                                    :user-answer  @answer
                                                    :select-cond  (+ @select-style 1)
                                                    :exp-cond     4
                                                    :record-time  (.getTime (js/Date.))}]])
                               (reset! select nil)
                               (reset! answer nil)
                               (reset! select-style -1)
                               (reset! selected-style #{-1})
                               (go-next))} "?????????"]]))])]]))

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
