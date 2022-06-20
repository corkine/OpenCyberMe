# 重力管家 - 状态数字化检测管理

一个模块化管理物品和个人状态的 Web 应用，使用 clojure 和 clojurescript 搭建。

[![ClojureCI](https://github.com/corkine/cyberMe/actions/workflows/clojure.yml/badge.svg)](https://github.com/corkine/cyberMe/actions/workflows/clojure.yml) [![CircleCI](https://circleci.com/gh/corkine/cyberMe/tree/cyber-me.svg?style=svg&circle-token=793142488339016f1a9498b5b432c020629a96d7)](https://circleci.com/gh/corkine/cyberMe/tree/cyber-me) [![codecov](https://codecov.io/gh/corkine/cyberMe/branch/cyber-me/graph/badge.svg?token=W3119RL5SM)](https://codecov.io/gh/corkine/cyberMe)

## Prerequisites

使用 [Leiningen][1] 构建，依赖 postgreSQL 数据库。

[1]: https://github.com/technomancy/leiningen

## Features

- 使用 ClojureScript 和 Message Bus 应对前端状态变化，将副作用从视图分离。
- 使用 Clojure 的丰富表达能力、宏和动态特性实现鉴权并加快后端 API 开发。
- 使用 PostgresSQL 的 JSON 特性提供从前端直接到数据库的业务变更快速支持。

## Functions

- HCM 打卡信息计算、加班信息计算和相关策略和自动化服务
- 每日生活提醒和习惯保持服务：健身、饮食和运动
- 快递追踪通知服务
- GPS 数据上报和轨迹追踪服务
- 跨设备便签同步服务
- 美剧更新通知服务
- Apple Watch 和 iOS 健康数据上传和分析服务
- Microsoft TODO 同步服务
- CMGoods 物品管理和打包管理服务
- CMDaily 日记服务
- 植物每天浇水和每周一学提醒服务
- 以上服务构成了一套可视化视图看板和数据统计与激励系统

## Running
    
```shell
//backend
lein repl

//frontend
lein shadow watch app
```

Copyright © 2022 Marvin Studio, Wuhan, China.