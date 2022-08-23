# CyberMe 脚本集合

此处存放着配合 CyberMe 服务端使用的信息采集脚本，用来执行像上传磁盘文件元数据到 CyberMe 数据库、上传 Calibre 数据库到 CyberMe、打包本地文件夹到阿里云 OSS 等工作。 

一些敏感的用户凭据从环境变量中读取。

每个脚本都可以独立的通过 [clj-runner](https://github.com/corkine/clj-runner) 执行，clj-runner 判断 .clj 文件头，并且决定使用何种方式来运行脚本：使用 `#!/usr/bin/env bb` 开头的交给 babashka 运行，其余的将第一行注释转换为命令行执行，比如 `bb clojure -Sdeps '{:paths ["."]}' -M -m xx.clj` 或者 `clojure -Sdeps '{:paths ["."]}' -M -m xx.clj` —— Clojure CLI 将依赖本地 .m2 仓库缓存的库执行代码，这种动态调用让大部分脚本的任务能够比 bash/powershell 脚本更快运行，而少部分复杂任务、项目和后台服务也可以在 JVM 虚拟机上高效执行。在 Windows下，将 clj-runner 关联 .clj 文件后可双击执行 Clojure 脚本。

