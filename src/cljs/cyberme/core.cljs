(ns cyberme.core
  (:require
    [day8.re-frame.http-fx]
    [reagent.dom :as rdom]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [markdown.core :refer [md->html]]
    [reitit.core :as reitit]
    [reitit.frontend.easy :as rfe]
    [cyberme.util.ajax :as ajax]
    [cyberme.util.upload :as up]
    [cyberme.util.events :as events]
    [cyberme.util.request :as req]
    [cyberme.util.form :refer [dialog]]
    [cyberme.place.request :as req-place]
    [cyberme.good.request :as req-good]
    [cyberme.work.request :as req-work]
    [cyberme.diary.request :as req-diary]
    [cyberme.dashboard.request :as req-dash]
    [cyberme.diary.request :as req-daily]
    [cyberme.place.new :as place-new]
    [cyberme.place.edit :as place-edit]
    [cyberme.good.package-new :as package-new]
    [cyberme.good.new :as good-new]
    [cyberme.good.edit :as good-edit]
    [cyberme.about :refer [log about-page]]
    [cyberme.router :as share]
    [cyberme.modals :as modals]
    [cyberme.login.core :as login]
    [cyberme.validation :as va]
    [clojure.string :as str]
    [cljs-time.format :as format]
    [cljs-time.core :as t])
  (:import goog.History))

(defn nav-link [uri title page]
  [:a.navbar-item
   {:href  uri
    :class (when (= page @(rf/subscribe [:common/page])) :is-active)}
   title])

(defn global-info []
  (let [{:keys [message pre-message callback callback-fn]} @(rf/subscribe [:global/notice])]
    [modals/modal-card :notice "提示"
     [:<>
      (when pre-message
        [:pre.has-text-black pre-message])
      (when message
        [:p.has-text-black message])]
     [:button.button.is-primary.is-fullwidth
      {:on-click (fn [_]
                   (rf/dispatch [:global/notice-clean])
                   (when-not (nil? callback)
                     (if (vector? (first callback))
                       (doseq [c callback]
                         (rf/dispatch c))
                       (do
                         (rf/dispatch callback))))
                   (when-not (nil? callback-fn)
                     (callback-fn))
                   (rf/dispatch [:app/hide-modal :notice]))}
      "确定"]
     #(rf/dispatch [:global/notice-clean])]))

(defn note-add-dialog
  []
  (dialog :note-add-dialog
          "添加新快速笔记"
          [[:content "笔记内容" "输入此笔记的内容" {:type :textarea :attr {:rows 4}}]]
          "确定"
          #(if-let [err (va/validate! @%1 [[:content va/required]])]
             (reset! %2 err)
             (rf/dispatch [:note/add (merge @%1
                                            {:from        "CyberMe Web Client"
                                             :liveSeconds 100})]))
          {:subscribe-ajax    [:note/add-data]
           :call-when-exit    [[:note/add-data-clean]]
           :call-when-success [[:note/add-data-clean]]}))

(req/ajax-flow {:call   :note/add
                :uri-fn #(str "/cyber/note")
                :is-post true
                :data   :note/add-data
                :clean  :note/add-data-clean})

(defn blue-add-dialog
  []
  (dialog :set-blue-dialog
          "添加 Blue 信息"
          [[:day "日期" "今天填写 0，昨天填写 -1，以此类推"]
           [:blue "确认?" "默认为 true，可填写 false" {:type :select :selects ["是的" "取消"]}]]
          "确定"
          #(if-let [err (va/validate! @%1 [[:day va/required] [:blue va/required]])]
             (reset! %2 err)
             (rf/dispatch [:dashboard/set-blue @%1]))
          {:subscribe-ajax    [:dashboard/set-blue-data]
           :call-when-exit    [[:dashboard/set-blue-data-clean]]
           :call-when-success [[:dashboard/set-blue-data-clean]
                               [:dashboard/recent]]
           :origin-data {:day (if (> (t/hour (t/time-now)) 12) 0 -1) :blue "是的"}}))

;添加 Blue 信息
(req/ajax-flow {:call   :dashboard/set-blue
                :uri-fn #(let [day-delta (js/parseInt (:day %))
                               date (format/unparse-local (format/formatter "yyyy-MM-dd")
                                                          (t/plus (t/time-now) (t/period :days day-delta)))
                               is-blue? (str/includes? (or (:blue %) "是") "是")]
                           (str "/cyber/blue/update?day=" date "&blue=" is-blue?))
                :data   :dashboard/set-blue-data
                :clean  :dashboard/set-blue-data-clean})

(defn navbar []
  (r/with-let [expanded? (r/atom false)]
              [:nav.navbar.is-info {:style {:z-index :5}}
               ;移动设备place-edit穿模，select 箭头 z-index 为 4，这里要设置为5
               ;默认 navbar 应该很高 z-index，其导致菜单被遮盖，5 的设置避免了此问题
               [:div.container
                [global-info]
                [place-edit/place-edit-holder]
                [good-edit/edit-good-holder]
                [note-add-dialog]
                [blue-add-dialog]
                [:div {:style {:width :0px :height :0px :overflow :hidden}}
                 [place-new/new-place-btn]
                 [package-new/new-package-btn]
                 [good-new/new-good-btn]
                 [login/login-button]]
                [:div.navbar-brand
                 [:a.icon-text.navbar-item.has-text-white {:href "/"}
                  [:span.icon [:i.fa.fa-ravelry.mr-1]]
                  [:span.has-text-bold.is-size-5.has-text-white "CyberMe"]]
                 [:span.navbar-burger.burger
                  {:data-target :nav-menu
                   :on-click    #(swap! expanded? not)
                   :class       (when @expanded? :is-active)}
                  [:span] [:span] [:span]]]
                [:div#nav-menu.navbar-menu
                 {:class (when @expanded? :is-active)}
                 [:div.navbar-start
                  [nav-link "/" "总览" :dashboard]
                  [nav-link "/work" "工作" :work]
                  [nav-link "/diary" "日记" :diary]
                  #_[nav-link "/cook" "厨记" :cook]
                  [nav-link "/properties" "物品" :properties]
                  #_[nav-link "/clothes" "衣物" :clothes]
                  #_[nav-link "/foods" "耗材" :foods]
                  [nav-link "/about" "关于" :about]]
                 [:div.navbar-end {:style {:margin-right :15px}}
                  [:div.navbar-item.is-hoverable.mx-0
                   (let [switch @(rf/subscribe [:paste-switch])
                         status @(rf/subscribe [:paste-status])
                         message (if switch
                                   "关闭页面任意位置图床上传功能？"
                                   "需要开启页面任意位置图床上传功能吗？")]
                     [:a.has-text-white.dui-tips
                      {:on-click     #(rf/dispatch [:global/notice
                                                    {:message  message
                                                     :callback [:set-paste-switch]}])
                       :data-tooltip "图床全局监听"}
                      (condp = status
                        :success [:i.fa.fa-check-circle]
                        :failed [:i.fa.fa-times-circle]
                        (if switch
                          [:i.fa.fa-exchange]
                          [:i.fa.fa-info-circle]))])]
                  [:div.navbar-item.has-dropdown.is-hoverable.mx-0
                   [:a.navbar-link "操作"]
                   [:div.navbar-dropdown.is-boxed
                    [:a.navbar-item
                     {:on-click #(rf/dispatch [:app/show-modal :create-new-place])}
                     "新建位置"]
                    [:a.navbar-item
                     {:on-click #(rf/dispatch [:app/show-modal :create-new-package])}
                     "新建打包"]
                    [:a.navbar-item
                     {:on-click #(rf/dispatch [:app/show-modal :create-new-good])}
                     "物品入库"]
                    [:a.navbar-item
                     {:on-click #(rf/dispatch [:app/show-modal :note-add-dialog])}
                     "新建笔记"]
                    [:a.navbar-item
                     {:on-click #(rf/dispatch [:note/last])}
                     "最近笔记"]
                    [:a.navbar-item
                     {:on-click #(rf/dispatch [:dashboard/make-clean])}
                     "标记清洁"]
                    [:a.navbar-item
                     {:on-click #(rf/dispatch [:app/show-modal :set-blue-dialog])}
                     "标记 Blue"]
                    [:a.navbar-item
                     {:on-click #(rf/dispatch [:dashboard/todo-sync])}
                     "同步待办"]]]
                  (let [{login-hint :user} @(rf/subscribe [:api-auth])
                        login-hint (or login-hint "登录")]
                    [:div.navbar-item.mx-0
                     [:div.is-clickable
                      {:on-click #(rf/dispatch [:app/show-modal :login-info-set])}
                      [:span.icon-text
                       [:span.icon [:i.fa.fa-user {:style {:margin-left :-10px}}]]
                       [:span {:style {:margin-left :-5px}} login-hint]]]
                     #_[:button.button.is-info
                        {:on-click #(rf/dispatch [:app/show-modal :login-info-set])}
                        [:span.icon-text
                         [:span.icon [:i.fa.fa-user {:style {:margin-right :-10px}}]]
                         [:span " 登录"]]]])]]]]))

(defn page []
  (if-let [page @(rf/subscribe [:common/page])]
    (let [hidden_nav (str/includes? (str page) "psy")]
      [:div
       (if-not hidden_nav [navbar])
       [page]])))

(defn navigate! [match _]
  (rf/dispatch [:common/navigate match]))

(defn start-router! []
  (rfe/start!
    #_router
    (reitit/router (share/share-router))
    navigate!
    {:use-fragment false}))

(defn ^:dev/after-load mount-components []
  (rf/clear-subscription-cache!)
  (rdom/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (start-router!)
  (set! (.-onpaste js/document)
        (fn [event]
          (let [target (.-clipboardData event)
                files (.-files target)
                switch (rf/subscribe [:paste-switch])]
            (println "paste" files (.-length files) ", switch is" @switch)
            (if (and (> (.-length files) 0) @switch)
              (up/upload-file
                files
                #(do
                   (println "Upload " %1)
                   (if (= (:status %1) 1)
                     (do
                       (rf/dispatch [:set-paste-status :success])
                       (.writeText (.-clipboard js/navigator)
                                   (str "![](" (:data %1) ")"))
                       (js/setTimeout (fn [_] (rf/dispatch [:set-paste-status nil])) 2000))
                     (do
                       (rf/dispatch [:set-paste-status :failed])
                       (rf/dispatch [:global/notice {:message (:message %1)}])))))
              (do
                (rf/dispatch [:set-paste-status :failed])
                (js/setTimeout (fn [_] (rf/dispatch [:set-paste-status nil])) 2000))))))
  (ajax/load-interceptors!)
  (mount-components))
