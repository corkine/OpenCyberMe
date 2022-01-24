(ns icemanager.feature-edit
  (:require [icemanager.feature :as f]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [icemanager.modals :as modals]
            [goog.string :as gstring]
            [clojure.string :as string]
            [icemanager.feature :as feature]))

(defn reformat-review-user-to-array [row]
  (let [review-list (-> row :info :review)
        new-review-list (map (fn [rev]
                               (let [participants-str (:participants rev)]
                                 (if (vector? participants-str)
                                   rev
                                   (let [replace-cn (string/replace participants-str "，" ",")]
                                     (assoc rev
                                       :participants
                                       (vec (map string/trim
                                                 (string/split replace-cn ","))))))))
                             review-list)]
    (assoc-in row [:info :review] (vec new-review-list))))

(defn reformat-api-req-response [row]
  "fetch :api-keys to handle :api.xxxx array
  :api.xxxx have element: name, path, method, note
  then fetch :api.xxx.request-keys array
  ::api.xxx.request.yyy have element: name, example, type
  then fetch :api.xxx.response-keys array
  ::api.xxx.response.zzz have element: name, example, type
  then delete :api-keys, :api-index
  [:api.{}.request-keys], [:api.{}.response-keys]
  [:api.{}.request-index], [:api.{}.response-index]
  and finally make
  {:info {:api [{:path, :method, :note,
                 :request [{:name :example :type}],
                 :response [{:name :example :type}]}]} "
  #_(println row)
  (let [key2vec #(vec (map (fn [id] (merge (get row id) (%2 id)))
                           %1))
        set-default-param-type (fn [param] (if (nil? (:type param))
                                             (assoc param :type (first feature/http-param-type))
                                             param))
        set-default-method (fn [api] (if (nil? (:method api))
                                       (assoc api :method (first feature/http-method))
                                       api))
        merge-req-resp
        (fn [api-key]
          (let [req-key-array (get row (keyword
                                         (string/replace
                                           (str api-key ".request-keys") ":" "")))
                resp-key-array (get row (keyword
                                          (string/replace
                                            (str api-key ".response-keys") ":" "")))]
            {:request  (vec (map set-default-param-type (key2vec req-key-array (fn [_] {}))))
             :response (vec (map set-default-param-type (key2vec resp-key-array (fn [_] {}))))}))
        api-vec (vec (map set-default-method (key2vec (:api-keys row) merge-req-resp)))]
    (let [top-keys [:api-keys :api-index]
          api-keys (:api-keys row)
          sub-keys (vec (flatten
                          (map (fn [api-key]
                                 (let [req-key (keyword
                                                 (string/replace
                                                   (str api-key ".request-keys") ":" ""))
                                       req-index (keyword
                                                   (string/replace
                                                     (str api-key ".request-index") ":" ""))
                                       resp-key (keyword
                                                  (string/replace
                                                    (str api-key ".response-keys") ":" ""))
                                       resp-index (keyword
                                                    (string/replace
                                                      (str api-key ".response-index") ":" ""))]
                                   (into (into (get row req-key) (get row resp-key))
                                         [req-key resp-key req-index resp-index]))) api-keys)))
          need-delete-keys (flatten [top-keys api-keys sub-keys])
          clean-data (apply dissoc row need-delete-keys)]
      (assoc-in clean-data [:info :api] api-vec))))

(defn developer-implement-review-to-data [row]
  (try
    (let [impl-keys (:implement-keys row)
          developer-keys (:developer-keys row)
          review-keys (:review-keys row)
          fnn (fn [agg new-key] (conj agg (get row new-key)))]
      (let [merged-data {:implement (reduce fnn [] impl-keys)
                         :review    (reduce fnn [] review-keys)
                         :developer (reduce fnn [] developer-keys)}
            new-data (assoc row :info (merge (get row :info) merged-data))
            temp-keys (conj (into (into impl-keys review-keys) developer-keys)
                            :review-keys :implement-keys :developer-keys
                            :review-index :implement-index :developer-index)
            final-data (apply dissoc new-data temp-keys)
            reformat-data (reformat-review-user-to-array final-data)
            ;_ (println "origin data: " reformat-data)
            reformat-data (reformat-api-req-response reformat-data)
            _ (println "final data: " reformat-data)]
        {:error nil :data reformat-data}))
    (catch js/Error. e
      {:error e :data nil})))

(defn data-to-dev-row [data]
  "将格式化数据转换为前端显示的文本 - JSON 格式化字符串"
  (f/clj->js-str (if (map? data) (vector data) data)))

(defn notice-modal-card []
  (modals/modal-card :update-feature-notice "提醒"
                     (let [error @(rf/subscribe [:update-feature-error])]
                       (if-not error
                         [:p "特性更新成功！"]
                         [:div
                          [:p "特性更新失败！"]
                          [:pre (with-out-str (cljs.pprint/pprint error))]]))
                     [:button.button.is-success.is-fullwidth
                      {:on-click #(rf/dispatch [:app/hide-modal :update-feature-notice])}
                      "关闭"]))

(defn delete-modal-card []
  (modals/modal-card :delete-feature-notice "提醒"
                     (let [data @(rf/subscribe [:del-feature-server-back])
                           delete-success (= (:status data) :success)]
                       (if delete-success
                         [:p "特性删除成功！"]
                         [:div
                          [:p "特性删除失败！"]
                          [:div.notification.is-danger.mt-4
                           (with-out-str (cljs.pprint/pprint (:content data)))]]))
                     [:button.button.is-success.is-fullwidth
                      {:on-click
                       (fn [_]
                         (rf/dispatch [:clean-del-feature-server-back])
                         (rf/dispatch [:app/hide-modal :delete-feature-notice])
                         (rf/dispatch [:common/navigate! :home]))}
                      "关闭"]))

(defn param-form [fields common-field {:keys [title form-keys-kw form-index-kw]}]
  (let [f @fields
        param-keys (get f form-keys-kw)
        param-index (get f form-index-kw)]
    [:<>
     [:label.label {:style {:margin-top :30px}} title]
     [:div.tabs {:style {:margin-left :-30px :margin-top :-20px}}
      [:ul
       (for [[index param-key] (map-indexed vector param-keys)]
         ^{:key param-key}
         [(if (= index param-index) :li.is-active :li)
          (let [data (get f param-key)]
            [:a
             {:on-click (fn [_] (swap! fields assoc form-index-kw index))}
             [:<>
              [:i.material-icons {:style {:font-size   :1.3em
                                          :margin-left :-2px :margin-right :5px}}
               "fiber_smart_record"]
              (or (:name data) "键")]
             [:i.material-icons {:style    {:font-size :1em :margin-left :0.5em}
                                 :on-click (fn [_]
                                             (swap! fields dissoc param-key)
                                             (swap! fields assoc form-keys-kw
                                                    (vec (remove #(= % param-key) param-keys))))}
              "cancel"]])])
       [:a {:on-click (fn [_]
                        (let [new-key (keyword (str (name form-keys-kw)
                                                    "." (first (string/split (random-uuid) "-"))))]
                          (swap! fields assoc new-key {})
                          (swap! fields assoc form-keys-kw (vec (conj param-keys new-key)))
                          (swap! fields assoc form-index-kw (count param-keys))))}
        [:i.material-icons "add"]]]]
     [:div.ml-4
      (when (empty? param-keys)
        [:div.notification.is-danger.pt-2.pb-2.pl-3
         "请点击 + 新建请求字段后再填写下面表单"])
      #_(println "->" param-keys param-index)
      [common-field [(get param-keys param-index) :name]
       "字段名称" "字段名称"
       {:small-label true
        :readonly    (if (empty? param-keys) "" nil)}]
      [common-field [(get param-keys param-index) :description]
       "字段含义" "字段含义"
       {:small-label true
        :readonly    (if (empty? param-keys) "" nil)}]
      [common-field [(get param-keys param-index) :type]
       "字段类型" "在此输入字段类型"
       {:type        :select :selects feature/http-param-type
        :small-label true
        :readonly    (if (empty? param-keys) "" nil)}]
      [common-field [(get param-keys param-index) :example]
       "示例值" "字段示例值"
       {:small-label true
        :readonly    (if (empty? param-keys) "" nil)}]]]))

(defn remove-q [in] (keyword (string/replace in ":" "")))

(defn api-form [fields common-field]
  (let [{:keys [api-keys api-index] :as f} @fields]
    [:<>
     [:p.title {:style {:margin-top :30px}} "API 接口"]
     [:div.tabs.is-boxed {:style {:margin-left :-30px
                                  :margin-top  :-30px}}
      [:ul
       (for [[index api-key] (map-indexed vector api-keys)]
         ^{:key api-key}
         [(if (= index api-index) :li.is-active :li)
          (let [data (get f api-key)]
            [:a
             {:on-click (fn [_] (swap! fields assoc :api-index index))}
             [:<>
              [:i.material-icons {:style {:font-size    :1.3em
                                          :margin-top   :4px
                                          :margin-left  :-2px
                                          :margin-right :5px}}
               "compare_arrows"]
              (or (:name data) "新建接口")]
             [:i.material-icons {:style    {:font-size   :1em
                                            :margin-left :0.5em}
                                 :on-click (fn [_]
                                             (swap! fields dissoc api-key)
                                             (swap! fields assoc :api-keys
                                                    (vec (remove #(= % api-key) api-keys))))}
              "cancel"]])])
       [:a {:on-click (fn [_]
                        (let [new-key (keyword (str "api." (first (string/split (random-uuid) "-"))))]
                          (swap! fields assoc new-key {})
                          (swap! fields assoc :api-keys (vec (conj api-keys new-key)))
                          (swap! fields assoc :api-index (count api-keys))))}
        [:i.material-icons "add"]]]]
     (when (empty? api-keys)
       [:div.notification.is-danger.pt-2.pb-2.pl-3
        "请点击 + 新建 API 接口后再填写下面表单"])
     [common-field [(get api-keys api-index) :name]
      "名称" "比如交换机查询接口"
      {:readonly (if (empty? api-keys) "" nil)}]
     [common-field [(get api-keys api-index) :path]
      "路径" "比如 /api/v1/underlay/switch"
      {:readonly (if (empty? api-keys) "" nil)}]
     [common-field [(get api-keys api-index) :method]
      "HTTP 方法" "比如 GET POST DELETE"
      {:type     :select :selects feature/http-method
       :readonly (if (empty? api-keys) "" nil)}]
     [param-form fields common-field {:title         "请求格式"
                                      :form-keys-kw  (remove-q (str (get api-keys api-index) ".request-keys"))
                                      :form-index-kw (remove-q (str (get api-keys api-index) ".request-index"))}]
     [param-form fields common-field {:title         "响应格式"
                                      :form-keys-kw  (remove-q (str (get api-keys api-index) ".response-keys"))
                                      :form-index-kw (remove-q (str (get api-keys api-index) ".response-index"))}]
     [:div.mt-5
      [common-field [(get api-keys api-index) :note]
       "备注" "在此输入接口的使用范围、约束和限制。"
       {:type     :textarea
        :rows     2
        :readonly (if (empty? api-keys) "" nil)}]]]))

(defn developer-form [fields common-field]
  (let [{:keys [rs-id developer-keys developer-index] :as f} @fields]
    [:<>
     [:p.title {:style {:margin-top :30px}} "开发者信息"]
     [:div.tabs.is-boxed {:style {:margin-left :-30px
                                  :margin-top  :-30px}}
      [:ul
       (for [[index developer-key] (map-indexed vector developer-keys)]
         ^{:key developer-key}
         [(if (= index developer-index) :li.is-active :li)
          (let [data (get f developer-key)]
            [:a
             {:on-click (fn [_] (swap! fields assoc :developer-index index))}
             [:<>
              [:i.material-icons {:style {:font-size    :1.3em
                                          :margin-top   :-2px
                                          :margin-left  :-2px
                                          :margin-right :1px}}
               "person_outline"]
              (or (:name data) "新添个人")]
             [:i.material-icons {:style    {:font-size   :1em
                                            :margin-left :0.5em}
                                 :on-click (fn [_]
                                             (swap! fields dissoc developer-key)
                                             (swap! fields assoc :developer-keys
                                                    (vec (remove #(= % developer-key) developer-keys))))}
              "cancel"]])])
       [:a {:on-click (fn [_]
                        (let [new-key (keyword (str "developer." (first (string/split (random-uuid) "-"))))]
                          (swap! fields assoc new-key {:title "" :content ""})
                          (swap! fields assoc :developer-keys (conj developer-keys new-key))
                          (swap! fields assoc :developer-index (count developer-keys))))}
        [:i.material-icons "add"]]]]
     [common-field [(get developer-keys developer-index) :name]
      "姓名" "在此输入开发者姓名"
      {}]
     [common-field [(get developer-keys developer-index) :email]
      "邮箱" "在此输入开发者联系方式"
      {}]
     [common-field [(get developer-keys developer-index) :role]
      "角色" "在此输入开发者角色"
      {:type :select :selects
       ["后端开发" "测试人员" "前端开发" "UI 设计" "系统架构" "项目经理" "其它"]}]
     [common-field [(get developer-keys developer-index) :duty]
      "任务" "在此输入开发者负责的任务"
      {:type :textarea
       :rows 2}]]))

(defn impl-form [fields common-field]
  (let [{:keys [rs_id implement-keys implement-index]} @fields]
    [:<>
     [:p.title {:style {:margin-top :30px}} "实施方案"]
     [:div.tabs.is-boxed {:style {:margin-left :-30px
                                  :margin-top  :-30px}}
      [:ul
       (for [[index implement-key] (map-indexed vector implement-keys)]
         ^{:key implement-key}
         [(if (= index implement-index) :li.is-active :li)
          [:a
           {:on-click (fn [_] (swap! fields assoc :implement-index index))}
           [:<>
            [:i.material-icons {:style {:font-size    :1.1em
                                        :margin-top   :-1px
                                        :margin-left  :-2px
                                        :margin-right :3px}}
             "description"]
            (str rs_id "." (gstring/format "%03d" (+ index 1)))]
           [:i.material-icons {:style    {:font-size   :1em
                                          :margin-left :0.5em}
                               :on-click (fn [_]
                                           (swap! fields dissoc implement-key)
                                           (swap! fields assoc :implement-keys
                                                  (vec (remove #(= % implement-key) implement-keys))))}
            "cancel"]]])
       [:a {:on-click (fn [_]
                        (let [new-key (keyword (str "implement." (first (string/split (random-uuid) "-"))))]
                          (swap! fields assoc new-key {:title "" :content "" :summary ""})
                          (swap! fields assoc :implement-keys (conj implement-keys new-key))
                          (swap! fields assoc :implement-index (count implement-keys))))}
        [:i.material-icons "add"]]]]
     (when (empty? implement-keys)
       [:div.notification.is-danger.pt-2.pb-2.pl-3
        "请点击 + 新建子方案后再填写下面表单"])
     [common-field [(get implement-keys implement-index) :title]
      "步骤标题" "在此输入方案分解后的子标题"
      (if (empty? implement-keys) {:readonly ""})]
     [common-field [(get implement-keys implement-index) :summary]
      "步骤概述" "在此输入方案分解的概述"
      {:type     :textarea
       :rows     2
       :readonly (if (empty? implement-keys) "" nil)}]
     [common-field [(get implement-keys implement-index) :content]
      "步骤内容" "在此输入方案分解的具体实施计划"
      {:type     :textarea
       :readonly (if (empty? implement-keys) "" nil)}]]))

(defn review-form [fields common-field]
  (let [{:keys [review-keys review-index] :as f} @fields]
    [:<>
     [:p.title {:style {:margin-top :30px}} "评审记录"]
     [:div.tabs.is-boxed {:style {:margin-left :-30px
                                  :margin-top  :-30px}}
      [:ul
       (for [[index review-key] (map-indexed vector review-keys)]
         ^{:key review-key}
         [(if (= index review-index) :li.is-active :li)
          (let [curr-data (get f review-key)]
            [:a
             {:on-click (fn [_] (swap! fields assoc :review-index index))}
             [:<>
              [:i.material-icons {:style {:font-size    :1.1em
                                          :margin-top   :-1px
                                          :margin-left  :-2px
                                          :margin-right :3px}}
               "today"]
              (:date curr-data)]
             [:i.material-icons {:style    {:font-size   :1em
                                            :margin-left :0.5em}
                                 :on-click (fn [_]
                                             (swap! fields dissoc review-key)
                                             (swap! fields assoc :review-keys
                                                    (vec (remove #(= % review-key) review-keys))))}
              "cancel"]])])
       [:a {:on-click (fn [_]
                        (let [new-key (keyword (str "implement." (first (string/split (random-uuid) "-"))))]
                          (swap! fields assoc new-key
                                 {:title "" :content ""
                                  :date  (cljs-time.format/unparse
                                           (cljs-time.format/formatter "yyyy-MM-dd HH:mm")
                                           (cljs-time.core/time-now))})
                          (swap! fields assoc :review-keys (conj review-keys new-key))
                          (swap! fields assoc :review-index (count review-keys))))}
        [:i.material-icons "add"]]]]
     (when (empty? review-keys)
       [:div.notification.is-danger.pt-2.pb-2.pl-3
        "请点击 + 新建评审后再填写下面表单"])
     [common-field [(get review-keys review-index) :title]
      "标题" "在此输入此次评审的标题"
      (if (empty? review-keys) {:readonly ""})]
     [common-field [(get review-keys review-index) :participants]
      "参会者（使用全角或半角逗号隔开，允许空格）" "在此输入此次评审的参会者，使用逗号隔开"
      (if (empty? review-keys) {:readonly ""})]
     [common-field [(get review-keys review-index) :content]
      "内容" "在此输入此次评审的主要内容"
      {:type     :textarea
       :readonly (if (empty? review-keys) "" nil)}]]))

(defn feature-form [db-data]
  (let [rand-uu #(first (string/split (random-uuid) "-"))
        vec->map (fn [prefix vec]
                   (zipmap (map #(keyword (str prefix "." %))
                                (repeatedly (count vec) rand-uu)) vec))
        flatten-api-req-resp
        (fn [api-entry] (let [api-key (key api-entry)
                              api-data (val api-entry)
                              req-vec (:request api-data)
                              resp-vec (:response api-data)
                              req-key (remove-q (str api-key ".request-keys"))
                              req-index (remove-q (str api-key ".request-index"))
                              resp-key (remove-q (str api-key ".response-keys"))
                              resp-index (remove-q (str api-key ".response-index"))
                              req-map (vec->map req-key req-vec)
                              resp-map (vec->map resp-key resp-vec)
                              all-map (merge req-map resp-map)]
                          (merge all-map
                                 {req-key    (vec (keys req-map))
                                  resp-key   (vec (keys resp-map))
                                  req-index  0
                                  resp-index 0})))
        review_map (vec->map "review" (-> db-data :info :review))
        implement_map (vec->map "implement" (-> db-data :info :implement))
        developer_map (vec->map "developer" (let [r (-> db-data :info :developer)]
                                              (if (vector? r) r [r])))
        api_map (vec->map "api" (-> db-data :info :api))
        req-resp-map (reduce (fn [agg entry]
                               (merge agg (flatten-api-req-resp entry))) {} api_map)
        fields_merged (merge db-data {:implement-keys  (vec (keys implement_map))
                                      :review-keys     (vec (keys review_map))
                                      :developer-keys  (vec (keys developer_map))
                                      :api-keys        (vec (keys api_map))
                                      :implement-index 0
                                      :review-index    0
                                      :developer-index 0
                                      :api-index       0}
                             review_map implement_map developer_map api_map req-resp-map)
        ;_ (println fields_merged)
        fields (r/atom fields_merged)
        errors (r/atom {})
        success (r/atom {})]
    (letfn [(common-field [id label hint {:keys [type selects moreNode rows readonly
                                                 small-label]}]
              (let [v (r/cursor fields (if (vector? id) id [id]))
                    e (r/cursor errors (if (vector? id) id [id]))]
                #_(println id)
                [:div.field
                 [(if small-label :label.label.has-text-weight-light
                                  :label.label) {:for id} label]
                 (when moreNode moreNode)
                 [(cond (= type :textarea) :div.control>textarea.textarea
                        (= type :select) :div.control>div.select
                        :else :div.control>input.input)
                  (merge {:type        (if type type :text)
                          :id          id
                          :value       @v
                          :on-change   #(reset! v (.. % -target -value))
                          :rows        (if rows rows 5)
                          :placeholder hint}
                         (if readonly {:readOnly true} {}))
                  (when-not (nil? selects)
                    [:select
                     {:id        id
                      :value     (or @v (first selects))
                      :on-change #(reset! v (.. % -target -value))}
                     (for [k selects]
                       ^{:key k}
                       [:option {:value k} k])])]
                 (when-let [message @e]
                   [:div.notification.is-danger.mt-4 message])]))
            (submit-feature-edit []
              (let [raw-data @fields
                    id (:id raw-data)
                    {:keys [error data]} (developer-implement-review-to-data raw-data)
                    ;_ (println data)
                    ]
                (if error (do
                            (rf/dispatch [:set-update-feature-error error])
                            (rf/dispatch [:app/show-modal :update-feature]))
                          (rf/dispatch [:update-feature id data]))))]
      [:<>
       [:div.title "特性概况"]
       [common-field :rs_id "RS 号" "全大写，不能有空格"]
       [common-field :title "特性名字" "简短易懂"]
       [common-field :description "描述" "对特性的描述，第一个全角句号前必须是一句话精简描述。"
        {:type :textarea :rows 2}]
       [common-field :version "引入版本" "输入 ICE 4.3 或者 ICE 5.0"
        {:type :select :selects f/ice-versions}]
       [common-field [:info :designRes] "设计图 URL" "输入设计图 xxx.svg 所在的位置"]
       [common-field [:info :uiRes] "UI 渲染图 URL" "输入 UI 渲染图所在文件夹"]
       [common-field [:info :apiRes] "外部测试环境 URL"
        "本特性的外部 SwaggerUI/PostMan/ApiMan 测试环境 URL"]
       [common-field [:info :feature-version] "特性版本"
        "当前特性的版本，不是 ICE 的版本，用于标记 TR 和评审文档"]
       [common-field [:info :limit] "设计约束" "当前特性的设计约束"
        {:type :textarea :rows 2}]
       [common-field [:info :status] "当前状态" "正在开发，开发完毕 或者尚未开始"
        {:type :select :selects f/ice-status}]
       [developer-form fields common-field]
       [api-form fields common-field]
       [impl-form fields common-field]
       [review-form fields common-field]
       [:button.button.is-primary.is-large.mt-4
        {:style    {:align :right}
         :on-click (fn [_] (submit-feature-edit))} "提交更新"]
       [notice-modal-card]
       [delete-modal-card]
       [:hr]
       [:a.is-danger.mt-4.dui-tips
        {:style        {:color :red}
         :data-tooltip "点选以删除此特性，删除后关于此特性所有方案、评审等信息都将被移除"
         :on-click     (fn [_] (rf/dispatch [:delete-feature (:id db-data)]))}
        "删除此特性"]])))