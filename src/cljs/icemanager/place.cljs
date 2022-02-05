(ns icemanager.place
  (:require
    [day8.re-frame.http-fx]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [markdown.core :refer [md->html]]
    [icemanager.events]
    [icemanager.about :refer [log about-page]]
    [cljs-time.core :as time]
    [cljs-time.format :as format]
    [clojure.string :as string]))

(defn gen-key []
  (gensym "key-"))

(defn jvm->js-time-str [jvm-time-str]
  (try
    (format/unparse
      (format/formatter "yyyy-MM-dd HH:mm")
      (format/parse (format/formatter "yyyy-MM-dd'T'HH:mm:ss.SSS") jvm-time-str))
    (catch js/Error e
      (.warn js/console (str "解析 " jvm-time-str "失败：" e))
      "0000-00-00 00:00")))

(defn items->map [items]
  "将每个位置的多个项目按照类别进行划分，包括一个 :all 所有项目"
  (let [map-data
        (reduce (fn [agg {:keys [labels] :as new-item}]
                  (reduce (fn [agg2 label]
                            (assoc agg2 (keyword label)
                                        (conj (get agg2 (keyword label) #{})
                                              new-item)))
                          agg
                          labels)) {} items)
        with-all-map (assoc map-data :all items)]
    with-all-map))

(declare place-card-right-nav)
(declare place-card-right-contents)
(declare place-card-right-contents-line)

(defn place-card [{:keys [id place location description updateAt items]
                   :or   {place       "未命名位置" location "未命名地点"
                          description "暂无备注" updateAt "2022-01-01T00:00:00.000000"
                          items       []}}]
  "首页位置和物品卡片。
  数据结构：{id place location description updateAt
           items [{id uid name note createAt updateAt status
                   labels [string]
                   packages [{id name status}]}]}
  渲染要求：将 items 的 labels 提取出来，按照 label 进行 items 的导航，label 按照 a-z
  排序，对于同一 label items 按照 status 进行排序。"
  (let [data (items->map items)
        labels (keys data)]
    (r/with-let
      [select (r/atom :all #_(first labels))]
      [:div.card.my-5
       [:div.card-content.columns
        [:div.column.is-3
         [:p.title
          ;:on-click #(rf/dispatch [:common/navigate! :feature-view
          ;                         {:rs-id (string/lower-case "11")}])
          [:span {:style {:cursor       :pointer
                          :margin-right :10px}} place]
          [:span.tag.is-light.is-rounded
           [:span.icon {:style {:margin-right :-4px
                                :margin-left  :-9px}}
            [:i.fa.fa-map-marker {:aria-hidden "true"}]]
           [:span location]]]
         [:p [:span.icon {:style {:margin-right :0px
                                  :margin-left  :-3px}}
              [:i.fa.fa-info-circle {:aria-hidden "true"}]]
          [:span.has-text-dark-lighter (if (string/blank? description) "暂无描述" description)]]
         [:p [:span.icon {:style {:margin-right :0px
                                  :margin-left  :-3px}}
              [:i.fa.fa-clock-o {:aria-hidden "true"}]]
          [:span.has-text-dark-lighter (jvm->js-time-str updateAt)]]] ;;位置描述左半边
        (let [ss @select
              select-data (sort-by identity (fn [{a :status a-n :name} {b :status b-n :name}]
                                              (if (= a b) (compare a-n b-n)
                                                          (case (str a ":" b)
                                                            "活跃:收纳" -1
                                                            "活跃:移除" -1
                                                            "收纳:移除" -1
                                                            "收纳:活跃" 1
                                                            "移除:活跃" 1
                                                            "移除:收纳" 1
                                                            1))) (get data ss))
              data-size (count select-data)
              split-col 4
              first-column (if (> data-size split-col) (take (/ data-size 2) select-data) select-data)
              second-column (if (> data-size split-col) (drop (/ data-size 2) select-data) [])]
          [:div.column
           [:nav                                              ;;抽屉导航
            [:p.panel-tabs.is-justify-content-start.pl-1      ;;抽屉详情右半边
             (for [label labels]
               ^{:key label}
               [place-card-right-nav label ss select])]
            [:div.columns.mt-2                                ;;抽屉内容
             [place-card-right-contents first-column second-column]]]])]])))

(defn place-card-right-nav [label ss select]
  "位置卡片右侧详情按照标签进行导航"
  (if (= label :all)
    [(if (= ss :all) :a.is-active.is-unselectable :a.is-unselectable)
     {:on-click #(reset! select label) :style {:padding "5px 0 5px 0"}}
     [:span.icon-text [:span.icon [:i.fa.fa-tags {:aria-hidden "true"}]]
      [:span "所有标签"]]]
    [(if (= ss label) :a.is-active.is-unselectable :a.is-unselectable)
     {:on-click #(reset! select label)}
     (str "# " (name label))]))

(defn place-card-right-contents [first second]
  "位置卡片物品详情列，包括物品状态、备注和打包信息，以及鼠标移入显示的操作按钮"
  [:<>
   [:div.column.my-0.py-0
    (for [col first]
      ^{:key (:id col)}
      [place-card-right-contents-line col])]
   [:div.column.my-0.py-0
    (for [col second]
      ^{:key (:id col)}
      [place-card-right-contents-line col])]])

(defn place-card-right-contents-line [{:keys [id status name note packages createAt updateAt]
                                      :or    {status "收纳" name "无标题" packages []}}]
  (let [color (case status
                "活跃" :is-success
                "收纳" :is-normal
                "移除" :is-danger
                :is-normal)
        have-package (> (count packages) 0)]
    [:a.panel-block.is-active
     [:span {:class [:tag :mr-3 :is-light color]} status] ;状态
     [:span.dropdown.is-hoverable.is-up
      [:div.dropdown-trigger
       [:div {:aria-haspopup "true" :aria-controls "dropdown-menu"}
        [:span.mr-2 name]]]                              ;名称和鼠标移动时的备注、创建和修改时间
      [:div#dropdown-menu.dropdown-menu {:role "menu"}
       (if (or note createAt updateAt)
         [:div.dropdown-content
          (when note [:div.dropdown-item.is-size-7.pt-1.pb-1
                      [:p [:i.fa.fa-info-circle] " " note]])
          (when createAt [:div.dropdown-item.is-size-7.pt-1.pb-1
                          [:p [:i.fa.fa-asterisk] " " (or (jvm->js-time-str createAt) "")]])
          (when updateAt [:div.dropdown-item.is-size-7.pt-1.pb-1
                          [:p [:i.fa.fa-clock-o] " " (or (jvm->js-time-str updateAt) "")]])])]]
     (for [{:keys [id name status] :as package} packages]
       ^{:key (random-uuid)}
       [(if (= status 1)                                 ;打包
          :span.tag.is-warning.is-small.is-rounded
          :span.tag.is-warning.is-small.is-rounded.is-light) name [:button.delete.is-small]])
     #_[:span.dui-tips (when note {:data-tooltip (or note "无备注")})]
     [:span.has-text-grey-light.hover-show {:style {:margin-left :auto}} ;操作按钮
      (if-not have-package
        [:span.dropdown.is-hoverable.is-up               ;移动到新位置按钮
         [:div.dropdown-trigger
          [:div {:aria-haspopup "true" :aria-controls "dropdown-menu"}
           [:span.icon [:i.fa.faa.fa-share]]]]
         [:div#dropdown-menu.dropdown-menu {:role "menu"}
          (if true
            [:div.dropdown-content
             [:button.button.is-white.dropdown-item [:p "抽屉 #1"]]
             [:button.button.is-white.dropdown-item [:p "抽屉 #2"]]
             [:button.button.is-white.dropdown-item [:p "抽屉 #3"]]])]])
      (if-not have-package
        [:span.dropdown.is-hoverable.is-up               ;打包按钮
         [:div.dropdown-trigger
          [:div {:aria-haspopup "true" :aria-controls "dropdown-menu"}
           [:span.icon [:i.fa.faa.fa-cube]]]]
         [:div#dropdown-menu.dropdown-menu {:role "menu"}
          (if true
            [:div.dropdown-content
             [:button.button.is-white.dropdown-item [:p "过年回家 #2022.02.04"]]
             [:button.button.is-white.dropdown-item [:p "海南旅游 #2022.03.04"]]])]])
      [:span.icon [:i.fa.faa.fa-pencil]]                     ;修改按钮
      (if-not have-package
        [:span.icon
         {:on-click
          #(rf/dispatch [:global/notice
                         {:message "此操作会将此物品标记为删除，但保留数据库条目，是否继续？"
                          :callback [:good/hide id]}])}
         [:i.fa.faa.fa-ban]])                     ;删除按钮
      (if-not have-package
        [:span.icon.has-text-danger
         {:on-click
          #(rf/dispatch [:global/notice
                         {:message "此操作会将此物品从数据库彻底移除，是否继续？"
                          :callback [:good/delete id]}])}
         [:i.fa.faa.fa-trash]])]]))