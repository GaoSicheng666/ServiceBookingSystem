# 老年人服务预约系统前端

这是一个原生 HTML/CSS/JavaScript 单页前端，用于对接 `ServiceBookingBackend` 的 Spring Boot REST API。

## 使用方式

直接打开：

```text
ServiceBookingFrontend/index.html
```

默认后端地址：

```text
http://121.40.96.66:8080/api
```

如果后端地址变化，可以在页面左侧的 `Base URL` 中修改并保存。

## 前端不需要 MySQL

本前端只通过 HTTP/JSON 调用后端接口，不连接 MySQL。MySQL 只由后端服务器使用。

## 已覆盖页面

- 登录
- 注册老人用户
- 注册护工员工
- 老人端：查看服务项目、选择日期、查询员工、下预约单、查看和取消预约
- 员工端：切换接单状态、查看分配预约、完成或取消预约
- 管理员端：服务项目管理、用户管理、员工管理、预约总览

## 后端接口约定

登录成功后保存后端返回的 JWT token。除注册和登录外，请求都会自动携带：

```text
Authorization: Bearer <token>
```
