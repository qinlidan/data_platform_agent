# 数据开发平台 (Data Platform)

## 项目概述

数据开发平台：支持 DB2HIVE / HIVE2HIVE ETL 作业的创建、SQL 配置、字段元数据解析、发布与多版本管理，并集成 Spring AI 实现自然语言创建/修改/发布/删除作业。

- 后端：Spring Boot 3.2.2 + Java 17 + MySQL 5.7 + MyBatis-Plus + Spring AI 1.0.0 GA
- 前端：Vue 3 + Element Plus + Vite + Vue Router + axios

## 前端 UI 规范（重要，务必遵守）

- **必须使用 Element Plus 原生（浅色）风格**，禁止自定义暗色主题。
- 禁止在 `main.js` 引入 `element-plus/theme-chalk/dark/css-vars.css`。
- 禁止全局覆盖 Element Plus 的按钮 / 背景 / 文字颜色（不要自定义 `.el-button--*` 颜色，不要给 `#app` / `body` / `:root` 设置暗色背景）。
- 所有页面（作业管理、数据源管理、AI 助手、新建作业等）UI 风格保持一致。

## 开发约定

- 后端接口统一返回 `ApiResponse<T>`（`{code, message, data}`，成功 `code=200`）。
- 前端 axios 实例在 `frontend/src/utils/request.js`（`baseURL: '/api'`，响应拦截器已解包 `res.data`）；接口封装在 `frontend/src/api/index.js`（具名导出，无 default）。
- 前端开发服务器端口 3000，代理 `/api` → `http://localhost:8089`。
- Maven 本地仓库在 `D:\idea\MavenReposity`（由 settings.xml 配置，不在默认 `~/.m2`）。
