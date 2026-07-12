# 老年人服务预约系统前端

这是一个原生 HTML/CSS/JavaScript 单页前端，用于对接 `ServiceBookingBackend` 的 Spring Boot REST API。

## 使用方式

直接打开：

```text
ServiceBookingFrontend/index.html
```

后端地址固定配置在 `app.js` 中：

```text
http://121.40.96.66:8080/api
```

## 前端不需要 MySQL

本前端只通过 HTTP/JSON 调用后端接口，不连接 MySQL。MySQL 只由后端服务器使用。

## 已覆盖页面

- 登录
- 注册老人用户
- 注册护工员工
- 老人端：适老化服务首页、四步预约向导、卡片式预约记录、查看和取消预约
- 员工端：今日任务工作台、接单状态开关、下一任务、电话联系、高德导航、完成或取消任务
- 管理员端：服务项目管理、用户管理、员工管理、预约总览
- 品牌图标：页面左上角与浏览器标签页使用 `assets/app-icon.png`
- 登录页插画：未登录时使用 `assets/auth-care-illustration.png` 展示独立欢迎页

## 后端接口约定

登录成功后保存后端返回的 JWT token。除注册和登录外，请求都会自动携带：

```text
Authorization: Bearer <token>
```
