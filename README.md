# CyberMe - 个人服务平台

一个 C/S 架构的 Web 应用，使用 clojure 和 clojurescript 搭建，承载了各种个人业务。

[![ClojureCI](https://github.com/corkine/cyberMe/actions/workflows/clojure.yml/badge.svg)](https://github.com/corkine/cyberMe/actions/workflows/clojure.yml) [![CircleCI](https://circleci.com/gh/corkine/cyberMe/tree/cyber-me.svg?style=svg&circle-token=793142488339016f1a9498b5b432c020629a96d7)](https://circleci.com/gh/corkine/cyberMe/tree/cyber-me) [![codecov](https://codecov.io/gh/corkine/cyberMe/branch/cyber-me/graph/badge.svg?token=W3119RL5SM)](https://codecov.io/gh/corkine/cyberMe) ![FOSSA](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fcorkine%2FcyberMe.svg?type=small)

## Prerequisites

使用 [Leiningen][1] 构建，依赖 postgreSQL 数据库。

[1]: https://github.com/technomancy/leiningen

## Features

- 使用 ClojureScript 和 Message Bus 应对前端状态变化，将副作用从视图分离。
- 使用 Clojure 的丰富表达能力、宏和动态特性实现鉴权并加快后端 API 开发。
- 使用 PostgresSQL 的 JSON 特性提供从前端直接到数据库的业务变更快速支持。

## Functions

- HCM 打卡信息计算、加班信息计算和相关策略和自动化服务
  - 后台定时从 HCM 获取打卡信息，并提供 API 以提供加班时长查询、工作时长计算、统计和打卡提醒功能（以及一个实验性质的自动化服务，依赖移动设备在特定时间段执行自动化服务，后台定时查询其任务是否派发，如果失败提供通知功能）。
  - 后端服务 `src/clj/cyberme/cyber/inspur.clj`
  - 前端组件 `src/cljs/cyberme/dashboard/core.cljs` `src/cljs/cyberme/work`
- 快递追踪通知服务 
  - 后台提供 API 从第三方服务商简单查询快递信息或(并)在后台定时确认快递更新，将更新信息推送到 iPhone Slack 频道。
  - 后端服务 `src/clj/cyberme/cyber/express.clj`
  - 前端组件 `src/cljs/cyberme/dashboard/core.cljs` 提供快递追踪录入表单。
- GPS 数据上报和轨迹追踪服务
  - 后台提供 API 从 iPhone 等设备采集 GPS 信息，联动百度鹰眼和百度地图进行位置解析和记录上报（根据配置），以绘制活动轨迹。
  - 后端服务 `src/clj/cyberme/cyber/track.clj`
- 跨设备便签同步服务
    - 后台提供 API 从 iPhone 快捷指令、Web 界面表单、Flutter APP 新建便签，然后跨设备从 iPhone 快捷指令、Web 界面表单、Flutter APP 获取便签。
    - 后端服务 `src/clj/cyberme/cyber/note.clj`
    - 前端组件 `src/cljs/cyberme/dashboard/core.cljs` 提供 Web 界面的便签输入和最近便签获取。
- 美剧更新通知服务
    - 后台定时从美剧网站查询感兴趣的美剧更新信息，存储到数据库并通知更新到 Web 界面或 iPhone Slack 频道。
    - 后端服务 `src/clj/cyberme/cyber/mini4k.clj`
    - 前端组件 `src/cljs/cyberme/dashboard/core.cljs` 提供美剧订阅录入表单和最近更新剧集展示。
- Microsoft TODO 同步服务
    - 后台定时从 Microsoft Graph API 获取 TODO 待办事项，和本地数据库进行交叉对比与同步，提供 API 进行展示和计分，此服务允许通过 OAuth 进行登录，用户凭证会自动刷新和维护。
    - 后端服务 `src/clj/cyberme/cyber/todo.clj`
    - 前端组件 `src/cljs/cyberme/dashboard/core.cljs` 提供 TODO 待办的展示。
- 物品管理和打包管理服务
    - 后端提供物品、位置、打包的增删改查服务，有 Web APP 和 Flutter APP 两个界面。
    - 后端服务 `src/clj/cyberme/cyber/goods.clj`
    - 前端组件 `src/cljs/cyberme/good` `src/cljs/cyberme/place` 提供美剧订阅录入表单和最近更新剧集展示。
- 心理学在线问卷和实验分发服务
    - 提供前端的心理学在线实验和后端的实验数据收集服务。
    - 后端服务 `src/clj/cyberme/cyber/psych.clj`
    - 前端组件 `src/cljs/cyberme/psych` 提供前端的心理学问卷和实验逻辑。
- 天气定期预报和预警服务
    - 后台定时获取彩云天气 API 并提供配置文件地点天气信息，推送到 Slack 频道，并提供 API 以供查询
    - 后端服务 `src/clj/cyberme/cyber/weather.clj`
- 分布式任务分发和回收服务与演示（浏览器操作模拟、代理和简单脚本）
    - 后台提供分布式的任务下发、定时超时回收与重试、任务结果整理服务。
    - 后端服务 `src/clj/cyberme/cyber/task.clj`
    - 前端爬虫 `demos/tasks` 中提供了基于 Firefox WebDriver 的带 Proxy 代理、重试的自动化服务
- 日记服务
    - 后端提供日记的增删改查服务，基于阿里云 OSS 的图片存储服务，有 Web APP 和 Flutter APP 两个界面。
    - 后端服务 `src/clj/cyberme/cyber/diary.clj` `src/clj/cyberme/cyber/file.clj`
    - 前端组件 `src/cljs/cyberme/diary` 提供日记的列表、单向展示，Markdown 编辑，图片拖拽上传等功能。
- 植物每天浇水和每周一学提醒服务
    - 后端提供植物每天浇水、每周一学数据库存储和相关 API，前端提供数据展示，增删改表单。
    - 后端服务 `src/clj/cyberme/cyber/diary.clj`
    - 前端组件 `src/cljs/cyberme/dashboard/core.clj` 提供日记的列表、单向展示，Markdown 编辑，图片拖拽上传等功能。
- 每日生活提醒和习惯保持：健身、饮食和运动，Apple Watch 健康数据分析服务
    - 后台提供 API 从 Apple Watch 和 iOS 健康应用通过自动化快捷指令上传饮食、站立、健身、心率和运动数据，进行按周计分，最长坚持计算并提供 Scriptable 小组件、Flutter APP 以及前端 Web 界面展示。
    - 后端服务 `src/clj/cyberme/cyber/fitness.clj`
    - 前端组件 `src/cljs/cyberme/dashboard/core.cljs` 提供可视化 Web 展示。
- 每周计划 KPI 与激励服务
    - 后台提供每周计划与完成进度跟踪的增删改查 API，前端提供增删改查界面，以跟踪锻炼、学习、工作和饮食 KPI
    - 后端服务 `src/clj/cyberme/cyber/week_plan.clj`
    - 前端界面 `src/cljs/cyberme/dashboard/week_plan.cljs` `src/cljs/cyberme/diary/edit.cljs`


## Running
    
```shell
//backend
lein repl

//frontend
lein shadow watch app
```

由 [Jetbrains OpenSource Support](https://jb.gg/OpenSourceSupport) 提供开发支持

<img src="https://resources.jetbrains.com/storage/products/company/brand/logos/IntelliJ_IDEA.png" width="300px">

Copyright © 2022 Marvin Studio, Wuhan, China.