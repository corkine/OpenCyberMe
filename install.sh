echo "========================================================================="
echo ""
echo "Init boot sequence, you should install git, postgreSQL, jdk, npm,
lein done and set dev-config.edn well."

# 下载安装必须的软件包：git、jvm、npm
# yum -y install git java-1.8.0-openjdk-devel.x86_64 npm

# 下载安装 lein
# chmod +x lein.sh
# ./lein.sh

# 下载安装 postgreSQL 14
# sudo yum install -y https://download.postgresql.org/pub/repos/yum/reporpms/EL-7-x86_64/pgdg-redhat-repo-latest.noarch.rpm
# sudo yum install -y postgresql14-server
# sudo /usr/pgsql-14/bin/postgresql-14-setup initdb
# sudo systemctl enable postgresql-14
# sudo systemctl start postgresql-14
# su - postgres
# psql -U postgres
# alter user postgres with password 'xxxxx'
# \q
# exit
# vi /var/lib/pgsql/14/data/postgresql.conf
# listen_address = '*'
# vi /var/lib/pgsql/14/data/pg_hba.conf
# host all all 0.0.0.0/0 trust/md5 (记得初次连接后改回密码验证)
# systemctl restart postgresql-14
# firewall-cmd --zone=public --add-port=5432/tcp --permanent
# firewall-cmd --reload

# 配置 git 密码存储，获取最新分支代码
# git config --global credential.helper store
# git checkout -b cm-goods origin/cm-goods
# git pull

# 配置数据库 schema
# ./lein.sh repl
# (start)
# (migration)

git pull
kill $(ps axu | grep "leiningen.core.main run" | grep -v grep | awk '{print $2}')
./lein.sh shadow release app
nohup ./lein.sh run 1>>/var/log/app.log 2>&1 &

echo "Server run on Port `ps aux | grep "leiningen.core.main run" | grep -v grep | awk '{print $2}'`"
echo ""
echo "========================================================================="

# example dev-config.edn:
# {:dev true
#  :port 3002
#  :nrepl-port 7002
#  :database-url
#  "postgresql://localhost:5432/ice?user=xxx&password=xxx&stringtype=unspecified"}