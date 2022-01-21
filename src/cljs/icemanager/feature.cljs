(ns icemanager.feature
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.string :as string]
            [icemanager.modals :as modals]
            [goog.string :as gstring]
            [reagent.core :as r]))

(defn clj->js-str [clj]
  (.stringify js/JSON (clj->js clj) nil 4))

(defn dev-row-to-data [row]
  "将前端显示的 JSON 字符串转换为格式化数据"
  (rf/dispatch [:app/show-modal :update-feature])
  (try
    (let [dev-json-str (:developer row)
          dev-json (.parse js/JSON (if (string/blank? dev-json-str)
                                     "[]" dev-json-str))
          clj-data (js->clj dev-json :keywordize-keys true)]
      {:error nil :data (assoc row :developer clj-data)})
    (catch js/Error. e
      {:error (str "不合法的 JSON 字符串，" e) :data nil})))

(defn feature-bottom-button [rs-id]
  (let [lower-id (string/lower-case rs-id)]
    [:div
     {:style {:display         :flex
              :flex-flow       "row wrap"
              :justify-content :flex-start
              :margin-bottom   :-10px}}
     [:a.button.mr-2.mt-1.mb-1
      {:href (str "/" lower-id ".docx")}
      [:i.material-icons {:style {:vertical-align :-20%
                                  :padding-right  :5px
                                  :margin-left    :-5px}}
       "description"]
      [:span "TR 文档"]]
     [:a.button.mr-2.mt-1.mb-1
      {:href (str "/" lower-id ".pdf")}
      [:i.material-icons {:style {:vertical-align :-20%
                                  :padding-right  :5px
                                  :margin-left    :-5px}}
       "picture_as_pdf"]
      [:span "导出 PDF"]]
     [:a.button.mr-2.mt-1.mb-1
      {:href (str "/" lower-id ".api")}
      [:i.material-icons {:style {:vertical-align :-20%
                                  :padding-right  :5px
                                  :margin-left    :-5px}}
       "public"]
      [:span "Swagger 接口"]]]))

(defn developer-card [dev-list {:keys [href-link]}]
  [:<>
   (for [{:keys [name email role duty]} dev-list]
     ^{:key (random-uuid)}
     [:a.dui-tips (let [origin {:style {:padding-right :6px
                                        :color
                                        (if href-link href-link "black")}
                                :data-tooltip
                                (str (if role role "说：\"不知道为啥就被") "\n"
                                     (if duty duty "塞到了这个项目中\""))
                                }]
                    (if email (assoc origin :href (str "mailto:" email)) origin))
      (when (not= href-link "white")
        [:i.material-icons {:style {:vertical-align :-20%
                                    :padding-right  :1px}}
         (let [role1 (or role "")]
           (cond (string/includes? role1 "前端")
                 "person"
                 (string/includes? role1 "后端")
                 "person"
                 (string/includes? role1 "测试")
                 "person"
                 :else "person"))])
      name])])

(defn feature-card [{:keys [id rs_id title version description update_at info]}
                    {:keys [with-footer with-description with-big-pic]}]
  [(if with-footer :div.box.columns.mt-5
                   :div.columns.mt-5)
   [:div.column {:style {:z-index :2}}
    [:p.title
     [:span {:on-click #(rf/dispatch [:common/navigate! :feature-view
                                      {:rs-id (string/lower-case rs_id)}])
             :style    {:cursor       :pointer
                        :margin-right (if-not with-footer :10px :0px)}}
      title]
     (when with-footer
       [:span [:a {:on-click #(rf/dispatch [:common/navigate! :feature
                                            {:rs-id (string/lower-case rs_id)}])
                   :style    {:cursor         :pointer
                              :font-size      :8px
                              :margin-left    :7px
                              :margin-right   :10px
                              :vertical-align :80%}}
               [:i.material-icons {:font-size :10px} "border_color"]]])
     [:span.tags.has-addons
      {:style {:display        :inline-block
               :vertical-align :-10%}}
      [:span.tag.is-link rs_id]
      [:span.tag.is-dark version]]]
    (let [{dev-list :developer
           status   :status} info]
      [:<>
       [:p (str "当前状态：" status)
        [:span {:style {:display        :inline-block
                        :margin-left    :10px
                        :vertical-align :6%}}
         (when with-footer
           [:<>
            (if (-> info :designRes)
              [:a.ml-1 {:href   (:designRes info)
                        :target :_black}
               [:span.tag.is-rounded "设计图"]])
            (if (-> info :uiRes)
              [:a.ml-1 {:href   (:uiRes info)
                        :target :_black}
               [:span.tag.is-rounded "UI 渲染图"]])])]]
       [:p "参与人员：" [developer-card
                    (if (vector? dev-list) dev-list [dev-list])
                    {:href-link (if with-footer "#545454" "white")}]]])
    [:p "最后更新：" update_at]
    (when with-footer [feature-bottom-button rs_id])]
   (when with-description
     [:div.tile.notification.column
      (when (not with-footer)
        {:style {:color            :white
                 :background-color "rgba(159, 219, 180, 0.19)"}})
      [:div.card-content
       [:div.content (if (clojure.string/blank? description)
                       "尚无介绍.." description)]]])
   (when with-big-pic
     [:div.is-hidden-mobile
      [:i.material-icons
       {:style {:font-size :23em
                :position  :absolute
                :top       :16px
                :right     :-120px
                :z-index :0
                :opacity :0.2
                }} "camera"]])])

(defn reformat-review-user-to-array [row]
  (let [review-list (-> row :info :review)
        new-review-list (map (fn [rev]
                               (let [participants-str (:participants rev)]
                                 (if (vector? participants-str)
                                   rev
                                   (let [replace-cn (string/replace participants-str "，" ",")]
                                     (assoc rev
                                       :participants
                                       (vec (map string/trim
                                                 (string/split replace-cn ","))))))))
                             review-list)]
    (assoc-in row [:info :review] (vec new-review-list))))

(defn developer-implement-review-to-data [row]
  (try
    (let [impl-keys (:implement-keys row)
          developer-keys (:developer-keys row)
          review-keys (:review-keys row)
          fnn (fn [agg new-key] (conj agg (get row new-key)))]
      (let [merged-data {:implement (reduce fnn [] impl-keys)
                         :review    (reduce fnn [] review-keys)
                         :developer (reduce fnn [] developer-keys)}
            new-data (assoc row :info (merge (get row :info) merged-data))
            temp-keys (conj (into (into impl-keys review-keys) developer-keys)
                            :review-keys :implement-keys :developer-keys
                            :review-index :implement-index :developer-index)
            final-data (apply dissoc new-data temp-keys)
            reformat-data (reformat-review-user-to-array final-data)]
        {:error nil :data reformat-data}))
    (catch js/Error. e
      {:error e :data nil})))

(defn data-to-dev-row [data]
  "将格式化数据转换为前端显示的文本 - JSON 格式化字符串"
  (clj->js-str (if (map? data) (vector data) data)))

(defn notice-modal-card []
  (modals/modal-card :update-feature-notice "提醒"
                     (let [error @(rf/subscribe [:update-feature-error])]
                       (if-not error
                         [:p "特性更新成功！"]
                         [:div
                          [:p "特性更新失败！"]
                          [:pre (with-out-str (cljs.pprint/pprint error))]]))
                     [:button.button.is-success.is-fullwidth
                      {:on-click #(rf/dispatch [:app/hide-modal :update-feature-notice])}
                      "关闭"]))

(defn developer-form [fields common-field]
  (let [{:keys [rs-id developer-keys developer-index] :as f} @fields]
    [:<>
     [:p.title {:style {:margin-top :30px}} "开发者信息"]
     [:div.tabs.is-boxed {:style {:margin-left :-30px
                                  :margin-top  :-30px}}
      [:ul
       (for [[index developer-key] (map-indexed vector developer-keys)]
         ^{:key developer-key}
         [(if (= index developer-index) :li.is-active :li)
          (let [data (get f developer-key)]
            [:a
             {:on-click (fn [_] (swap! fields assoc :developer-index index))}
             [:<>
              [:i.material-icons {:style {:font-size    :1.3em
                                          :margin-top   :-2px
                                          :margin-left  :-2px
                                          :margin-right :1px}}
               "person_outline"]
              (or (:name data) "新添个人")]
             [:i.material-icons {:style    {:font-size   :1em
                                            :margin-left :0.5em}
                                 :on-click (fn [_]
                                             (swap! fields dissoc developer-key)
                                             (swap! fields assoc :developer-keys
                                                    (vec (remove #(= % developer-key) developer-keys))))}
              "cancel"]])])
       [:a {:on-click (fn [_]
                        (let [new-key (keyword (str "developer." (random-uuid)))]
                          (swap! fields assoc new-key {:title "" :content ""})
                          (swap! fields assoc :developer-keys (conj developer-keys new-key))
                          (swap! fields assoc :developer-index (count developer-keys))))}
        [:i.material-icons "add"]]]]
     [common-field [(get developer-keys developer-index) :name]
      "姓名" "在此输入开发者姓名"
      {}]
     [common-field [(get developer-keys developer-index) :email]
      "邮箱" "在此输入开发者联系方式"
      {}]
     [common-field [(get developer-keys developer-index) :role]
      "角色" "在此输入开发者角色"
      {:type :select :selects
       ["后端开发" "测试人员" "前端开发" "UI 设计" "系统架构" "项目经理" "其它"]}]
     [common-field [(get developer-keys developer-index) :duty]
      "任务" "在此输入开发者负责的任务"
      {:type :textarea
       :rows 2}]]))

(defn impl-form [fields common-field]
  (let [{:keys [rs_id implement-keys implement-index]} @fields]
    [:<>
     [:p.title {:style {:margin-top :30px}} "实施方案"]
     [:div.tabs.is-boxed {:style {:margin-left :-30px
                                  :margin-top  :-30px}}
      [:ul
       (for [[index implement-key] (map-indexed vector implement-keys)]
         ^{:key implement-key}
         [(if (= index implement-index) :li.is-active :li)
          [:a
           {:on-click (fn [_] (swap! fields assoc :implement-index index))}
           [:<>
            [:i.material-icons {:style {:font-size    :1.1em
                                        :margin-top   :-1px
                                        :margin-left  :-2px
                                        :margin-right :3px}}
             "description"]
            (str rs_id "." (gstring/format "%03d" (+ index 1)))]
           [:i.material-icons {:style    {:font-size   :1em
                                          :margin-left :0.5em}
                               :on-click (fn [_]
                                           (swap! fields dissoc implement-key)
                                           (swap! fields assoc :implement-keys
                                                  (vec (remove #(= % implement-key) implement-keys))))}
            "cancel"]]])
       [:a {:on-click (fn [_]
                        (let [new-key (keyword (str "implement." (random-uuid)))]
                          (swap! fields assoc new-key {:title "" :content ""})
                          (swap! fields assoc :implement-keys (conj implement-keys new-key))
                          (swap! fields assoc :implement-index (count implement-keys))))}
        [:i.material-icons "add"]]]]
     (when (empty? implement-keys)
       [:div.notification.is-danger.pt-2.pb-2.pl-3
        "请点击 + 新建子方案后再填写下面表单"])
     [common-field [(get implement-keys implement-index) :title]
      "步骤标题" "在此输入方案分解后的子标题"
      (if (empty? implement-keys) {:readonly ""})]
     [common-field [(get implement-keys implement-index) :content]
      "步骤内容" "在此输入方案分解的具体实施计划"
      {:type :textarea
       :readonly (if (empty? implement-keys) "" nil)}]]))

(defn review-form [fields common-field]
  (let [{:keys [review-keys review-index] :as f} @fields]
    [:<>
     [:p.title {:style {:margin-top :30px}} "评审记录"]
     [:div.tabs.is-boxed {:style {:margin-left :-30px
                                  :margin-top  :-30px}}
      [:ul
       (for [[index review-key] (map-indexed vector review-keys)]
         ^{:key review-key}
         [(if (= index review-index) :li.is-active :li)
          (let [curr-data (get f review-key)]
            [:a
             {:on-click (fn [_] (swap! fields assoc :review-index index))}
             [:<>
              [:i.material-icons {:style {:font-size    :1.1em
                                          :margin-top   :-1px
                                          :margin-left  :-2px
                                          :margin-right :3px}}
               "today"]
              (:date curr-data)]
             [:i.material-icons {:style    {:font-size   :1em
                                            :margin-left :0.5em}
                                 :on-click (fn [_]
                                             (swap! fields dissoc review-key)
                                             (swap! fields assoc :review-keys
                                                    (vec (remove #(= % review-key) review-keys))))}
              "cancel"]])])
       [:a {:on-click (fn [_]
                        (let [new-key (keyword (str "implement." (random-uuid)))]
                          (swap! fields assoc new-key
                                 {:title "" :content ""
                                  :date  (cljs-time.format/unparse
                                           (cljs-time.format/formatter "yyyy-MM-dd HH:mm")
                                           (cljs-time.core/time-now))})
                          (swap! fields assoc :review-keys (conj review-keys new-key))
                          (swap! fields assoc :review-index (count review-keys))))}
        [:i.material-icons "add"]]]]
     (when (empty? review-keys)
       [:div.notification.is-danger.pt-2.pb-2.pl-3
        "请点击 + 新建评审后再填写下面表单"])
     [common-field [(get review-keys review-index) :title]
      "标题" "在此输入此次评审的标题"
      (if (empty? review-keys) {:readonly ""})]
     [common-field [(get review-keys review-index) :participants]
      "参会者（使用全角或半角逗号隔开，允许空格）" "在此输入此次评审的参会者，使用逗号隔开"
      (if (empty? review-keys) {:readonly ""})]
     [common-field [(get review-keys review-index) :content]
      "内容" "在此输入此次评审的主要内容"
      {:type :textarea
       :readonly (if (empty? review-keys) "" nil)}]]))

(defn feature-form [db-data]
  (let [review (-> db-data :info :review)
        implement (-> db-data :info :implement)
        developer (let [r (-> db-data :info :developer)] (if (vector? r) r [r]))
        review_map (zipmap (map #(keyword (str "review." %))
                                (repeatedly (count review) #(random-uuid))) review)
        implement_map (zipmap (map #(keyword (str "implement." %))
                                   (repeatedly (count implement) #(random-uuid))) implement)
        developer_map (zipmap (map #(keyword (str "developer." %))
                                   (repeatedly (count developer) #(random-uuid))) developer)
        fields_merged (merge db-data {:implement-keys  (vec (keys implement_map))
                                      :review-keys     (vec (keys review_map))
                                      :developer-keys  (vec (keys developer_map))
                                      :implement-index 0
                                      :review-index    0
                                      :developer-index 0}
                             review_map implement_map developer_map)
        ;_ (println fields_merged)
        fields (r/atom fields_merged)
        errors (r/atom {})
        success (r/atom {})]
    (letfn [(common-field [id label hint {:keys [type selects moreNode rows readonly]}]
              (let [v (r/cursor fields (if (vector? id) id [id]))
                    e (r/cursor errors (if (vector? id) id [id]))]
                #_(println id)
                [:div.field
                 [:label.label {:for id} label]
                 (when moreNode moreNode)
                 [(cond (= type :textarea) :div.control>textarea.textarea
                        (= type :select) :div.control>div.select
                        :else :div.control>input.input)
                  (merge {:type        (if type type :text)
                          :id          id
                          :value       @v
                          :on-change   #(reset! v (.. % -target -value))
                          :rows        (if rows rows 5)
                          :placeholder hint}
                         (if readonly {:readonly ""} {}))
                  (when-not (nil? selects)
                    [:select
                     {:id        id
                      :value     (or @v "")
                      :on-change #(reset! v (.. % -target -value))}
                     (for [k selects]
                       ^{:key k}
                       [:option {:value k} k])])]
                 (when-let [message @e]
                   [:div.notification.is-danger.mt-4 message])]))
            (submit-feature-edit []
              (let [raw-data @fields
                    id (:id raw-data)
                    {:keys [error data]} (developer-implement-review-to-data raw-data)
                    _ (println data)]
                (if error (do
                            (rf/dispatch [:set-update-feature-error error])
                            (rf/dispatch [:app/show-modal :update-feature]))
                          (rf/dispatch [:update-feature id data]))))]
      [:<>
       [:div.title "特性概况"]
       [common-field :rs_id "RS 号" "全大写，不能有空格"]
       [common-field :title "特性名字" "简短易懂"]
       [common-field :description "描述" "简短的对特性的应用场景进行描述"
        {:type :textarea}]
       [common-field :version "引入版本" "输入 ICE 4.3 或者 ICE 5.0"
        {:type :select :selects ["ICE 4.3" "ICE 5.0"]}]
       [common-field [:info :uiRes] "UI 渲染图 URL" "输入 UI 渲染图所在文件夹"]
       [common-field [:info :designRes] "设计图 URL" "输入设计图 xxx.svg 所在的位置"]
       [common-field [:info :status] "当前状态" "正在开发，开发完毕 或者尚未开始"
        {:type :select :selects
         ["TR1", "TR2", "TR3", "TR4", "TR5", "尚未开始" "正在开发" "正在测试" "开发完毕"]}]
       [developer-form fields common-field]
       [impl-form fields common-field]
       [review-form fields common-field]
       [:button.button.is-primary.is-large.mt-4
        {:style    {:align :right}
         :on-click (fn [_] (submit-feature-edit))} "提交更新"]
       [notice-modal-card]])))

(defn feature-view-content [feature-data]
  [:<>
   [:div.hero.is-success.is-small
    {:style {:padding-left   :30px
             :padding-bottom :30px}}
    [feature-card feature-data {:with-footer      false
                                        :with-description false
                                        :with-big-pic true}]]
   [:section.section>div.container>div.content
    (let [{:keys [description version rs_id info]} feature-data
          {:keys [uiRes designRes status developer implement review]} info]
      [:<>
       [:h3.notification.is-link.is-light.pt-2.pb-2.pl-3
        {:style {:margin-top :-15px}} "特性简介"]
       [:p.ml-2 description]
       [:h3.notification.is-link.is-light.pt-2.pb-2.pl-3 "设计草图"]
       [:div.ml-2
        (if-not (string/blank? designRes)
          [:<>
           [:object {:data designRes :type "image/svg+xml"}]
           [:a {:href designRes}
            [:i.material-icons {:style {:vertical-align :-30%
                                        :margin-right   :3px}}
             "insert_link"]
            designRes]]
          [:div "暂无相关文件"])]
       [:h3.notification.is-link.is-light.pt-2.pb-2.pl-3 "UI 渲染图"]
       [:div.ml-2
        (if-not (string/blank? uiRes)
          [:<>
           [:a {:href uiRes :target :_black}
            [:i.material-icons {:style {:vertical-align :-20%
                                        :margin-right   :3px}} "call_made"]
            "点此显示预览界面"]]
          [:div "暂无相关文件"])]
       [:h3.notification.is-link.is-light.pt-2.pb-2.pl-3 "特性分解"]
       [:div.ml-2
        (for [[index {:keys [title content]}] (map-indexed vector implement)]
          ^{:key title}
          [:<>
           [:h5  " # " title " " [:span.tag.is-light {:style {:vertical-align :10%}}
                                  (str "RS." rs_id ".00" (inc index))]]
           [:p content]])]
       [:h3.notification.is-link.is-light.pt-2.pb-2.pl-3 "评审记录"]
       [:div.ml-2
        (for [[index {:keys [title date content participants]}]
              (map-indexed vector review)]
          ^{:key title}
          [:<>
           [:h5 " # " title " " [:span.tag.is-light {:style {:vertical-align :10%}}
                                 (str "@" date)]]
           [:p content]
           [:blockquote.notification.is-second.is-light.pt-1.pb-1.pl-3.mr-6 "与会人员："
            (if (vector? participants)
              (string/join "、" participants)
              participants)]])]
       [:h3.notification.is-link.is-light.pt-2.pb-2.pl-3 "参与人员"]
       [:div.ml-2
        [developer-card (if (vector? developer) developer [developer])
         {:href-link "#121212"}]]])]])