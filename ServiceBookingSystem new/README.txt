老年人服务预约系统

当前版本使用 Java 17、JavaFX 17 和 MySQL 8，可在本机运行。
数据库账号与密码通过 SERVICE_DB_USER、SERVICE_DB_PASSWORD 环境变量设置。

当前 JavaFX 客户端仍直接连接本机 MySQL，并使用根目录 database.sql。
database/ 目录保存 C/S 三层架构的新版数据库脚本，需等待 Spring Boot 服务端接入后使用。

公网服务器：121.40.96.66
当前 Ping 测试超时，可能尚未开放 ICMP；Spring Boot API 尚未部署。

完整配置、编译命令、安全说明和改造进度请查看 README.md。
