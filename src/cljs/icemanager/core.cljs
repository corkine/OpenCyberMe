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
    [icemanager.request :as req])
  (:import goog.History))

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
                 [nav-link "#/feature" "特性" :feature]
                 [nav-link "#/about" "关于" :about]]]]))

(defn about-page []
  [:div.hero.is-danger.is-fullheight-with-navbar
   [:div.hero-body]
   [:section.section>div.container>div.content
    {:style {:margin-bottom "200px"}}
    [:p.title "由 Corkine Ma 开发"]
    [:p.subtitle "Powered By clojure, luminus, posgreSQL"]
    [:img {:src "/img/warning_clojure.png"}]]])

(defn feature-page []
  [:<>
   [:div.hero.is-success.is-small
    [:div.hero-body
     [:p.title "特性：配置时光机"]
     [:p "RS 编号: TIMEMACHINE"]
     [:p "引入版本：ICE 5.0"]
     [:p "当前状态：正在开发"]
     [:p "维护人员："
      [:a {:href "mailto:mazhangjing@inspur.com"} "马章竞"]]]]
   [:section.section>div.container>div.content
    [:p "特性页面"]]])

(defn home-page []
  [:<>
   [:div.hero.is-warning.is-small
    [:div.hero-body
     [:p.title "ICE 特性列表"]
     [:p "当前正在开发的 ICE 特性列表"]]]
   [:section.section>div.container>div.content {:style {:margin-top "-20px"}}
    (for [{:keys [id rs_id title version description update_at info]} @(rf/subscribe [:get-features])]
      ^{:key id}
      [:div.box.columns.mt-5
       [:div.column
        [:p.title title
         [:span.tags.has-addons
          {:style {:display        :inline-block
                   :margin-left    :10px
                   :vertical-align :-10%}}
          [:span.tag.is-link rs_id]
          [:span.tag.is-dark version]]]
        (let [{{:keys [name email]} :developer
               status               :status} info]
          [:<>
           [:p (str "当前状态：" status)
            [:span {:style {:display        :inline-block
                            :margin-left    :10px
                            :vertical-align :6%}}
             (if (-> info :designRes)
               [:a.ml-1 {:href (:designRes info)}
                [:span.tag.is-rounded "设计图"]])
             (if (-> info :uiRes)
               [:a.ml-1 {:href (:uiRes info)}
                [:span.tag.is-rounded "UI 渲染图"]])]]
           [:p "维护人员："
            [:a {:href (str "mailto:" email)} name]]])
        [:p "最后更新：" update_at]
        [:button.button
         [:i.material-icons {:style {:margin-top   :4px
                                     :margin-right :3px
                                     :margin-left  :-7px}} "description"]
         [:span "TR 文档"]]
        [:button.button.ml-3
         [:i.material-icons {:style {:margin-top   :4px
                                     :margin-right :3px
                                     :margin-left  :-7px}} "picture_as_pdf"]
         [:span "导出 PDF"]]
        [:button.button.ml-3
         [:i.material-icons {:style {:margin-top   :4px
                                     :margin-right :3px
                                     :margin-left  :-7px}} "public"]
         [:span "Swagger 接口"]]
        ]
       [:div.tile.notification.column
        [:div.card-content
         [:div.content (if (clojure.string/blank? description)
                         "尚无介绍.." description)]]]])]])

#_(defn home-page []
    [:section.section>div.container>div.content
     (when-let [docs @(rf/subscribe [:docs])]
       [:div {:dangerouslySetInnerHTML {:__html (md->html docs)}}])])

(defn page []
  (if-let [page @(rf/subscribe [:common/page])]
    [:div
     [navbar]
     [page]]))

(defn navigate! [match _]
  (rf/dispatch [:common/navigate match]))

(def router
  (reitit/router
    [#_["/" {:name        :home
             :view        #'home-page
             :controllers [{:start (fn [_] (rf/dispatch [:page/init-home]))}]}]
     ["/" {:name        :home
           :view        #'home-page
           :controllers [{:start (fn [_] (rf/dispatch [:fetch-features]))}]}]
     ["/feature" {:name :feature
                  :view #'feature-page}]
     ["/about" {:name :about
                :view #'about-page}]]))

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
