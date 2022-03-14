# 重力管家 - 状态数字化检测管理

一个模块化管理物品和个人状态的 Web 应用，使用 clojure 和 clojurescript 搭建。

CI by Circle CI & Github Actions

[![CircleCI](https://circleci.com/gh/corkine/cyberMe/tree/cyber-me.svg?style=svg&circle-token=793142488339016f1a9498b5b432c020629a96d7)](https://circleci.com/gh/corkine/cyberMe/tree/cyber-me)

[![Clojure CI](https://github.com/corkine/cyberMe/actions/workflows/clojure.yml/badge.svg)](https://github.com/corkine/cyberMe/actions/workflows/clojure.yml)

## Prerequisites

使用 [Leiningen][1] 构建，依赖 postgreSQL 数据库。

[1]: https://github.com/technomancy/leiningen

## Features

- 使用 ClojureScript 和 Message Bus 应对前端状态变化，将副作用从视图分离。
- 使用 Clojure 的丰富表达能力、宏和动态特性实现鉴权并加快后端 API 开发。
- 使用 PostgreSQL 的 JSON 特性提供从前端直接到数据库的业务变更快速支持。

## Running
    
    //backend
    lein repl

    //frontend
    lein shadow watch app

Copyright © 2022 Marvin Studio, Wuhan, China.