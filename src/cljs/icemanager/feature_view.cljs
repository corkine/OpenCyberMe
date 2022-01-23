(ns icemanager.feature-view
  (:require [icemanager.feature :as feature]
            [clojure.string :as string]
            [goog.string :as gstring]
            [reagent.core :as r]
            [reagent.dom :as rdom]))

(defn feature-head [title go]
  (r/create-class
    {:component-did-mount
     (fn [this]
       (when (= go title) (.scrollIntoView (rdom/dom-node this) true)))
     :reagent-render
     (fn [_] [:h3.notification.is-link.is-light.pt-2.pb-2.pl-3 title])}))

(defn feature-top [go]
  (r/create-class
    {:component-did-mount
     (fn [this]
       (when (= go nil) (.scrollTo js/window 0 0)))
     :reagent-render
     (fn [_] [:p ""])}))

(defn format-req-resp [in]
  (map (fn [{:keys [name type example description]}]
         (gstring/format "%-15s %-10s = %-15s %s"
                         name (str ": [" type "]")
                         (if (string/blank? example) "???" example)
                         (if (string/blank? description) "" (str ";; "description))))
       in))

(defn feature-view-content [feature-data go]
  [:<>
   [feature-top]
   [:div.hero.is-success.is-small
    {:style {:padding-left   :30px
             :padding-bottom :30px}}
    [feature/feature-card feature-data {:with-footer      false
                                        :with-description false
                                        :with-big-pic     true
                                        :with-edit        true}]]
   [:section.section>div.container>div.content
    (let [{:keys [description version rs_id info]} feature-data
          {:keys [uiRes designRes status developer implement review api
                  apiRes]} info]
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
       [feature-head "API 测试环境" go]
       [:div.ml-2
        (if-not (string/blank? apiRes)
          [:<>
           [:a {:href apiRes :target :_black}
            [:i.material-icons {:style {:vertical-align :-20%
                                        :margin-right   :3px}} "call_made"]
            "点此打开外部 API 测试环境"]]
          [:div "暂无本特性的外部接口测试环境"])]
       [feature-head "API 接口" go]
       [:div.ml-2
        (for [[index {:keys [name note path method request response]}]
              (map-indexed vector api)]
          ^{:key index}
          [:<>
           [:p.has-text-weight-bold.is-size-5.is-family-code.mt-5
            [:i.material-icons
             {:style {:vertical-align :-13%
                      :margin-right   :3px
                      :font-size      :21px}}
             "public"] method " " path]
           [:p.mb-1 "请求格式"]
           [:pre (string/join "\n" (format-req-resp request))]
           [:p.mb-1 "响应格式"]
           [:pre (string/join "\n" (format-req-resp response))]
           [:blockquote.is-size-7 [:i.material-icons {:style {:vertical-align :-25%
                                                              :font-size :20px
                                                              :margin-left :-10px
                                                              :margin-right   :3px}}
                                   "attach_file"] (str "备注：" note)]])]
       [:h3.notification.is-link.is-light.pt-2.pb-2.pl-3 "特性分解"]
       [:div.ml-2
        (for [[index {:keys [title content]}] (map-indexed vector implement)]
          ^{:key title}
          [:<>
           [:h5 " # " title " " [:span.tag.is-light {:style {:vertical-align :10%}}
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
        [feature/developer-card (if (vector? developer) developer [developer])
         {:href-link "#121212"}]]])]])