(ns icemanager.about
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [icemanager.modals :as modals]
            [goog.string :as gstring]
            [clojure.string :as string]))

(def version "beta 1.0.1")

(def log (str "now: " version "
[2022-02-03]
草稿设计，基于 ICE Manager 修改界面样式。
实现了主界面（位置）布局：可快速浏览位置以及位置内不同类别物品，可快速对物品进行操作（删除、彻底删除、打包、移动位置和编辑）。
[2022-02-04]
实现了位置物品详情的组件，可解析所有位置和物品的数据并自动展示 UI；实现了“新位置”、“新打包”和“物品入库”三个按钮和模态框的编写，进行了数据校验。
修复了一个因为父组件 overflow auto 导致的 dropdown 组件被遮盖问题。
实现了数据库数据模型，提供了主页所有位置物品的数据查询并进行了数据聚合。
[2022-02-05]
实现了“新键位置”、“新键外出”、“删除物品”、“隐藏物品” API 和弹窗提醒、ajax 反馈和 UI 更新，打通从前端界面到后端和数据库交互。
[2022-02-07]
修复了全局对话框在移动设备上因为折叠不弹窗的问题。
对 re-frame 事件进行抽象，提供了 ajax-flow 抽象事件流接口。
实现了位置信息的更新和删除。
实现了物品打包的最近包选择：点选准备、单击确认、点击拆包。
[2022-02-08]
实现了主界面快速打包删除（hover 显示），使用 flexbox grow 处理了打包点击和删除的区分。
实现了打包在物品选择打包界面中快速删除。
实现了主界面的位置、标签（暂时仅支持单个选项）和状态过滤、物品搜索。
实现了物品位置的移动。
修复了在移动设备因为 navbar z-index 不够高导致修改位置模态框（位于 navbar）穿模的问题。
实现了物品的修改。
修复了模态框滚动后重新打开滚动条不在顶部的问题（scrollIntoView）。
修复了当某个标签最后一个物品删除后显示空白的问题。
[2022-02-09]
Basic Auth 保护 API。

================================================
TODO:
- 实现每个位置选择标签的记忆和恢复（页面加载时从 localStorage 获取，之后通过 re-frame 处理事件和查询，离开页面存储）
- 实现资产、食品耗材和衣物的区分。
- 实现物品图片上传、预览和修改。

===============================================
愿望清单：
- 实现一个美观、易用的物品管理系统 ✔
- 可以方便的入库新物品，将物品在不同位置间迁移 ✔
- 简化外出物品检查流程，包括预备携带和已经打包的（80%）
- 简化外出后物品归位流程 ✔
"))

(defn about-page []
  [:div.hero.is-danger.is-fullheight-with-navbar
   [:section.section>div.container>div.content
    [:p.title "由 Corkine Ma 开发"]
    (let [usage @(rf/subscribe [:usage])
          server-back @(rf/subscribe [:wishlist-server-back])
          wish-list @(rf/subscribe [:wishlist])
          real-wish-list (filter #(= (:kind %) "愿望") wish-list)
          bug-list (filter #(= (:kind %) "BUG") wish-list)]
      [:<>
       [:p {:style {:margin-top :-20px}} (str "本服务已服务 " (:pv usage) " 人，共计 " (:uv usage) " 次")]
       [:pre (str log
                  "\n================================================\n数据库记录的请求：\n"
                  (string/join "\n"
                               (map (fn [line] (str "- " (:advice line)
                                                    " / 来自：" (:client line) "")) real-wish-list))
                  "\n\n================================================\n数据库记录的 BUG：\n"
                  (string/join "\n"
                               (map (fn [line] (str "- " (:advice line)
                                                    " / 来自：" (:client line) "")) bug-list))
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