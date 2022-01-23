(ns icemanager.feature
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.string :as string]
            [icemanager.modals :as modals]
            [goog.string :as gstring]
            [reagent.core :as r]))

(def ice-versions ["ICE 5.0" "ICE 4.3" "ICE 4.2" "ICE 4.1" "ICE 4.0"
                   "早期 ICE 版本" "未来 ICE 版本"])

(def ice-status ["TR1", "TR2", "TR3", "TR4", "TR5",
                 "尚未开始" "正在开发" "正在测试" "开发完毕"])

(def http-method ["GET" "POST" "DELETE" "PATCH" "PUT"])

(def http-param-type ["string", "integer", "double", "map", "array", "any"])

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
                    {:keys [with-footer with-description with-big-pic with-edit]}]
  [(if with-footer :div.box.columns.mt-5
                   :div.columns.mt-5)
   [:div.column {:style {:z-index :2}}
    [:p.title
     [:span {:on-click #(rf/dispatch [:common/navigate! :feature-view
                                      {:rs-id (string/lower-case rs_id)}])
             :style    {:cursor       :pointer
                        :margin-right (if-not with-edit :10px :0px)}}
      title]
     (when with-edit
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
                :z-index   :0
                :opacity   :0.2
                }} "camera"]])])

(defn feature-filter []
  (let [versions @(rf/subscribe [:all-version])
        developers @(rf/subscribe [:all-developer])
        statuses @(rf/subscribe [:all-status])
        filter @(rf/subscribe [:filter])]
    [:div.hero.is-warning.is-small
     [:div.hero-body
      [:p.title "ICE 特性列表"]
      [:div.columns
       [:div.column.is-6
        [:p "纳入系统管理的 ICE 特性列表"]]
       [:div.column.is-6.has-text-right.has-text-left-mobile
        {:style {:margin-top "-8px"}}
        [:div.select.is-small.is-warning.mr-2.mt-1
         [:select
          {:on-change (fn [e]
                        (let [sel-o (-> e .-target .-value)
                              mg (if (= sel-o "所有版本")
                                   (dissoc filter :version)
                                   (assoc filter :version sel-o))]
                          (rf/dispatch [:set-filter mg])
                          (reitit.frontend.easy/replace-state :home nil mg)))
           :value (or (:version filter) "")
           :id :version}
          [:option "所有版本"]
          (for [version versions]
            ^{:key version}
            [:option {:value version} version])]]
        [:div.select.is-small.is-warning.mr-2.mt-1>select
         {:on-change (fn [e] (let [sel-o (-> e .-target .-value)
                                   mg (if (= sel-o "任意状态")
                                        (dissoc filter :status)
                                        (assoc filter :status sel-o))]
                               (rf/dispatch [:set-filter mg])
                               (reitit.frontend.easy/replace-state :home nil mg)))
          :value (or (:status filter) "")}
         [:option "任意状态"]
         (for [status statuses]
           ^{:key status}
           [:option {:value status} status])]
        [:div.select.is-small.is-warning.mr-2.mt-1>select
         {:on-change (fn [e] (let [sel-o (-> e .-target .-value)
                                   mg (if (= sel-o "任意人员")
                                        (dissoc filter :contains)
                                        (assoc filter :contains sel-o))]
                               (rf/dispatch [:set-filter mg])
                               (reitit.frontend.easy/replace-state :home nil mg)))
          :value (or (:contains filter) "")}
         [:option "任意人员"]
         (for [developer developers]
           ^{:key developer}
           [:option {:value developer} (str "包含:" developer)])]]]]]))