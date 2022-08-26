# CyberMe Script Collection

此处存放着配合 CyberMe 服务端使用的信息采集脚本。 一些敏感的用户凭据从环境变量中读取。

## clj_runner.go && *.clj

clj-runner 是一个根据 .clj 脚本文件头行判断如何执行此文件的“派发器”：[仓库](https://github.com/corkine/clj-runner)。

每个 .clj 脚本都可以独立的通过 clj-runner 执行，其判断 .clj 文件头，并且决定使用何种方式来运行脚本：使用 `#!/usr/bin/env bb` 开头的交给 babashka 运行，其余的将第一行注释转换为命令行执行，比如 `bb clojure -Sdeps '{:paths ["."]}' -M -m xx.clj` 或者 `clojure -Sdeps '{:paths ["."]}' -M -m xx.clj` —— Clojure CLI 将依赖本地 .m2 仓库缓存的库执行代码，这种动态调用让大部分脚本的任务能够比 bash/powershell 脚本更快运行，而少部分复杂任务、项目和后台服务也可以在 JVM 虚拟机上高效执行。在 Windows下，将 clj-runner 关联 .clj 文件后可双击执行 Clojure 脚本。

## zendao.js

一个自动将 CyberMe 中的当日 Microsoft TODO 待办事项 - 工作列表部分写入禅道日报系统字段的脚本，配合 Tampermonkey 使用。

<img src="../screenshorts/zen1.jpg" width="300" alt="点击填报">

<img src="../screenshorts/zen2.jpg" width="723" alt="确定填报">

<img src="../screenshorts/zen3.jpg" width="520" alt="完成填报">

## overtime.js

一个自动将内部加班系统点击事件上传到 CyberMe 的脚本，配合 Tampermonkey 使用。

## scriptable.js

一个依赖 CyberMe API 在 iOS 桌面小组件显示信息的 JavaScript 脚本，配合 Scriptable 使用。

<img src="../screenshorts/scriptable.jpg" width="420" alt="Scriptable 脚本">
