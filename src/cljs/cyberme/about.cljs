(ns cyberme.about
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [cyberme.modals :as modals]
            [goog.string :as gstring]
            [clojure.string :as string]))

(def old-log-1 "
[CyberMe Go Version]
0.1.0 初步上线打卡记录功能（2021年6月20日）
0.2.0 完善代码中的 Bug：记录不为 2 时获取数组超出索引（2021年6月20日）
0.3.0 上线工时统计功能（2021年6月21日）
0.3.1 完善工时统计功能，识别单卡的上下班卡，去除中间午休时间（2021年6月21日）
0.4.2 提供 HTTP 请求缓存（2021-6-22）
0.4.3 HTTP 缓存提供缓存过期功能（2021-6-22）
0.4.4 对今天和过去使用不同时长的缓存过期时间（2021-6-22）
0.4.5 当今天有两次打卡后，亦加入永不过期缓存（2021-6-22）
0.4.6 处理了晚饭时间工时计算（Beta）
0.5.0 添加了缓存的持久化实现 - 2021年6月30日
0.5.1 修复了 Linux SHELL 脚本和一个 Go 包函数错误 - 2021年6月30日
0.5.2 使用更加智能的 VERSION 变量写法 2021年6月30日
0.6.0 提供缓存读写锁 2021年6月30日
0.6.1 修复了 query 删除过期缓存 - RLock 升级 Lock 导致的死锁 2021年7月1日
0.6.2 添加了打早卡提示 2021年7月1日
0.6.3 添加了每月工时统计 2021年7月1日
0.6.4 添加了所有时长工时统计 2021年7月1日
0.6.5 提供了平均工时统计 2021年7月2日
0.6.6 修复了平均工时统计的一个错误，现在基于2次打卡的日期预测工时 2021年7月2日
0.6.7 添加了每周工时预测（相比较平均）2021年7月2日
0.6.8 工时预测当周一时无法推测本周预计工时，返回一个等待的小图标 2021年7月2日
0.6.9 修复了周一无法预测工时 length = 0 导致的 NaN 序列化错误，更智能的提示为 ⌛ 2021年7月5日
0.6.10 修复了打两次卡加班不计算晚饭时间的错误 2021年07月05日
0.6.11 试图修复每天 3 次数据（带有重复数据）时导致的时间计算问题 2021年07月13日
0.6.12 添加了每月加班 KPI 计算
0.7.1 增加了大致可用的 TODO 项目检测
0.7.2 提供了自动内部更新的快速 /todo/today 接口
0.7.3 提供了多租户缓存支持，区分了 todo 和 check 密码 2021年07月24日
0.8.0 重构了 inspur/server.go 删除了晚饭时长计算 2021年07月24日
0.8.1 优化了加班时间计算的方法（当日如果下班，则计入加班时长统计） 2021年07月24日
0.8.2 添加了客户端查询 HCM 加班时长的并发简单程序 2021年7月29日
0.8.3 完善了纯 Client 客户端服务 2021年7月29日
0.8.4 紧急修复了一个 /0 导致的错误 2021年07月30日
0.8.5 添加了上个月加班时长统计，添加了是否统计早上加班时长的按钮 2021年8月4日
0.8.6 添加了 KPI 活动目标 2021年8月6日
0.9.9 项目模块化，废弃基于 cache.go 的内存缓存实现 2021年08月07日
1.0.0 使用 Redis 实现缓存，提供了基于用户名和密码的验证，基于用户名缓存 HCM Info 2021年08月08日
1.0.1 修复了一个 Redis 实现的 bug：混用了基于用户和基于 Token 的 API 2021年08月08日
1.0.2 修复了一个 TODO 的空指针 panic 错误 2021年08月08日
1.0.3 修复了一个 TODO 的空指针 panic 错误 2021年08月14日
1.0.4 添加了零食 API 2021年08月17日
1.0.5 提供了 Overtime API 和基本的 AutoBot 实现 2021年08月21日
1.0.6 完善了 Overtime API 和 Bot 代码 2021年8月23日
1.0.7 完善了 Overtime API 和 Bot 代码 2021年08月23日
1.0.8 Bot 代码健壮化，当发生错误时使用指数避免以休眠，休眠后配合智能睡眠工作 2021年8月24日
1.0.9 Bot 代码健壮化，提供了 conf 错误的指数回归定时，修复 check 和 order 的指数回归定时重试清除 2021年8月25日
1.1.0 添加了 Bot 信息统计 2021年8月25日
1.1.1 详细显示 Slack 回调 Robot 消息 2021年8月26日
1.1.2 整合 hint API 2021年8月26日
1.1.3 bot 行为更加人性化，提供了点击后查看确认 2021年8月31日
1.1.4 提供了健康日志跟踪 2021年08月31日
1.1.5 修改周六加班确认方式 2021年9月3日
1.1.6 提供了初步的健康记录统计功能 2021年09月04日
1.1.7 重新组织代码，整合健康记录和 HINT 2021年9月4日
1.1.8 提供了健身记录激励 2021年9月4日
1.1.9 修复 bot 周六加班计时逻辑问题 2021年09月04日
1.2.0 紧急修复 clean 和 fitness 时长统计问题，clean 数据 merge 问题 2021年09月05日
1.2.1 修复了 clean API 重复提交的问题 2021年9月8日
1.2.2 缩短 HCM 打卡后更新数据时长，从 100s 缩短到 15s 2021年09月10日
1.2.3 提供了测试版本的快递信息查询 2021年09月11日
1.2.4 周六下午 5 点不打卡直接请求数据库设置 check 为 true 2021年9月11日
1.2.5 修正了快递查询策略，对出错快递进行处理 2021年09月12日
1.2.6 增加了 Location 追踪 2021-9-15
1.2.7 提供了基于用户名的认证 2021-9-17
1.2.8 重写了 bot conf，提供加密认证 2021年9月17日
1.2.9 添加了 BlueInfo 记录 2021年9月25日
1.3.0 添加了 EnergyInfo 记录 2021年9月27日
1.3.1 添加了修改每日加班的 API 2021年9月28日
1.3.2 修复了电影数据被覆盖的问题 2021年9月29日
1.3.3 提供了 2021 公休日计算 2021年10月01日
1.3.4 细分了 blue 类别 2021年10月9日
1.3.5 将膳食和运动信息整合，提供一个新的指标：BalanceCalories 和 IsBalance 值 2021年10月14日
1.3.6 提供了膳食运动信息的教练 2021年10月14日
1.3.7 添加每日运动静息卡路里计算支持 2021年10月19日
1.3.8 优化代码，删除了膳食目标和统计功能，仅衡量运动目标和减重目标 2021年10月19日
1.3.9 提供了 /note 快速笔记功能，更新了 2022 年国家法定假期 2021年10月26日
1.3.10 提供 /note 前端界面支持，提供 Basic 认证支持 2021年10月28日
1.3.11 提供了 OneNote 版本检查支持 2021年12月14日
1.3.12 关闭了大部分功能：笔记，签到，加班，快递，电影，追踪，迁移到 CyberMe Clojure 实现。

[CyberMe Clojure/ClojureScript Version]
[2022-02-03]
草稿设计，基于 ICE Manager 修改界面样式。
实现了主界面（位置）布局：可快速浏览位置以及位置内不同类别物品，可快速对物品进行操作（删除、彻底删除、打包、移动位置和编辑）。
[2022-02-04]
实现了位置物品详情的组件，可解析所有位置和物品的数据并自动展示 UI；实现了“新位置”、“新打包”和“物品入库”三个按钮和模态框的编写，进行了数据校验。
修复了一个因为父组件 overflow auto 导致的 dropdown 组件被遮盖问题。
实现了数据库数据模型，提供了主页所有位置物品的数据查询并进行了数据聚合。
[2022-02-05]
实现了“新键位置”、“新键外出”、“删除物品”、“隐藏物品” API 和弹窗提醒、ajax 反馈和 UI 更新，打通从前端界面到后端和数据库交互。
[2022-02-07]
修复了全局对话框在移动设备上因为折叠不弹窗的问题。
对 re-frame 事件进行抽象，提供了 ajax-flow 抽象事件流接口。
实现了位置信息的更新和删除。
实现了物品打包的最近包选择：点选准备、单击确认、点击拆包。
[2022-02-08]
实现了主界面快速打包删除（hover 显示），使用 flexbox grow 处理了打包点击和删除的区分。
实现了打包在物品选择打包界面中快速删除。
实现了主界面的位置、标签（暂时仅支持单个选项）和状态过滤、物品搜索。
实现了物品位置的移动。
修复了在移动设备因为 navbar z-index 不够高导致修改位置模态框（位于 navbar）穿模的问题。
实现了物品的修改。
修复了模态框滚动后重新打开滚动条不在顶部的问题（scrollIntoView）。
修复了当某个标签最后一个物品删除后显示空白的问题。
[2022-02-09]
Basic Auth 保护 API。
[2022-03-08]
实现了 CyberMe HCM 和 TODO、快递 API。
实现了 user 和 secret 的 query param 鉴权。
[2022-03-09]
实现了 CyberMe 大部分 API。
完善了 TODO 返回数据的日期时区问题，完善了 user 和 secret 登录限制。
添加了 TODO Token 的内存持久化读写。
实现了 TODO 和 Express 后台轮询，处理了轮询 Sleep 的逻辑错误。
[2022-03-10]
可变数据通过 edn 配置文件加载。
TODO 和 HCM Token 均使用本地文件保存机制进行处理。
支持多用户鉴权。
实现了基于 REPL、Swagger 和测试的开发流程，增强 DevOps 开发效率。
实现了 Mini4K、Note 和 Track 功能的实现。
添加了打卡锚点增删改查 API，修复 Mini4K 通知格式。
[2022-03-13]
添加了考勤日历功能。
[2022-03-14]
添加了考勤周报功能。
修复 CyberMe API 错误：更新 HCM 缓存写入机制，修复 get-hcm-info 错误后的静默异常
修复了 LocalDate 和 LocalDateTime 错误的比较。
考勤日历优化，只打了一次卡只显示一次。
[2022-03-15]
为 HCM Auto 添加了 Check 功能，当 Auto 意外失败时 Check 会通知。
考勤日历添加 Check 数据显示。
[2022-03-16]
修复 CyberMe API 错误：express 查询停止通知，允许追踪快递覆盖数据库。
[2022-03-17]
修复了 Circle CI 在标准时间 CST 0:00 测试 serve-hcm-auto 的一个跨日期错误。
从 ICE Manager 迁移了登录交互界面，不依赖浏览器 Basic Auth 登录。
使用 403 而非 401 处理无权限问题，以在 Chrome 下避免无权限冗余的浏览器弹窗。
")

(def version "1.4.2")

(def log (str "now: " version old-log-1 "
[1.4.0 2022-3-17]
关闭 CyberMe Go 实现，完成 iOS 快捷指令，Pixel Tasker，CyberMe Flutter APP 以及其他各种桌面和工具 API 的迁移。
[1.4.1 2022-3-21]
提供了 CyberMe Dashboard 界面。
[1.4.2 2022-3-28]
提供了 Dashboard 刷新按钮，优化了进度条显示，着重显示今天的日程。
[1.4.3 2022-3-29]
提供了工时、健康和习惯详情看板。添加了带有标签的日记系统，可根据菜单、URL、点击前端过滤日记。

================================================
TODO:
- 实现每个位置选择标签的记忆和恢复（页面加载时从 localStorage 获取，之后通过 re-frame 处理事件和查询，离开页面存储）
- 实现资产、食品耗材和衣物的区分。
- 提供一个选项，允许显示空位置。
- 实现物品图片上传、预览和修改。

"))

(defn about-page []
  [:div.hero.is-danger.is-fullheight-with-navbar
   [:section.section>div.container>div.content
    [:p.title "由 Corkine Ma 开发"
     [:a.ml-3.is-size-6.has-text-weight-light
      {:href "/cyber/api-docs/index.html" :target :_black} "CyberAPI"]
     [:a.ml-2.is-size-6.has-text-weight-light
      {:href "/api/api-docs/index.html" :target :_black} "GoodsAPI"]]
    (let [usage @(rf/subscribe [:usage])
          server-back @(rf/subscribe [:wishlist-server-back])
          wish-list @(rf/subscribe [:wishlist])
          real-wish-list (filter #(= (:kind %) "愿望") wish-list)
          bug-list (filter #(= (:kind %) "BUG") wish-list)]
      [:<>
       [:p {:style {:margin-top :-20px}} (str "本服务已服务 " (:pv usage) " 人，共计 " (:uv usage) " 次")]
       [:pre (str log
                  "\n================================================\n数据库记录的请求：\n"
                  (string/join "\n"
                               (map (fn [line] (str "- " (:advice line)
                                                    " / 来自：" (:client line) "")) real-wish-list))
                  "\n\n================================================\n数据库记录的 BUG：\n"
                  (string/join "\n"
                               (map (fn [line] (str "- " (:advice line)
                                                    " / 来自：" (:client line) "")) bug-list))
                  "\n\n================================================\n最近 10 次 API 更改：\n"
                  (string/join "\n"
                               (map #(gstring/format "%-15s %-4s %-20s %-s"
                                                     (:from %) (string/upper-case (:method %))
                                                     (:api %) (:time %))
                                    (:usage usage))))]
       [:div.mb-3
        (r/with-let
          [user (r/atom nil)
           kind (r/atom "愿望")
           advice (r/atom nil)
           error (r/atom nil)]
          [modals/modal-button :wishlist
           {:button {:class ["is-light" "mt-0"]}}
           (str "提愿望/建议/BUG")
           [:div {:style {:color "black"}}
            [:<>
             [:label.label {:for "user"} "称呼 *"]
             [:input.input {:type        :text
                            :id          :user
                            :value       (or @user "")
                            :placeholder "输入你的称呼"
                            :on-change   #(reset! user (.. % -target -value))}]
             [:label.label.mt-4 {:for "kind"} "类别 *"]
             [:div.select>select {:id        :kind
                                  :value     @kind
                                  :on-change #(reset! kind (.. % -target -value))}
              [:option {:value "愿望"} "愿望"]
              [:option {:value "建议"} "建议"]
              [:option {:value "BUG"} "BUG"]]
             [:label.label.mt-4 {:for "advice"} (str @kind " * (不少于 10 个字)")]
             [:textarea.textarea {:rows        4
                                  :value       (or @advice "")
                                  :id          :advice
                                  :on-change   #(reset! advice (.. % -target -value))
                                  :placeholder (str "输入你的" @kind)}]
             (when server-back
               [(if (= (:status server-back) :success)
                  :div.notification.is-success.mt-4
                  :div.notification.is-danger.mt-4) (str (:content server-back))])
             (when-let [message @error]
               [:div.notification.is-warning.mt-4 message])]]
           (let [is-success-call (and (not (nil? server-back))
                                      (= (:status server-back) :success))]
             [:button.button.is-primary.is-fullwidth
              {:on-click (if is-success-call
                           (fn [_]
                             (reset! error nil)
                             (reset! user nil)
                             (reset! kind "愿望")
                             (reset! advice nil)
                             (rf/dispatch [:clean-wishlist-server-back])
                             (rf/dispatch [:app/hide-modal :wishlist]))
                           (fn [_]
                             (reset! error nil)
                             (cond (nil? @user) (reset! error "称呼不能为空")
                                   (nil? @kind) (reset! error "类别不能为空")
                                   (nil? @advice) (reset! error (str @kind "不能为空"))
                                   (< (count @advice) 10) (reset! error (str @kind "少于 10 个字。"))
                                   :else (rf/dispatch [:send-wishlist {:client @user
                                                                       :kind   @kind
                                                                       :advice @advice}]))))}
              (if is-success-call "关闭" "提交")])])]])
    [:pre "Powered by clojure & clojureScript.
Build with shadow-cljs, cljs-ajax, reagent, re-frame, react, bulma, http-kit, muuntaja, swagger, ring, mount, conman, cprop, cheshire, selmer, google closure compiler.
Managed by lein, maven and npm.
Data stored with postgreSQL.
Developed with firefox and IDEA.
All Open Source Software, no evil."]
    [:div
     [:img {:src "/img/made-with-bulma-semiwhite.png"
            :width "170px"
            :style {:margin-left :-10px
                    :vertical-align :-40%}}]
     [:span " © 2016-2022 Marvin Studio. All Right Reserved."]]]])