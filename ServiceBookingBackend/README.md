# 老年人服务预约系统 —— 后端(Spring Boot REST API)

对应技术方案 v1.2 的服务器端实现。客户端(网页/小程序)通过 HTTP + JSON 调用本服务,本服务再访问 MySQL,数据库密码只保存在服务器,不暴露给客户端。

> ✅ 本项目已通过 `mvn package` 编译打包验证,并已部署到云服务器实际运行、完成跨网全流程验证。

> 2026-07-15 新增的护工培训、答题与工作时间表功能已在本地编译和测试通过；线上服务器需要替换最新 JAR 并重启后才会生效。

## 📌 部署状态

- **已上线运行**:部署在阿里云 Windows Server,已用 nssm 注册为 Windows 服务(开机自启、后台常驻)。
- **已验证**:从外部电脑通过公网完成「注册用户/员工 → 员工接单 → 用户下单 → 查询预约」全流程,证明多客户端跨网访问可用。
- 访问地址、账号密码等敏感信息见 **《部署凭据与配置.md》**(⚠️ 含密码,勿提交公开仓库)。

## 📚 相关文档

| 文档 | 内容 |
|------|------|
| 本文件 README.md | 项目说明、本机运行、打包、接口清单、测试示例 |
| 部署凭据与配置.md | 服务器/数据库/JWT 凭据、初始账号、上线安全清单(⚠️ 敏感) |
| 服务器操作手册.md | 启动/停止/更新、nssm 开机自启、故障排查、数据库备份 |
| ../ServiceBookingSystem/技术方案-CS架构改造.md | 整体架构方案与团队分工 |

---

## 一、技术栈

- Java 21+(服务器实测 JDK 25 可运行)
- Spring Boot 3.3.5(内嵌 Tomcat)
- Spring JDBC(JdbcTemplate,直接写 SQL)
- MySQL 8
- JWT(jjwt)做登录令牌
- Maven 构建

## 二、目录结构

```
src/main/java/com/eldercare/
├── ServiceBookingBackendApplication.java   启动类
├── common/        统一返回 ApiResponse、业务异常、全局异常处理
├── config/        WebConfig(拦截器注册 + 跨域)
├── controller/    REST 接口:Auth/User/Employee/Service/Admin
├── dto/           请求/响应对象(注册、登录、预约、培训答题、工作时间)
├── entity/        实体:User/Employee/Admin/ServiceItem/Appointment
├── repository/    数据访问层(JdbcTemplate)
├── security/      JWT 工具、鉴权拦截器、当前登录身份
├── service/       业务逻辑:认证、预约、员工、管理员
└── util/          PasswordEncoder(PBKDF2,兼容旧数据)
src/main/resources/
├── application.yml   配置(数据库/JWT,均可用环境变量覆盖)
└── schema.sql        建表脚本 + 初始数据(含培训状态与工作时段表,启动时自动执行)
```

---

## 三、本机开发运行

### 1. 前置
- 本机装好 JDK(17+)和 MySQL,MySQL 里已能登录。
- 用 IntelliJ IDEA 打开本项目(它会自动识别 Maven 并下载依赖)。

### 2. 配置数据库连接(二选一)
数据库连接默认连本地 `service_booking_system` 库,用户 `root`、空密码。若你的不同,用**环境变量**覆盖(在 IntelliJ 运行配置的 Environment variables 填):

```
SERVICE_DB_PASSWORD=你的MySQL密码
```

如需改地址/账号,再加:
```
SERVICE_DB_URL=jdbc:mysql://localhost:3306/service_booking_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
SERVICE_DB_USER=root
```

> 数据库和表**不用手动建**:程序启动会自动执行 `schema.sql` 建库表(需数据库 `service_booking_system` 已存在;若未建库,先执行一次 `CREATE DATABASE service_booking_system DEFAULT CHARSET utf8mb4;`)。

### 3. 运行
运行 `ServiceBookingBackendApplication.java`。看到 `Started ServiceBookingBackendApplication` 即启动成功,服务监听 **8080** 端口。

### 4. 初始账号
启动后 `schema.sql` 会预置:
- **管理员**:用户名 `admin`,密码 `admin123`(⚠️ 上线前务必改)
- **5 个示例服务项目**:助浴、陪诊、做饭、保洁、陪聊

用户和员工账号通过注册接口创建。

---

## 四、打包成 jar(部署用)

在项目根目录执行(或用 IntelliJ 的 Maven 面板双击 `package`):

```bash
mvn package -DskipTests
```

产物:`target/service-booking-backend-1.0.0.jar`(可直接 `java -jar` 运行的胖 jar)。

---

## 五、部署到 Windows 服务器

> 前提:服务器已装 JDK(25)+ MySQL(参见部署手册)。

1. **建库**:在服务器 MySQL 里执行一次:
   ```sql
   CREATE DATABASE IF NOT EXISTS service_booking_system DEFAULT CHARSET utf8mb4;
   ```
   建议再建一个专用账号(弃用 root):
   ```sql
   CREATE USER 'booking_app'@'localhost' IDENTIFIED BY '强密码';
   GRANT ALL PRIVILEGES ON service_booking_system.* TO 'booking_app'@'localhost';
   FLUSH PRIVILEGES;
   ```

2. **上传 jar**:把 `service-booking-backend-1.0.0.jar` 传到服务器(远程桌面直接拖入,或用其他方式)。

3. **设置环境变量并启动**(在服务器上开 CMD):
   ```cmd
   set SERVICE_DB_USER=booking_app
   set SERVICE_DB_PASSWORD=强密码
   set SERVICE_JWT_SECRET=换成一段足够长的随机字符串至少32字节
   java -jar service-booking-backend-1.0.0.jar
   ```
   看到 `Started ServiceBookingBackendApplication` 即成功。

4. **放行端口**:在阿里云控制台**安全组**放行 **8080**(临时用 IP 访问)。数据库 3306 **不要**对公网放行。
   - 临时验证:浏览器访问 `http://服务器公网IP:8080/api/services`(需先登录拿 token,未登录会返回 401,能看到 JSON 说明服务通了)。

5. **后续正式上线**:备案通过后,用 Nginx 反向代理 8080 → 443,挂域名 + HTTPS(这一步等域名备案好了再做,我可以再给你 Nginx 配置)。

> 让服务长期后台运行:可用 Windows 的"任务计划程序"设开机自启,或用 `nssm` 把 jar 注册成 Windows 服务。需要时我可以提供脚本。

---

## 六、接口一览

统一返回格式:`{ "code": 0, "message": "成功", "data": ... }`,`code=0` 成功。
除 `/api/auth/**` 外,请求头需带 `Authorization: Bearer <登录返回的token>`。

| 角色 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 公共 | POST | `/api/auth/register` | 注册(role=USER/EMPLOYEE) |
| 公共 | POST | `/api/auth/login` | 登录,返回 token/role/name |
| 用户 | GET | `/api/users/me` | 我的信息 |
| 用户 | GET | `/api/services` | 服务项目列表 |
| 用户 | GET | `/api/employees/available?date=2026-07-15` | 某天可预约员工 |
| 用户 | POST | `/api/appointments` | 下预约单 |
| 用户 | GET | `/api/appointments/me?status=` | 我的预约+历史 |
| 用户 | PATCH | `/api/appointments/{id}/cancel` | 取消预约 |
| 员工 | GET | `/api/employees/me` | 我的信息 |
| 员工 | PATCH | `/api/employees/me/training/complete` | 确认完成岗前学习 |
| 员工 | POST | `/api/employees/me/training/quiz` | 提交 4 道培训题 |
| 员工 | GET/PUT | `/api/employees/me/availability` | 查询或保存每周可工作时段 |
| 员工 | PATCH | `/api/employees/me/status` | 切换工作状态 |
| 员工 | GET | `/api/employees/me/appointments` | 分配给我的预约 |
| 员工 | PATCH | `/api/employees/me/appointments/{id}/complete` | 标记完成 |
| 员工 | PATCH | `/api/employees/me/appointments/{id}/cancel` | 取消 |
| 管理员 | GET/POST/PUT/DELETE | `/api/admin/services` | 服务项目增删改查 |
| 管理员 | GET | `/api/admin/users` | 用户列表 |
| 管理员 | PATCH/DELETE | `/api/admin/users/{id}/active`、`/api/admin/users/{id}` | 禁用/删除用户 |
| 管理员 | GET | `/api/admin/employees` | 员工列表 |
| 管理员 | PATCH | `/api/admin/employees/{id}/training` | 为护工开放答题权限 |
| 管理员 | PATCH/DELETE | `/api/admin/employees/{id}/active`、`/api/admin/employees/{id}` | 禁用/删除员工 |
| 管理员 | GET | `/api/admin/appointments?status=` | 全部预约总览 |

---

## 七、Postman / curl 快速测试

**1. 登录管理员拿 token**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```
返回里的 `data.token` 就是令牌。

**2. 注册一个老人用户**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"role":"USER","username":"laoli","password":"123456","name":"李大爷","phone":"13800000000","age":70,"address":"北京市朝阳区"}'
```

**3. 注册一个员工**(注册后登录该员工并取得 token)
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"role":"EMPLOYEE","username":"nurse1","password":"123456","name":"王护工","phone":"13900000000","age":30,"salary":5000}'
```

**4. 员工完成学习并提交答题**(用员工 token)
```bash
curl -X PATCH http://localhost:8080/api/employees/me/training/complete \
  -H "Authorization: Bearer <员工token>"

curl -X POST http://localhost:8080/api/employees/me/training/quiz \
  -H "Authorization: Bearer <员工token>" \
  -H "Content-Type: application/json" \
  -d '{"answers":{"q1":"B","q2":"C","q3":"A","q4":"B"}}'
```

**5. 员工保存可工作时间并切换为接单**(用员工 token)
```bash
curl -X PUT http://localhost:8080/api/employees/me/availability \
  -H "Authorization: Bearer <员工token>" \
  -H "Content-Type: application/json" \
  -d '{"slots":["1_MORNING","1_AFTERNOON","3_EVENING"]}'

curl -X PATCH http://localhost:8080/api/employees/me/status \
  -H "Authorization: Bearer <员工token>" \
  -H "Content-Type: application/json" \
  -d '{"isWorking":true}'
```

**6. 老人查可预约员工并下单**(用老人 token)
```bash
curl "http://localhost:8080/api/employees/available?date=2026-07-15" -H "Authorization: Bearer <老人token>"

curl -X POST http://localhost:8080/api/appointments \
  -H "Authorization: Bearer <老人token>" \
  -H "Content-Type: application/json" \
  -d '{"employeeId":1,"serviceId":1,"appointmentDate":"2026-07-15"}'
```

**7. 老人查看自己的预约**
```bash
curl "http://localhost:8080/api/appointments/me" -H "Authorization: Bearer <老人token>"
```

---

## 八、关键设计说明(与技术方案对应)

- **三角色统一登录**:登录接口依次匹配 用户→员工→管理员;修复了原 JavaFX 版登录时"空值未 return"的逻辑缺陷。
- **密码安全**:沿用原项目 PBKDF2(12 万次迭代 + 随机盐);种子/旧明文密码在首次登录时自动升级为哈希。
- **防并发重复预约**:下单在事务中用 `SELECT ... FOR UPDATE` 行锁,保证"同一员工同一天""同一用户同一天"不被重复预约。
- **预约增强**:预约单含服务项目、日期(按天)、状态(待服务/已完成/已取消),支持历史查询。
- **岗前准入**:护工需完成学习并在 4 道题中答对至少 3 道；未通过者不能接单，也不会出现在可预约列表中。
- **工作时间持久化**:星期一至星期五的上午、下午、晚上选择保存在 `employee_availability` 表中。
- **权限隔离**:`/api/admin/**` 由拦截器强制要求 ADMIN 角色。
- **数据库凭据不落客户端**:全部通过服务器环境变量注入。

---

## 九、下一步(待办)

- [ ] 服务器备案通过后,配置 Nginx 反向代理 + HTTPS。
- [x] 前端(网页端)已对接培训、答题、工作时间和三角色业务接口。
- [ ] 上线前修改管理员默认密码、设置强 `SERVICE_JWT_SECRET`。
- [ ] (可选)接入短信/微信通知、把服务注册成 Windows 服务实现开机自启。
