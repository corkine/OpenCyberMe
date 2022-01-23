# ICE Manager

ICE 特性（文档和评审）管理器，使用 clojure 和 clojurescript 搭建。

## Prerequisites

使用 [Leiningen][1] 构建，依赖 postgreSQL 数据库。

[1]: https://github.com/technomancy/leiningen

## Features

- 使用 cljs 提供 “格式化 ICE 特性数据” 的前端交互接口：功能丰富，实用与美观并重。
- 使用 clojure 基于 “格式化 ICE 特性数据” 生成丰富的展示：HTML 文档，DOCX 文档，PDF 文档，TR 和评审文档等。

## Running
    
    //backend
    lein repl

    //frontend
    lein shadow watch app

Copyright © 2022 Corkine Ma & Inspur Cisco Networking Technology Co.Ltd, Wuhan, China.