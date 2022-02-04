(ns icemanager.place
  (:require
    [day8.re-frame.http-fx]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [markdown.core :refer [md->html]]
    [icemanager.events]
    [icemanager.about :refer [log about-page]]
    [clojure.string :as string]))

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

(defn place-card-detail-column [column-data]
  "位置卡片物品详情列，包括物品状态、备注和打包信息，以及鼠标移入显示的操作按钮"
  [:div.column.my-0.py-0
   (for [{:keys [status name note packages createAt lastUpdate]
          :or {status "收纳"
               name   "无标题"
               packages []} :as line} column-data]
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
          (if (or note createAt lastUpdate)
            [:div.dropdown-content
             (when note [:div.dropdown-item
                         [:p [:i.fa.fa-info-circle] " " note]])
             (when createAt [:div.dropdown-item
                             [:p [:strong "创建时间："] (or createAt "")]])
             (when lastUpdate [:div.dropdown-item
                               [:p [:strong "更新时间："] (or lastUpdate "")]])])]]
        (for [{:keys [id name status] :as package} packages]
          ^{:key package}
          [(if (= status 1)                                 ;打包
             :span.tag.is-warning.is-small.is-rounded
             :span.tag.is-warning.is-small.is-rounded.is-light) name [:button.delete.is-small]])
        #_[:span.dui-tips (when note {:data-tooltip (or note "无备注")})]
        [:span.has-text-grey-light.hover-show {:style {:margin-left :auto}} ;操作按钮
         (if-not have-package
           [:span.dropdown.is-hoverable.is-up                 ;移动到新位置按钮
            [:div.dropdown-trigger
             [:div {:aria-haspopup "true" :aria-controls "dropdown-menu"}
              [:span.icon [:i.fa.fa-share]]]]
            [:div#dropdown-menu.dropdown-menu {:role "menu"}
             (if true
               [:div.dropdown-content
                [:a.dropdown-item [:p "抽屉 #1"]]
                [:a.dropdown-item [:p "抽屉 #2"]]
                [:a.dropdown-item [:p "抽屉 #3"]]])]])
         (if-not have-package
           [:span.dropdown.is-hoverable.is-up                 ;打包按钮
            [:div.dropdown-trigger
             [:div {:aria-haspopup "true" :aria-controls "dropdown-menu"}
              [:span.icon [:i.fa.fa-cube]]]]
            [:div#dropdown-menu.dropdown-menu {:role "menu"}
             (if true
               [:div.dropdown-content
                [:a.dropdown-item [:p "过年回家 #2022.02.04"]]
                [:a.dropdown-item [:p "海南旅游 #2022.03.04"]]])]])
         [:span.icon [:i.fa.fa-pencil]]                     ;修改按钮
         (if-not have-package
           [:span.icon [:i.fa.fa-ban]])                        ;删除按钮
         (if-not have-package
           [:span.icon.has-text-danger [:i.fa.fa-trash]])]]))])

(defn place-card [{:keys    [place location description lastUpdate items]
                        :or {place       "未命名位置" location "未命名地点"
                               description "暂无备注" lastUpdate (js/Date)
                               items       []}}]
  "首页位置和物品卡片。
  数据结构：{id place location description lastUpdate
           items [{id uid name note createAt lastUpdate status
                   labels [string]
                   packages [{id name status}]}]}
  渲染要求：将 items 的 labels 提取出来，按照 label 进行 items 的导航，label 按照 a-z
  排序，对于同一 label items 按照 status 进行排序。"
  (r/with-let
    [data (items->map items)
     labels (keys data)
     select (r/atom :all)]
    [:div.card.my-5
     [:div.card-content.columns
      [:div.column.is-3
       [:p.title
        [:span {:on-click #(rf/dispatch [:common/navigate! :feature-view
                                         {:rs-id (string/lower-case "11")}])
                :style    {:cursor       :pointer
                           :margin-right :10px}}
         place]
        [:span.tag.is-light.is-rounded
         [:span.icon {:style {:margin-right :-4px
                              :margin-left  :-9px}}
          [:i.fa.fa-map-marker {:aria-hidden "true"}]]
         [:span location]]]
       [:p [:span.icon {:style {:margin-right :0px
                                :margin-left  :-3px}}
            [:i.fa.fa-info-circle {:aria-hidden "true"}]]
        [:span.has-text-dark-lighter description]]
       [:p [:span.icon {:style {:margin-right :0px
                                :margin-left  :-3px}}
            [:i.fa.fa-clock-o {:aria-hidden "true"}]]
        [:span.has-text-dark-lighter lastUpdate]]]          ;;位置描述左半边
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
             (if (= label :all)
               [(if (= ss :all) :a.is-active :a)
                {:on-click #(reset! select label) :style {:padding "5px 0 5px 0"}}
                [:span.icon-text [:span.icon [:i.fa.fa-tags {:aria-hidden "true"}]]
                 [:span "所有标签"]]]
               [(if (= ss label) :a.is-active :a)
                {:on-click #(reset! select label)}
                (str "# " (name label))]))]
          [:div.columns.mt-2                                 ;;抽屉内容
           [place-card-detail-column first-column]
           [place-card-detail-column second-column]]]])]]))