(ns icemanager.core
  (:require
    [day8.re-frame.http-fx]
    [reagent.dom :as rdom]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [goog.events :as events]
    [goog.history.EventType :as HistoryEventType]
    [markdown.core :refer [md->html]]
    [icemanager.ajax :as ajax]
    [icemanager.events]
    [reitit.core :as reitit]
    [reitit.frontend.easy :as rfe]
    [clojure.string :as string]
    [icemanager.feature :as feature]
    [icemanager.request :as req])
  (:import goog.History))

(def log "[2022-01-19]
搭建 luminus 项目，完成前端、后端和数据库框架
[2022-01-20]
修复了多个按钮在移动端的 UI 展示问题。
特性参与人员现在可以有多个，优化了人员展示和数据存储逻辑。
优化了交互逻辑，提供参与人员 JSON 输入合法性校验和反馈，特性修改接口调用成功和失败的弹窗。
提供了 API 调用的日志监控，提供服务统计接口和统计页面。
")

(defn nav-link [uri title page]
  [:a.navbar-item
   {:href  uri
    :class (when (= page @(rf/subscribe [:common/page])) :is-active)}
   title])

(defn navbar []
  (r/with-let [expanded? (r/atom false)]
              [:nav.navbar.is-info>div.container
               [:div.navbar-brand
                [:a.navbar-item {:href "/" :style {:font-weight :bold}} "ICE 特性管理器"]
                [:span.navbar-burger.burger
                 {:data-target :nav-menu
                  :on-click    #(swap! expanded? not)
                  :class       (when @expanded? :is-active)}
                 [:span] [:span] [:span]]]
               [:div#nav-menu.navbar-menu
                {:class (when @expanded? :is-active)}
                [:div.navbar-start
                 [nav-link "#/" "主页" :home]
                 #_[nav-link "#/feature" "特性" :feature]
                 [nav-link "#/about" "关于" :about]]]]))

(defn about-page []
  [:div.hero.is-danger.is-fullheight-with-navbar
   [:div.hero-body]
   [:section.section>div.container>div.content
    {:style {:margin-bottom "200px"}}
    [:p.title "由 Corkine Ma 开发"]
    (let [usage @(rf/subscribe [:usage])]
      [:p {:style {:margin-top :-20px}} (str "本服务已服务 " (:pv usage) " 人，共计 " (:uv usage) " 次")])
    [:pre log]
    [:pre "Powered by clojure & clojureScript。
Build with shadow-cljs, cljs-ajax, reagent, re-frame, react, bulma, http-kit, muuntaja, swagger, ring, mount, conman, cprop, cheshire, selmer, google closure compiler。
Managed by lein, maven and npm.
Data stored with postgreSQL.
Developed with firefox and IDEA.
All Open Source Software, no evil."]
    [:img {:src "/img/warning_clojure.png"}]]])

(defn home-page []
  [:<>
   [:div.hero.is-warning.is-small
    [:div.hero-body
     [:p.title "ICE 特性列表"]
     [:p "当前正在开发的 ICE 特性列表"]]]
   [:section.section>div.container>div.content {:style {:margin-top "-20px"}}
    (for [data @(rf/subscribe [:get-features])]
      ^{:key (:id data)}
      [feature/feature-card data {:with-footer      true
                                  :with-description true}])]])

(defn feature-page []
  (let [feature-data @(rf/subscribe [:current-feature])]
    [:<>
     [:div.hero.is-success.is-small
      {:style {:padding-left   :30px
               :padding-bottom :30px}}
      [feature/feature-card feature-data {:with-footer      false
                                          :with-description true}]]
     [:section.section>div.container>div.content
      [feature/feature-form feature-data]]]))

(defn page []
  (if-let [page @(rf/subscribe [:common/page])]
    [:div
     [navbar]
     [page]]))

(defn navigate! [match _]
  (rf/dispatch [:common/navigate match]))

(def router
  (reitit/router
    [["/" {:name        :home
           :view        #'home-page
           :controllers [{:start (fn [_] (rf/dispatch [:fetch-features]))}]}]
     ["/feature/:rs-id/edit" {:name        :feature
                              :view        #'feature-page
                              :controllers [{:parameters {:path [:rs-id]}
                                             :start      (fn [{{:keys [rs-id]} :path}]
                                                           (rf/dispatch [:fetch-feature rs-id]))}]}]
     ["/about" {:name :about
                :view #'about-page
                :controllers [{:start (fn [_] (rf/dispatch [:fetch-usage]))}]}]]))

(defn start-router! []
  (rfe/start!
    router
    navigate!
    {}))

;; -------------------------
;; Initialize app
(defn ^:dev/after-load mount-components []
  (rf/clear-subscription-cache!)
  (rdom/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (start-router!)
  (ajax/load-interceptors!)
  (mount-components))
