(ns icemanager.feature
  (:require [re-frame.core :as rf]
            [clojure.string :as string]
            [icemanager.modals :as modals]
            [cljs.pprint :as pprint]
            [reagent.core :as r]))

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
                    (if email (assoc origin :email (str "mailto:" email)) origin))
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
                    {:keys [with-footer with-description]}]
  [(if with-footer :div.box.columns.mt-5
                   :div.columns.mt-5)
   [:div.column
    [:p.title
     {:on-click #(rf/dispatch [:common/navigate! :feature
                               {:rs-id (string/lower-case rs_id)}])
      :style    {:cursor :pointer}}
     [:span {:style {:margin-right :10px}} title]
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
         (if (-> info :designRes)
           [:a.ml-1 {:href   (:designRes info)
                     :target :_black}
            [:span.tag.is-rounded "设计图"]])
         (if (-> info :uiRes)
           [:a.ml-1 {:href   (:uiRes info)
                     :target :_black}
            [:span.tag.is-rounded "UI 渲染图"]])]]
       [:p "维护人员：" [developer-card
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
                       "尚无介绍.." description)]]])])

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

(defn feature-form [db-data]
  (let [fields (r/atom {:id          (:id db-data)
                        :rs-id       (:rs_id db-data)
                        :title       (:title db-data)
                        :description (:description db-data)
                        :status      (-> db-data :info :status)
                        :uiRes       (-> db-data :info :uiRes)
                        :designRes   (-> db-data :info :designRes)
                        :developer   (data-to-dev-row
                                       (-> db-data :info :developer))
                        :version     (:version db-data)
                        })
        errors (r/atom {})
        success (r/atom {})]
    (letfn [(common-field [id label hint {:keys [type selects moreNode rows]}]
              (r/with-let [v (r/cursor fields [id])
                           e (r/cursor errors [id])]
                          [:div.field
                           [:label.label {:for id} label]
                           (when moreNode moreNode)
                           [(cond (= type :textarea) :div.control>textarea.textarea
                                  (= type :select) :div.control>div.select
                                  :else :div.control>input.input)
                            {:type        (if type type :text)
                             :id          id
                             :value       @v
                             :on-change   #(reset! v (.. % -target -value))
                             :rows        (if rows rows 5)
                             :placeholder hint}
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
                    {:keys [error data]} (dev-row-to-data raw-data)]
                (if error (swap! errors assoc :developer error)
                          (rf/dispatch [:update-feature id data]))))]
      [:<>
       [notice-modal-card]
       [:h3 "修改特性"]
       [common-field :rs-id "RS 号" "全大写，不能有空格"]
       [common-field :title "特性名字" "简短易懂"]
       [common-field :description "描述" "简短的对特性的应用场景进行描述"
        {:type :textarea}]
       [common-field :version "引入版本" "输入 ICE 4.3 或者 ICE 5.0"
        {:type :select :selects ["ICE 4.3" "ICE 5.0"]}]
       [common-field :uiRes "UI 渲染图 URL" "输入 UI 渲染图所在文件夹"]
       [common-field :designRes "设计图 URL" "输入设计图 xxx.svg 所在的位置"]
       [common-field :status "当前状态" "正在开发，开发完毕 或者尚未开始"
        {:type :select :selects
         ["TR1","TR2","TR3","TR4","TR5","尚未开始" "正在开发" "正在测试" "开发完毕"]}]
       [common-field :developer "开发者信息" "JSON 数组"
        {:type     :textarea
         :rows     10
         :moreNode [:<>
                    [:div.has-text-weight-light.is-size-6 "JSON 数组格式填写，比如："]
                    [:pre
                     (clj->js-str [{:name  "张三"
                                    :email "zhangsan@inspur.com"
                                    :role  "前端开发"
                                    :duty  "负责本特性的前端开发工作"}
                                   {:name  "李四"
                                    :email "lisi@inspur.com"
                                    :role  "后端开发"
                                    :duty  "负责本特性的后端开发工作"}])]]}]
       [:button.button.is-primary.is-large.mt-4
        {:style    {:align :right}
         :on-click (fn [_] (submit-feature-edit))} "提交更新"]])))