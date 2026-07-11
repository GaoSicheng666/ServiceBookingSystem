# 老年人服务预约系统

这是一个使用 JavaFX 和 MySQL 开发的桌面端服务预约系统。目前可在本机运行，支持普通用户和服务员工注册、登录、预约及取消预约。

项目正在从“JavaFX 客户端直连数据库”逐步改造为“客户端 → Spring Boot 服务端 → MySQL”的 C/S 三层架构。数据库 v2 设计已经完成，Spring Boot 服务端尚未接入，因此当前 JavaFX 程序仍使用旧版数据库模型。

## 当前运行状态

| 项目 | 当前状态 |
| --- | --- |
| JDK | Java 17.0.10 |
| JavaFX | JavaFX SDK 17.0.10 |
| 数据库 | 本机 MySQL 8.0，服务正常运行 |
| JDBC 驱动 | `lib/mysql-connector-j-9.7.0.jar` |
| 桌面客户端 | 已成功编译并运行 |
| 公网服务器 | `121.40.96.66` |
| Ping 测试 | ICMP 超时，可能未在安全组或防火墙中放行 |
| Spring Boot API | 尚未创建和部署 |

## 已实现功能

- 普通用户与服务员工注册、登录
- PBKDF2-SHA256 密码加盐存储，迭代次数 120000
- 旧版明文密码登录后自动升级为 PBKDF2
- 用户查看可预约员工并建立一对一预约
- 用户和员工取消当前预约
- 员工切换“工作中/暂停工作”状态
- MySQL 事务和行锁防止一名员工被重复分配
- 注册数据库写入失败时不再错误提示成功
- 数据库不可用时显示明确提示

## 注册约束

注册页面在填写过程中实时显示格式提示，用户名输入完成并离开输入框时自动查询数据库。

| 字段 | 约束 |
| --- | --- |
| 用户名 | 4–20 位字母、数字或下划线，全系统唯一 |
| 密码 | 8–32 位，必须同时包含字母和数字 |
| 姓名 | 2–50 个字符 |
| 手机号 | 11 位中国大陆手机号 |
| 普通用户年龄 | 1–120 岁 |
| 服务员工年龄 | 18–100 岁 |
| 地址 | 必填，最长 255 个字符 |
| 薪资 | 非负数字，最多两位小数 |

## 项目结构

```text
ServiceBookingSystem/
├─ src/                         JavaFX 源代码
│  ├─ Main.java                程序入口
│  ├─ AppManager.java          页面与数据管理器协调
│  ├─ LoginScene.java          登录页面
│  ├─ RegisterScene.java       注册页面及实时校验
│  ├─ UserDashboardScene.java  普通用户页面
│  ├─ EmployeeDashboardScene.java 员工页面
│  └─ DataManager.java         当前 JDBC 数据访问层
├─ lib/                        MySQL JDBC 驱动
├─ database.sql                当前 JavaFX 版本使用的旧版数据库脚本
├─ database/
│  ├─ schema-v2.sql            C/S 架构新版数据库结构
│  ├─ migrate-v1-to-v2.sql     旧库升级脚本
│  ├─ create-app-user.sql      服务端低权限数据库账号模板
│  └─ README.md                数据库部署说明
└─ server.env.example          未来 Spring Boot 服务端环境变量模板
```

## 数据库配置

当前客户端默认连接：

```text
jdbc:mysql://localhost:3306/service_booking_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
```

数据库账号和密码通过环境变量配置，禁止把真实密码写入源代码或提交到版本库：

```powershell
$env:SERVICE_DB_USER="root"
$env:SERVICE_DB_PASSWORD="你的MySQL密码"
```

生产环境应使用 [create-app-user.sql](database/create-app-user.sql) 创建低权限账号，不使用 `root`。

## 初始化当前数据库

当前 JavaFX 客户端使用根目录的 `database.sql`：

```powershell
mysql -u root -p --default-character-set=utf8mb4
```

进入 MySQL 后执行：

```sql
SOURCE E:/service/ServiceBookingSystem-master/ServiceBookingSystem-master/ServiceBookingSystem/database.sql;
```

不要把根目录的 `database.sql` 和 `database/schema-v2.sql` 混合用于当前客户端。v2 表结构需要配套的新服务端业务代码。

## 编译与运行

PowerShell 示例：

```powershell
$project = "E:\service\ServiceBookingSystem-master\ServiceBookingSystem-master\ServiceBookingSystem"
$fx = "$env:LOCALAPPDATA\openjfx\javafx-sdk-17.0.10\lib"

Set-Location $project
$env:SERVICE_DB_USER = "root"
$env:SERVICE_DB_PASSWORD = "你的MySQL密码"

New-Item -ItemType Directory -Force out | Out-Null
javac --module-path $fx --add-modules javafx.controls `
  -cp "lib\mysql-connector-j-9.7.0.jar" -encoding UTF-8 `
  -d out src\*.java

java --module-path $fx --add-modules javafx.controls `
  -cp "out;lib\mysql-connector-j-9.7.0.jar" Main
```

## 公网服务器与 C/S 改造

服务器公网 IP：`121.40.96.66`。

当前 Ping 测试超时。这通常表示阿里云安全组或服务器防火墙没有开放 ICMP，不足以单独判断服务器离线。后续部署前应分别检查 SSH、HTTP 和 HTTPS 端口。

规划中的地址：

- 临时联调：`http://121.40.96.66:8080/api`
- 正式环境：`https://已备案域名/api`
- MySQL：仅允许服务端通过 `127.0.0.1:3306` 访问，禁止公网开放 3306

建议云安全组仅开放：

- `22`：SSH，限制管理来源 IP
- `80`：HTTP，用于跳转 HTTPS 或证书验证
- `443`：HTTPS API
- `8080`：仅在临时联调期间限制来源开放，Nginx 上线后关闭

## 数据库 v2 改造内容

新版数据库脚本位于 `database/`，已经完成：

- 管理员表 `admins`
- 服务项目表 `services`
- 员工服务能力表 `employee_services`
- 支持服务项目、预约日期和历史状态的新预约表
- 取消预约软删除及完成时间记录
- 同一员工同一天只能存在一条待服务预约的数据库唯一约束
- 用户和员工的 `is_active` 禁用字段
- 面向 Spring Boot 服务端的最小权限数据库账号模板

尚未完成：

- Spring Boot 服务端工程
- REST API、JWT 登录和角色权限控制
- JavaFX 客户端由 JDBC 改为 HTTP API
- Nginx、域名、HTTPS 和服务器部署
- 管理员后台及新版按日期/服务项目预约界面

## 安全注意事项

- 不要将 MySQL 3306 暴露到公网
- 不要在客户端中保存生产数据库密码
- 不要使用 `root` 作为生产应用账号
- 正式 API 必须使用 HTTPS
- 管理接口必须验证管理员角色
- 修改数据库结构前先使用 `mysqldump --single-transaction` 备份

