(ns icemanager.pages
  (:require
    [day8.re-frame.http-fx]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [markdown.core :refer [md->html]]
    [icemanager.events]
    [icemanager.feature :as feature]
    [icemanager.about :refer [log about-page]]
    [icemanager.feature-view :as feature-view]
    [icemanager.feature-edit :as feature-edit]
    [clojure.string :as string]))

(defn top-point []
  (r/create-class
    {:component-did-mount
     (fn [this]
       (.scrollTo js/window 0 0)
       #_(when true (.scrollIntoView (rdom/dom-node this) true)))
     :reagent-render
     (fn [_] [:div ""])}))

(defn place-card []
  [:div.card.my-5
   [:div.card-content.columns
    [:div.column.is-3
     [:p.title
      [:span {:on-click #(rf/dispatch [:common/navigate! :feature-view
                                       {:rs-id (string/lower-case "11")}])
              :style    {:cursor       :pointer
                         :margin-right :10px}}
       "抽屉 #1"]
      [:span.tag.is-light.is-rounded
       [:span.icon {:style {:margin-right :-4px
                            :margin-left :-9px}} [:i.fa.fa-map-marker {:aria-hidden "true"}]]
       [:span "拿铁公寓"]]]
     [:p [:span.icon {:style {:margin-right :0px
                              :margin-left :-3px}}
          [:i.fa.fa-info-circle {:aria-hidden "true"}]]
      [:span.has-text-dark-lighter "橱柜右侧最上面的抽屉"]]
     [:p [:span.icon {:style {:margin-right :0px
                              :margin-left :-3px}}
          [:i.fa.fa-clock-o {:aria-hidden "true"}]]
      [:span.has-text-dark-lighter "2022年2月3日"]]]
    [:div.column
     [:nav
      [:p.panel-tabs.is-justify-content-start.pl-1
       [:a.is-active "#工具"]
       [:a "#厨房清洁"]
       [:a "#应急医药"]
       [:a "#身份证件"]
       [:a {:style {:padding "5px 0 5px 0"}} [:span.icon-text [:span.icon [:i.fa.fa-tags {:aria-hidden "true"}]] [:span "所有标签"]]]]
      [:div.columns.mt-2
       [:div.column.my-0.py-0
        [:a.panel-block.is-active
         [:span.tag.mr-3.is-success.is-light "活跃"]
         "钱包/社保卡/身份证"
         [:span.has-text-grey-light.hover-show {:style {:margin-left :auto}}
          [:span.icon [:i.fa.fa-share]]
          [:span.icon [:i.fa.fa-cube]]
          [:span.icon [:i.fa.fa-pencil]]
          [:span.icon [:i.fa.fa-ban]]
          [:span.icon.has-text-danger [:i.fa.fa-trash]]]]
        [:a.panel-block
         [:span.tag.mr-3.is-light "收纳"]
         "螺丝刀套装/钳子"
         [:span.has-text-grey-light.hover-show {:style {:margin-left :auto}}
          [:span.icon [:i.fa.fa-share]]
          [:span.icon [:i.fa.fa-cube]]
          [:span.icon [:i.fa.fa-pencil]]
          [:span.icon [:i.fa.fa-ban]]
          [:span.icon.has-text-danger [:i.fa.fa-trash]]]]
        [:a.panel-block
         [:span.tag.mr-3.is-light "收纳"]
         "百洁布"
         [:span.has-text-grey-light.hover-show {:style {:margin-left :auto}}
          [:span.icon [:i.fa.fa-share]]
          [:span.icon [:i.fa.fa-cube]]
          [:span.icon [:i.fa.fa-pencil]]
          [:span.icon [:i.fa.fa-ban]]
          [:span.icon.has-text-danger [:i.fa.fa-trash]]]]
        [:a.panel-block
         [:span.tag.mr-3.is-success.is-light "活跃"]
         "电池若干"
         [:span.tag.is-warning.is-small.is-rounded.ml-2.is-light "打包 #20211203" [:button.delete.is-small]]
         [:span.has-text-grey-light.hover-show {:style {:margin-left :auto}}
          [:span.icon [:i.fa.fa-share]]
          [:span.icon [:i.fa.fa-cube]]
          [:span.icon [:i.fa.fa-pencil]]
          [:span.icon [:i.fa.fa-ban]]
          [:span.icon.has-text-danger [:i.fa.fa-trash]]]]
        [:a.panel-block
         [:span.tag.mr-3.is-danger.is-light "移除"]
         "布洛芬 有效期至 2023-02-03"
         [:span.has-text-grey-light.hover-show {:style {:margin-left :auto}}
          [:span.icon [:i.fa.fa-share]]
          [:span.icon [:i.fa.fa-cube]]
          [:span.icon [:i.fa.fa-pencil]]
          [:span.icon [:i.fa.fa-ban]]
          [:span.icon.has-text-danger [:i.fa.fa-trash]]]]
        [:a.panel-block
         [:span.tag.mr-3.is-success.is-light "活跃"]
         "感冒灵颗粒"
         [:span.has-text-grey-light.hover-show {:style {:margin-left :auto}}
          [:span.icon [:i.fa.fa-share]]
          [:span.icon [:i.fa.fa-cube]]
          [:span.icon [:i.fa.fa-pencil]]
          [:span.icon [:i.fa.fa-ban]]
          [:span.icon.has-text-danger [:i.fa.fa-trash]]]]]
       [:div.column.my-0.py-0
        [:a.panel-block.is-active
         [:span.tag.mr-3.is-success.is-light "活跃"]
         "钱包/社保卡/身份证"
         [:span.has-text-grey-light.hover-show {:style {:margin-left :auto}}
          [:span.icon [:i.fa.fa-share]]
          [:span.icon [:i.fa.fa-cube]]
          [:span.icon [:i.fa.fa-pencil]]
          [:span.icon [:i.fa.fa-ban]]
          [:span.icon.has-text-danger [:i.fa.fa-trash]]]]
        [:a.panel-block
         [:span.tag.mr-3.is-light "收纳"]
         "螺丝刀套装/钳子"
         [:span.has-text-grey-light.hover-show {:style {:margin-left :auto}}
          [:span.icon [:i.fa.fa-share]]
          [:span.icon [:i.fa.fa-cube]]
          [:span.icon [:i.fa.fa-pencil]]
          [:span.icon [:i.fa.fa-ban]]
          [:span.icon.has-text-danger [:i.fa.fa-trash]]]]
        [:a.panel-block
         [:span.tag.mr-3.is-light "收纳"]
         "百洁布"
         [:span.has-text-grey-light.hover-show {:style {:margin-left :auto}}
          [:span.icon [:i.fa.fa-share]]
          [:span.icon [:i.fa.fa-cube]]
          [:span.icon [:i.fa.fa-pencil]]
          [:span.icon [:i.fa.fa-ban]]
          [:span.icon.has-text-danger [:i.fa.fa-trash]]]]
        [:a.panel-block
         [:span.tag.mr-3.is-success.is-light "活跃"]
         "电池若干"
         [:span.tag.is-warning.is-small.is-rounded.ml-2.is-light "打包 #20211203" [:button.delete.is-small]]
         [:span.has-text-grey-light.hover-show {:style {:margin-left :auto}}
          [:span.icon [:i.fa.fa-share]]
          [:span.icon [:i.fa.fa-cube]]
          [:span.icon [:i.fa.fa-pencil]]
          [:span.icon [:i.fa.fa-ban]]
          [:span.icon.has-text-danger [:i.fa.fa-trash]]]]
        [:a.panel-block
         [:span.tag.mr-3.is-danger.is-light "移除"]
         "布洛芬 有效期至 2023-02-03"
         [:span.has-text-grey-light.hover-show {:style {:margin-left :auto}}
          [:span.icon [:i.fa.fa-share]]
          [:span.icon [:i.fa.fa-cube]]
          [:span.icon [:i.fa.fa-pencil]]
          [:span.icon [:i.fa.fa-ban]]
          [:span.icon.has-text-danger [:i.fa.fa-trash]]]]
        [:a.panel-block
         [:span.tag.mr-3.is-success.is-light "活跃"]
         "感冒灵颗粒"
         [:span.has-text-grey-light.hover-show {:style {:margin-left :auto}}
          [:span.icon [:i.fa.fa-share]]
          [:span.icon [:i.fa.fa-cube]]
          [:span.icon [:i.fa.fa-pencil]]
          [:span.icon [:i.fa.fa-ban]]
          [:span.icon.has-text-danger [:i.fa.fa-trash]]]]]]]]]])

(defn home-page []
  [:<>
   [top-point]
   [feature/home-filter]
   (let [feature-filtered @(rf/subscribe [:get-filtered-features])]
     (if-not (empty? feature-filtered)
       [:div.container>div.content.mx-3.is-full {:style {:margin-top "0px"}}
        (for [data feature-filtered]
          ^{:key (:id data)}
          [place-card]
          #_[feature/feature-card data {:with-footer      true
                                      :with-description true
                                      :with-edit        true}])]
       [:div.hero.is-small.pl-0.pr-0
        [:div.hero-body
         [:div.container.has-text-centered
          [:h3.subtitle.mt-6
           "Oops... 暂无符合条件的模块"]]]]))])

(defn feature-page []
  (let [feature-data @(rf/subscribe [:current-feature])]
    [:<>
     [top-point]
     [:div {:style {:background-color :#48c774}}
      [:div.hero.is-success.is-small.container.is-fullhd
       {:style {:padding-left   :30px
                :padding-bottom :30px}}
       [feature/feature-card feature-data {:with-footer      false
                                           :with-description true}]]]
     [:section.section>div.container>div.content
      [feature-edit/feature-form feature-data]]]))

(defn feature-view-page []
  (let [feature-data @(rf/subscribe [:current-feature])
        go @(rf/subscribe [:view-go])]
    [:<>
     [feature-view/feature-view-content feature-data go]]))