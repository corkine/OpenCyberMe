(ns icemanager.about
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [icemanager.modals :as modals]
            [goog.string :as gstring]
            [clojure.string :as string]))

(def version "alpha 0.1.3")

(def log (str "now: " version "
[2022-01-19]
搭建 luminus 项目，完成前端、后端和数据库框架
[2022-01-20]
修复了多个按钮在移动端的 UI 展示问题。
特性参与人员现在可以有多个，优化了人员展示和数据存储逻辑。
优化了交互逻辑，提供参与人员 JSON 输入合法性校验和反馈，特性修改接口调用成功和失败的弹窗。
提供了 API 调用的日志监控，提供服务统计接口和统计页面。
[2022-01-21]
实现了'参与人员'、'实施方案' 和 '评审记录' 功能。
实现了'特性展示页面'。
优化了'特性编辑'页面中'实施方案'，'评审记录' 为空时的逻辑，提供提示，不允许直接输入内容。
提供了'提愿望/BUG/建议' 的功能。
[2022-01-22]
提供了可视化 'API 接口调用' 最近变更统计功能。
提供 '新建特性' 和 '删除特性' 功能。
提供特性的 'API 接口' 编辑和展示功能。
[2022-01-23]
为 'API 接口调用' 可视化功能提供更好的展示格式。
实现了按照 '版本'，'状态'，'开发者' 从前端对特性进行过滤，且允许通过 URL 访问特性过滤条件列表。
解决了一个在 webkit 内核浏览器上由于 <select> 标签 on-click 导致无法过滤特性的 BUG。
为特性提供了 '外部 API 测试环境' 属性，如果有的话，则主页特性 card 提供按钮跳转，没有的话，则跳转到特性页 api 文档位置。

================================================
愿望清单：
- 实现一种对恶意操作系统的鉴别能力（已实现，不提供登录功能，提供 APi 调用审查功能）
- 提供对服务调用的统计（已实现）
- 提供对方案分解的支持以方便生成可用的 TR 文档（已实现）
- 使用更好的前端交互实现特性开发者表单，而非直接要求输入 JSON 数据（已实现）
- 提供对于特性评审的支持（已实现）
- 提供特性分享页面，方便查看特性信息，而不是直接打开就是编辑特性（已实现）
- 提供移除特性的能力（已实现）
- 实现新建 ICE 特性的能力（已实现）
- 实现特性 API 接口编辑功能（已实现）
- 实现根据项目筛选特性的能力：ICE 4.3 or ICE 5.0（已实现）
- 整合 devKit，根据数据自动生成 TR 文档，评审文档
- 根据特性的 API 接口自动生成 Swagger 文档（推迟，暂无必要）
"))

(defn about-page []
  [:div.hero.is-danger.is-fullheight-with-navbar
   [:section.section>div.container>div.content
    [:p.title "由 Corkine Ma 开发"]
    (let [usage @(rf/subscribe [:usage])
          server-back @(rf/subscribe [:wishlist-server-back])
          wish-list @(rf/subscribe [:wishlist])]
      [:<>
       [:p {:style {:margin-top :-20px}} (str "本服务已服务 " (:pv usage) " 人，共计 " (:uv usage) " 次")]
       [:pre (str log
                  "\n================================================\n数据库记录的请求：\n"
                  (string/join "\n"
                               (map (fn [line] (str "- " (:advice line)
                                                    " / 来自：" (:client line) "")) wish-list))
                  "\n\n================================================\n最近 10 次 API 更改：\n"
                  (string/join "\n"
                               (map #(gstring/format "%-15s %-4s %-20s %-s"
                                                     (:from %) (string/upper-case (:method %))
                                                     (:api %) (:time %))
                                    (:usage usage))))]
       [:div.mb-3
        (r/with-let
          [user (r/atom nil)
           kind (r/atom "愿望")
           advice (r/atom nil)
           error (r/atom nil)]
          [modals/modal-button :wishlist
           {:button {:class ["is-light" "mt-0"]}}
           (str "提愿望/建议/BUG")
           [:div {:style {:color "black"}}
            [:<>
             [:label.label {:for "user"} "称呼 *"]
             [:input.input {:type        :text
                            :id          :user
                            :value       (or @user "")
                            :placeholder "输入你的称呼"
                            :on-change   #(reset! user (.. % -target -value))}]
             [:label.label.mt-4 {:for "kind"} "类别 *"]
             [:div.select>select {:id        :kind
                                  :value     @kind
                                  :on-change #(reset! kind (.. % -target -value))}
              [:option {:value "愿望"} "愿望"]
              [:option {:value "建议"} "建议"]
              [:option {:value "BUG"} "BUG"]]
             [:label.label.mt-4 {:for "advice"} (str @kind " * (不少于 10 个字)")]
             [:textarea.textarea {:rows        4
                                  :value       (or @advice "")
                                  :id          :advice
                                  :on-change   #(reset! advice (.. % -target -value))
                                  :placeholder (str "输入你的" @kind)}]
             (when server-back
               [(if (= (:status server-back) :success)
                  :div.notification.is-success.mt-4
                  :div.notification.is-danger.mt-4) (str (:content server-back))])
             (when-let [message @error]
               [:div.notification.is-warning.mt-4 message])]]
           (let [is-success-call (and (not (nil? server-back))
                                      (= (:status server-back) :success))]
             [:button.button.is-primary.is-fullwidth
              {:on-click (if is-success-call
                           (fn [_]
                             (reset! error nil)
                             (reset! user nil)
                             (reset! kind "愿望")
                             (reset! advice nil)
                             (rf/dispatch [:clean-wishlist-server-back])
                             (rf/dispatch [:app/hide-modal :wishlist]))
                           (fn [_]
                             (reset! error nil)
                             (cond (nil? @user) (reset! error "称呼不能为空")
                                   (nil? @kind) (reset! error "类别不能为空")
                                   (nil? @advice) (reset! error (str @kind "不能为空"))
                                   (< (count @advice) 10) (reset! error (str @kind "少于 10 个字。"))
                                   :else (rf/dispatch [:send-wishlist {:client @user
                                                                       :kind   @kind
                                                                       :advice @advice}]))))}
              (if is-success-call "关闭" "提交")])])]])
    [:pre "Powered by clojure & clojureScript.
Build with shadow-cljs, cljs-ajax, reagent, re-frame, react, bulma, http-kit, muuntaja, swagger, ring, mount, conman, cprop, cheshire, selmer, google closure compiler.
Managed by lein, maven and npm.
Data stored with postgreSQL.
Developed with firefox and IDEA.
All Open Source Software, no evil."]]])