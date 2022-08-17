# CyberMe 脚本集合

此处存放着配合 CyberMe 服务端使用的信息采集脚本，用来执行像上传磁盘文件元数据到 CyberMe 数据库、上传 Calibre 数据库到 CyberMe、打包本地文件夹到阿里云 OSS 等工作。 

一些敏感的用户凭据从环境变量中读取。

每个脚本都可以独立的通过 [clj-runner](https://github.com/corkine/clj-runner) 关联 .clj 文件后双击执行 —— clj-runner 是一个 Go 开发的简单程序，其让 .clj 文件可以像通过安装包安装的 Python 环境一样，双击 .py 脚本直接运行 —— 使用本地缓存的 .m2 依赖库，依赖 clojure CLI。

