(ns cyberme.fake
  (:require [re-frame.core :as rf]
            [clojure.string :as string]))

(defn demo-place-card []
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
                            :margin-left  :-9px}} [:i.fa.fa-map-marker {:aria-hidden "true"}]]
       [:span "拿铁公寓"]]]
     [:p [:span.icon {:style {:margin-right :0px
                              :margin-left  :-3px}}
          [:i.fa.fa-info-circle {:aria-hidden "true"}]]
      [:span.has-text-dark-lighter "橱柜右侧最上面的抽屉"]]
     [:p [:span.icon {:style {:margin-right :0px
                              :margin-left  :-3px}}
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
         [:span.dui-tips {:data-tooltip "HELLO"} "钱包/社保卡/身份证"]
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

(def fake-place-data
  {:id 1 :place "抽屉 #1" :location "拿铁公寓" :description "衣帽柜最上面的抽屉"
   :items
   [{:id     1 :uid "CM123" :name "钱包/社保卡/身份证" :note "很长的信息信息信息信息信息" :createAt nil :lastUpdate nil
     :status "活跃" :labels ["身份"] :packages []}
    {:id     2 :uid nil :name "螺丝刀套装/钳子" :note nil :createAt nil :lastUpdate nil
     :status "收纳" :labels ["工具"] :packages []}
    {:id     3 :uid "CM124" :name "百洁布" :note nil :createAt nil :lastUpdate nil
     :status "活跃" :labels ["药物"] :packages []}
    {:id     4 :uid "CM125" :name "电池若干" :note nil :createAt nil :lastUpdate nil
     :status "移除" :labels ["电子"] :packages [{:id 1 :name "过年回家 2022.02.04" :status 0}]}
    {:id     5 :uid "CM126" :name "布洛芬" :note "有效期至 2023-02-03" :createAt nil :lastUpdate nil
     :status "活跃" :labels ["药物"] :packages []}
    {:id     6 :uid "CM127" :name "感冒灵颗粒" :note nil :createAt nil :lastUpdate nil
     :status "活跃" :labels ["药物"] :packages []}
    {:id     7 :uid "CM128" :name "钱包/社保卡/身份证" :note nil :createAt nil :lastUpdate nil
     :status "收纳" :labels ["身份"] :packages []}
    {:id     8 :uid "CM129" :name "螺丝刀套装/钳子" :note nil :createAt nil :lastUpdate nil
     :status "活跃" :labels ["工具"] :packages [{:id 1 :name "过年回家 2022.02.04" :status 1}]}
    {:id     9 :uid "CM133" :name "百洁布" :note nil :createAt nil :lastUpdate nil
     :status "收纳" :labels ["厨具"] :packages []}
    {:id     10 :uid "CM134" :name "电池若干" :note nil :createAt nil :lastUpdate nil
     :status "活跃" :labels ["电子"] :packages []}
    {:id     11 :uid "CM135" :name "布洛芬 有效期至 2023-02-03" :note nil :createAt nil :lastUpdate nil
     :status "收纳" :labels ["药物"] :packages [{:id 1 :name "海南旅行 2022.02.04" :status 0}]}
    {:id     12 :uid "CM136" :name "感冒灵颗粒" :note nil :createAt nil :lastUpdate nil
     :status "活跃" :labels ["药物"] :packages []}]})