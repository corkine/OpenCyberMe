# 分布式任务调度平台示例

- gk2022.clj 展示了模拟浏览器运行、手动滑块的任务调度客户端示例。
- gk2022_proxy.clj 是一个带 HTTP(S) 代理和 n 次自动重启浏览器实例的实现。
- zk2022.clj 展示了直接解析简单网页的任务调度客户端示例。

## Usage

### gk2022

如下所示，准备好 package 最终分发文件夹下的浏览器（对应操作系统和指令集）、驱动（对应浏览器版本）、Java Runtime、依赖库、Clojure 脚本文件和执行脚本，双击执行脚本 run.bat 以在 JRE 和依赖库的支持下运行对应的 gk2022.clj 脚本文件。

> 依赖库来自重写 project.clj 后（如果想要包更小，可跳过此步骤）`lein uberjar` 编译后解压 jar 包得到的 Java 字节码

```clojure
;write with lein project.clj:
(defproject auto_browser "0.1.0-SNAPSHOT"
            :description "分布式爬虫客户端"
            :url "https://mazhangjing.com"
            :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
                      :url "https://www.eclipse.org/legal/epl-2.0/"}
            :dependencies [[org.clojure/clojure "1.10.1"]
                           [etaoin "0.4.6"]
                           [cheshire "5.10.0"]
                           [luminus-http-kit "0.1.9"]]
            :repl-options {:init-ns gk2022}
            ;:aot :all
            :main gk2022)

;prepare package folder with browser, webdriver, jre, library and run script
;|-- jre1.8.0_301/
;|-- firefox/
;|-- classes/
;|-- geckodriver.exe
;|-- gk2022.clj
;|-- run_gk.bat
;    .\jre1.8.0_301\bin\java.exe -cp "classes;." clojure.main -m gk2022
```

### zk2022

一个简单的没有 IP 和验证码限制的任务客户端实现，使用方式和上面类似，重定义 project.clj（如果需要精简包大小，可跳过此步骤），然后准备好分发文件夹即可（不需要浏览器和浏览器驱动）：

```clojure
;write with lein project.clj:
(defproject auto_browser "0.1.0-SNAPSHOT"
            :description "分布式爬虫客户端"
            :url "https://mazhangjing.com"
            :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
                      :url "https://www.eclipse.org/legal/epl-2.0/"}
            :dependencies [[org.clojure/clojure "1.10.1"]
                           [cheshire "5.10.0"]
                           [luminus-http-kit "0.1.9"]]
            :repl-options {:init-ns zk2022}
            ;:aot :all
            :main zk2022)
;prepare package folder with browser, webdriver, jre, library and run script
;|-- jre1.8.0_301/
;|-- classes/
;|-- zk2022.clj
;|-- run_zk.bat
;    .\jre1.8.0_301\bin\java.exe -cp "classes;." clojure.main -m zk2022
```

## License

Copyright © 2022 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
