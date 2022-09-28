(ns cyberme.pages
  (:require
    [day8.re-frame.http-fx]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [markdown.core :refer [md->html]]
    [cyberme.util.events]
    [cyberme.about :refer [log about-page]]
    [cyberme.place.filter :as place-filter]
    [cyberme.place.core :as place]
    [cyberme.work.core :as work]
    [cyberme.work.whole :as work-all]
    [cyberme.diary.core :as diary]
    [cyberme.dashboard.core :as dashboard]
    [cyberme.dashboard.plan :as plan]
    [cyberme.file :as file]
    [cyberme.psych.main :as exp]
    [clojure.string :as string]))

(defn top-point []
  (r/create-class
    {:component-did-mount
     (fn [this]
       (.scrollTo js/window 0 0)
       #_(when true (.scrollIntoView (rdom/dom-node this) true)))
     :reagent-render
     (fn [_] [:div ""])}))

(defn dashboard-page []
  [:<>
   [top-point]
   [dashboard/dashboard-page]])

(defn properties-page []
  [:<>
   [top-point]
   [place-filter/home-filter]
   (let [fetched-place-raw @(rf/subscribe [:place/fetch-data-filtered])
         fetched-place (sort-by :id fetched-place-raw)]
     (if-not (empty? fetched-place)
       [:div.container>div.content.mx-3.is-full {:style {:margin-top "0px"}}
        (for [data fetched-place]
          ^{:key (:id data)}
          [place/place-card data])]
       [:div.hero.is-small.pl-0.pr-0
        [:div.hero-body
         [:div.container.has-text-centered
          [:h3.subtitle.mt-6
           "Oops... 暂无符合条件的选项"]]]]))
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 " "Marvin Studio." " All Right Reserved.")]]])

(defn clothes-page []
  [:<>
   [top-point]
   [place-filter/home-filter]
   (let [fetched-place-raw @(rf/subscribe [:place/fetch-data-filtered])
         fetched-place (sort-by :id fetched-place-raw)]
     (if-not (empty? fetched-place)
       [:div.container>div.content.mx-3.is-full {:style {:margin-top "0px"}}
        (for [data fetched-place]
          ^{:key (:id data)}
          [place/place-card data])]
       [:div.hero.is-small.pl-0.pr-0
        [:div.hero-body
         [:div.container.has-text-centered
          [:h3.subtitle.mt-6
           "Oops... 暂无符合条件的选项"]]]]))
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 " "Marvin Studio." " All Right Reserved.")]]])

(defn foods-page []
  [:<>
   [top-point]
   [place-filter/home-filter]
   (let [fetched-place-raw @(rf/subscribe [:place/fetch-data-filtered])
         fetched-place (sort-by :id fetched-place-raw)]
     (if-not (empty? fetched-place)
       [:div.container>div.content.mx-3.is-full {:style {:margin-top "0px"}}
        (for [data fetched-place]
          ^{:key (:id data)}
          [place/place-card data])]
       [:div.hero.is-small.pl-0.pr-0
        [:div.hero-body
         [:div.container.has-text-centered
          [:h3.subtitle.mt-6
           "Oops... 暂无符合条件的选项"]]]]))
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 " "Marvin Studio." " All Right Reserved.")]]])

(defn goods-page []
  [:<>
   [top-point]
   [:section.hero.is-large
    [:div.hero-body.has-text-centered
     [:p.title.is-family-code [:i.fa.fa-exclamation-triangle] " Coming Soon..."]
     [:p.subtitle.is-family-code "正在施工"]]]
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 " "Marvin Studio." " All Right Reserved.")]]])

(defn diary-page []
  [:<>
   [top-point]
   [diary/diary-page]
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 " "Marvin Studio." " All Right Reserved.")]]])

(defn diary-edit-page []
  [:<>
   [top-point]
   [diary/diary-edit-page]
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 " "Marvin Studio." " All Right Reserved.")]]])

(defn diary-new-page []
  [:<>
   [top-point]
   [diary/diary-new-page]
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 " "Marvin Studio." " All Right Reserved.")]]])

(defn diary-view-page []
  [:<>
   [top-point]
   [diary/diary-view-page]
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 " "Marvin Studio." " All Right Reserved.")]]])

(defn plan-page []
  [:<>
   [top-point]
   [:div.container>div.content
    [plan/plan-page]]
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 " "Marvin Studio." " All Right Reserved.")]]])

(defn hcm-page []
  [:<>
   [top-point]
   [:div.container>div.content
    [work/main-page]]
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 " "Marvin Studio." " All Right Reserved.")]]])

(defn hcm-all-page []
  [:<>
   [top-point]
   [:div.container>div.content
    [work-all/main-page]]
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 " "Marvin Studio." " All Right Reserved.")]]])

(defn cook-page []
  [:<>
   [top-point]
   [:div
    [:div.container>div.content.mt-6
     [:p "正在施工..."]]
    [:footer.mt-6.mb-4
     [:p.footer-content.has-text-centered.has-text-grey
      (str "© 2016-2022 " "Marvin Studio." " All Right Reserved.")]]]])

(defn file-page []
  [:<>
   [top-point]
   [file/file-main]
   [:footer.mt-6.mb-4 {:style {:position :absolute
                               :bottom   0 :left 0 :right 0 :z-index -1}}
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 " "Marvin Studio." " All Right Reserved.")]]])

(defn package-page []
  [:<>
   [top-point]
   [:div.hero.is-large
    [:div.hero-body.has-text-centered
     [:p.title.is-family-code [:i.fa.fa-exclamation-triangle] " Coming Soon..."]
     [:p.subtitle.is-family-code "正在施工"]]]
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 "
          "Marvin Studio."
          " All Right Reserved.")]]])

(defn command-card [with-header name device]
  (r/with-let [show (r/atom false)]
              (let [device (or device "spine-1")]
                [:div.mx-2.px-4.py-1
                 (when with-header
                   [:p.is-size-5.mb-0 [:span.mr-2 name]
                    [:span.is-family-code.is-size-7.is-text-grey "BgpManager::configBgp"]])
                 [:a.mb-3.mt-1.is-clickable.is-unselectable {:on-click #(swap! show not)}
                  [:span.mr-1 "设备：" device] [:span.mx-2 "地址：admin@192.168.1.2"]
                  #_[:a.button.is-info.is-rounded.is-small.is-outlined.ml-3
                     (if @show "⚡ 折叠命令" "⭐ 展开命令")]]
                 (if @show
                   [:pre.mt-4 (str
                                "feature lacp\n",
                                "feature vpc\n",
                                "vrf context VPC-KPL\n",
                                "  vpc domain %s\n",
                                "  peer-keepalive destination %s source %s vrf %s\n",
                                "  peer-switch\n",
                                "  peer-gateway\n",
                                "  auto-recovery\n",
                                "  ip arp synchronize\n",
                                "interface port-channel 4095\n",
                                "  no switchport\n",
                                "  vrf member VPC-KPL\n",
                                "  ip address %s/24\n",
                                "interface port-channel 4096\n",
                                "  switchport\n",
                                "  switchport mode trunk\n",
                                "  vpc peer-link\n",
                                "interface %s\n",
                                "  channel-group 4096 force mode active\n",
                                "  no shutdown")])])))

(defn grouped-card
  [g-name device-list]
  (r/with-let
    [show (r/atom false)]
    [:<>
     [:div.mx-2.my-2.px-4.pt-4.pb-2
      [:p.is-size-5.mb-0 [:span.mr-2 g-name]
       [:span.is-family-code.is-size-7.is-text-grey "BgpManager::configBgp"]
       [:a.button.is-info.is-rounded.is-small.is-outlined.ml-3
        {:on-click #(swap! show not)} (if @show "⚡ 折叠分组" "⭐ 展开分组")]]
      [:p.mb-2.pb-0.mt-0 [:span.mr-1 "命令：" (rand-int 100) " 个"]]
      (if @show
        [:div {:style {:margin-left   "30px"
                       :margin-bottom "30px"}}
         (for [{:keys [is-sub-task? name device-list device] :as data} device-list]
           ^{:key data}
           (if is-sub-task?
             [grouped-card name device-list]
             [command-card false g-name device]))])]
     [:hr]]))

(defn demo-page []
  [:<>
   [top-point]
   [:div.container.mt-5
    [grouped-card "BGP 特性配置"
     [{:device "leaf-1"}
      {:device "leaf-2"}
      {:device "leaf-3"}
      {:device "leaf-4"}]]
    [grouped-card "BGP 邻居配置"
     [{:device "leaf-1"}
      {:device "leaf-2"}
      {:device "leaf-3"}
      {:device "leaf-4"}]]
    [grouped-card "BGP 组播配置"
     [{:is-sub-task? true
       :name         "Pim RP 配置"
       :device-list  [{:device "leaf-1"}
                      {:device "leaf-2"}
                      {:device "leaf-3"}
                      {:device "leaf-4"}]}
      {:is-sub-task? true
       :name         "Anycast RP 配置"
       :device-list  [{:device "leaf-1"}
                      {:device "leaf-2"}
                      {:device "leaf-3"}
                      {:device "leaf-4"}]}]]]
   [:footer.mt-6.mb-4
    [:p.footer-content.has-text-centered.has-text-grey
     (str "© 2016-2022 "
          "Marvin Studio."
          " All Right Reserved.")]]])

(defn psy-exp-page []
  [:<>
   [top-point]
   [:div.hero.is-info.is-fullheight
    [:div.hero-body {:style {:flex-direction :column
                                            :align-content :center
                                            :justify-content :center}}
     [:span.title.mr-5.ml-5.mb-6.is-family-code "CyberMe Psychology System"]
     [:span.subtitle.mb-3.has-text-weight-bold "高效 · 专业 · 易用"]
     [:span.subtitle.mb-3.is-size-6.is-capitalized "实验设计联系：corkine@outlook.com"]]]])

(defn psy-exp-detail-page []
  [:<>
   [top-point]
   [exp/root]])