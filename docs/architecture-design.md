# 数据开发平台架构设计文档

## 1. 系统概述

数据开发平台是一个用于管理和配置数据同步作业的系统，支持 DB2Hive 和 Hive2Hive 两种作业类型，具备完整的作业生命周期管理、版本控制和元数据管理能力。

## 2. 技术栈

- **后端框架**: Spring Boot 3.x
- **Java 版本**: Java 17
- **数据库**: MySQL 5.7
- **ORM 框架**: MyBatis-Plus 3.x
- **认证授权**: Spring Security + JWT
- **API 文档**: SpringDoc OpenAPI (Swagger)
- **构建工具**: Maven 3.8+
- **其他**: Lombok, Hutool, Jackson

## 3. 系统架构

### 3.1 分层架构

```
┌─────────────────────────────────────────┐
│         前端层 (Vue3/React)              │
├─────────────────────────────────────────┤
│         Controller Layer                 │
│         (RESTful API)                    │
├─────────────────────────────────────────┤
│         Service Layer                    │
│   ┌──────────┬──────────┬─────────┐    │
│   │  User    │   Job    │ Version │    │
│   │ Service  │ Service  │ Service │    │
│   └──────────┴──────────┴─────────┘    │
├─────────────────────────────────────────┤
│         Mapper Layer                     │
│         (MyBatis-Plus)                   │
├─────────────────────────────────────────┤
│         MySQL Database                   │
└─────────────────────────────────────────┘
```

### 3.2 核心模块

1. **用户认证模块** (auth)
   - 登录/登出
   - JWT Token 管理
   - 权限控制

2. **作业管理模块** (job)
   - 作业 CRUD
   - 作业类型管理 (DB2Hive, Hive2Hive)
   - 作业配置管理

3. **版本管理模块** (version)
   - 作业版本创建
   - 版本发布/回滚
   - 版本历史查询

4. **数据源管理模块** (datasource)
   - 数据源配置
   - 连接测试

5. **元数据管理模块** (metadata)
   - 字段元数据管理
   - SQL 解析

## 4. 数据库设计

### 4.1 用户表 (sys_user)

```sql
CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(128) NOT NULL COMMENT '密码(加密)',
  `real_name` VARCHAR(50) COMMENT '真实姓名',
  `email` VARCHAR(100) COMMENT '邮箱',
  `phone` VARCHAR(20) COMMENT '手机号',
  `status` TINYINT DEFAULT 1 COMMENT '状态:0-禁用,1-启用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` VARCHAR(50) COMMENT '创建人',
  `update_by` VARCHAR(50) COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

### 4.2 数据源表 (data_source)

```sql
CREATE TABLE `data_source` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `source_code` VARCHAR(50) NOT NULL COMMENT '数据源编码',
  `source_name` VARCHAR(100) NOT NULL COMMENT '数据源名称',
  `source_type` VARCHAR(20) NOT NULL COMMENT '数据源类型:MYSQL,HIVE,ORACLE等',
  `jdbc_url` VARCHAR(500) COMMENT 'JDBC连接URL',
  `username` VARCHAR(100) COMMENT '用户名',
  `password` VARCHAR(200) COMMENT '密码(加密)',
  `description` VARCHAR(500) COMMENT '描述',
  `status` TINYINT DEFAULT 1 COMMENT '状态:0-禁用,1-启用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` VARCHAR(50) COMMENT '创建人',
  `update_by` VARCHAR(50) COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_code` (`source_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源表';
```

### 4.3 作业表 (etl_job)

```sql
CREATE TABLE `etl_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_code` VARCHAR(100) NOT NULL COMMENT '作业英文名(唯一标识)',
  `job_name` VARCHAR(200) NOT NULL COMMENT '作业中文名',
  `job_type` VARCHAR(20) NOT NULL COMMENT '作业类型:DB2HIVE,HIVE2HIVE',
  `source_datasource_id` BIGINT COMMENT '源数据源ID',
  `source_database` VARCHAR(100) COMMENT '源库名',
  `source_table` VARCHAR(100) COMMENT '源表名',
  `target_database` VARCHAR(100) NOT NULL COMMENT '目标库名',
  `target_table` VARCHAR(100) NOT NULL COMMENT '目标表名',
  `engine_type` VARCHAR(20) NOT NULL COMMENT '执行引擎:HIVE,SPARK',
  `current_version` INT DEFAULT 0 COMMENT '当前版本号',
  `published_version` INT DEFAULT 0 COMMENT '已发布版本号',
  `status` VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态:DRAFT-草稿,PUBLISHED-已发布,ARCHIVED-已归档',
  `description` VARCHAR(1000) COMMENT '描述',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` VARCHAR(50) COMMENT '创建人',
  `update_by` VARCHAR(50) COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_code` (`job_code`),
  KEY `idx_job_type` (`job_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ETL作业表';
```

### 4.4 作业版本表 (etl_job_version)

```sql
CREATE TABLE `etl_job_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_id` BIGINT NOT NULL COMMENT '作业ID',
  `version` INT NOT NULL COMMENT '版本号',
  `sql_content` TEXT COMMENT 'SQL映射逻辑',
  `spark_config` TEXT COMMENT 'Spark参数配置(JSON格式)',
  `is_published` TINYINT DEFAULT 0 COMMENT '是否已发布:0-未发布,1-已发布',
  `publish_time` DATETIME COMMENT '发布时间',
  `publish_by` VARCHAR(50) COMMENT '发布人',
  `change_log` VARCHAR(1000) COMMENT '变更日志',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_by` VARCHAR(50) COMMENT '创建人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_version` (`job_id`, `version`),
  KEY `idx_job_id` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ETL作业版本表';
```

### 4.5 字段元数据表 (etl_field_metadata)

```sql
CREATE TABLE `etl_field_metadata` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_version_id` BIGINT NOT NULL COMMENT '作业版本ID',
  `field_name` VARCHAR(100) NOT NULL COMMENT '字段名',
  `field_type` VARCHAR(50) NOT NULL COMMENT '字段类型',
  `field_comment` VARCHAR(500) COMMENT '字段注释',
  `is_partition` TINYINT DEFAULT 0 COMMENT '是否分区字段:0-否,1-是',
  `is_primary` TINYINT DEFAULT 0 COMMENT '是否主键:0-否,1-是',
  `is_nullable` TINYINT DEFAULT 1 COMMENT '是否可为空:0-否,1-是',
  `default_value` VARCHAR(200) COMMENT '默认值',
  `field_order` INT COMMENT '字段顺序',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_version_id` (`job_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段元数据表';
```

## 5. 核心业务流程

### 5.1 作业创建流程

```
1. 用户填写作业基本信息
   ↓
2. 选择作业类型(DB2HIVE/HIVE2HIVE)
   ↓
3. 配置数据源和表信息
   ↓
4. 选择执行引擎(HIVE/SPARK)
   ↓
5. 配置Spark参数(可选)
   ↓
6. 保存作业(草稿状态)
   ↓
7. 创建版本1(自动)
```

### 5.2 SQL编写和字段配置流程

```
1. 在版本中编写SQL映射逻辑
   ↓
2. 系统解析SQL获取字段列表
   ↓
3. 用户填写字段元数据信息
   ↓
4. 保存版本配置
```

### 5.3 作业发布流程

```
1. 选择要发布的版本
   ↓
2. 校验版本配置完整性
   ↓
3. 发布版本
   ↓
4. 更新作业状态为PUBLISHED
   ↓
5. 记录发布历史
```

## 6. API 设计

### 6.1 用户认证 API

- `POST /api/auth/login` - 用户登录
- `POST /api/auth/logout` - 用户登出
- `GET /api/auth/info` - 获取当前用户信息

### 6.2 作业管理 API

- `POST /api/jobs` - 创建作业
- `PUT /api/jobs/{id}` - 更新作业
- `DELETE /api/jobs/{id}` - 删除作业
- `GET /api/jobs/{id}` - 获取作业详情
- `GET /api/jobs` - 分页查询作业列表(支持筛选)

### 6.3 版本管理 API

- `POST /api/jobs/{jobId}/versions` - 创建新版本
- `PUT /api/jobs/{jobId}/versions/{version}` - 更新版本
- `GET /api/jobs/{jobId}/versions` - 获取版本列表
- `POST /api/jobs/{jobId}/versions/{version}/publish` - 发布版本
- `POST /api/jobs/{jobId}/versions/{version}/rollback` - 回滚版本

### 6.4 字段元数据 API

- `POST /api/jobs/{jobId}/versions/{version}/fields` - 批量保存字段元数据
- `GET /api/jobs/{jobId}/versions/{version}/fields` - 获取字段列表
- `POST /api/jobs/{jobId}/versions/{version}/parse-sql` - 解析SQL获取字段

### 6.5 数据源管理 API

- `POST /api/datasources` - 创建数据源
- `PUT /api/datasources/{id}` - 更新数据源
- `DELETE /api/datasources/{id}` - 删除数据源
- `GET /api/datasources` - 获取数据源列表
- `POST /api/datasources/{id}/test` - 测试数据源连接

## 7. 项目结构

```
data-platform
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com.dataplatform
│   │   │       ├── DataPlatformApplication.java
│   │   │       ├── config                  # 配置类
│   │   │       │   ├── SecurityConfig.java
│   │   │       │   ├── MyBatisPlusConfig.java
│   │   │       │   └── SwaggerConfig.java
│   │   │       ├── controller              # 控制器
│   │   │       │   ├── AuthController.java
│   │   │       │   ├── JobController.java
│   │   │       │   ├── JobVersionController.java
│   │   │       │   ├── FieldMetadataController.java
│   │   │       │   └── DataSourceController.java
│   │   │       ├── service                 # 服务层
│   │   │       │   ├── IUserService.java
│   │   │       │   ├── IJobService.java
│   │   │       │   ├── IJobVersionService.java
│   │   │       │   ├── IFieldMetadataService.java
│   │   │       │   ├── IDataSourceService.java
│   │   │       │   └── impl
│   │   │       ├── mapper                  # 数据访问层
│   │   │       │   ├── UserMapper.java
│   │   │       │   ├── JobMapper.java
│   │   │       │   ├── JobVersionMapper.java
│   │   │       │   ├── FieldMetadataMapper.java
│   │   │       │   └── DataSourceMapper.java
│   │   │       ├── entity                  # 实体类
│   │   │       │   ├── User.java
│   │   │       │   ├── Job.java
│   │   │       │   ├── JobVersion.java
│   │   │       │   ├── FieldMetadata.java
│   │   │       │   └── DataSource.java
│   │   │       ├── dto                     # 数据传输对象
│   │   │       │   ├── request
│   │   │       │   └── response
│   │   │       ├── vo                      # 视图对象
│   │   │       ├── enums                   # 枚举
│   │   │       │   ├── JobType.java
│   │   │       │   ├── EngineType.java
│   │   │       │   ├── JobStatus.java
│   │   │       │   └── DataSourceType.java
│   │   │       ├── common                  # 公共类
│   │   │       │   ├── Result.java
│   │   │       │   ├── PageResult.java
│   │   │       │   └── Constants.java
│   │   │       ├── exception              # 异常处理
│   │   │       │   ├── GlobalExceptionHandler.java
│   │   │       │   └── BizException.java
│   │   │       ├── security               # 安全相关
│   │   │       │   ├── JwtUtil.java
│   │   │       │   ├── JwtAuthenticationFilter.java
│   │   │       │   └── UserDetailsServiceImpl.java
│   │   │       └── util                   # 工具类
│   │   │           ├── SqlParserUtil.java
│   │   │           └── EncryptUtil.java
│   │   └── resources
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── mapper                     # MyBatis XML
│   └── test
├── pom.xml
└── README.md
```

## 8. 核心功能设计要点

### 8.1 作业版本管理

- 每次修改作业配置或SQL时创建新版本
- 支持版本对比
- 只有发布的版本才能用于生产执行
- 支持版本回滚

### 8.2 SQL 解析

- 使用 JSqlParser 或 Calcite 解析 SQL
- 提取字段名、类型、表达式
- 自动识别源表和目标表的映射关系

### 8.3 Spark 参数配置

存储为 JSON 格式，示例：
```json
{
  "spark.executor.memory": "4g",
  "spark.executor.cores": "2",
  "spark.driver.memory": "2g",
  "spark.sql.shuffle.partitions": "200"
}
```

### 8.4 权限控制

- 基于角色的访问控制(RBAC)
- 作业创建者拥有完整权限
- 支持作业级别的权限分配

## 9. 非功能性需求

### 9.1 安全性

- 密码使用 BCrypt 加密
- JWT Token 有效期管理
- 数据源密码加密存储
- SQL 注入防护

### 9.2 性能

- 作业列表分页查询
- 使用索引优化查询
- 版本历史分页加载

### 9.3 可扩展性

- 支持新增作业类型
- 支持新增数据源类型
- 插件化架构设计

## 10. 部署架构

```
┌─────────────────┐
│   Nginx/Gateway │
└────────┬────────┘
         │
┌────────▼────────┐
│  Spring Boot    │
│   Application   │
└────────┬────────┘
         │
┌────────▼────────┐
│   MySQL 5.7     │
└─────────────────┘
```

## 11. 后续优化方向

1. 添加调度功能(集成 XXL-JOB 或 DolphinScheduler)
2. 作业执行监控和日志
3. 数据质量校验
4. 元数据血缘分析
5. 作业依赖管理
6. 审计日志
7. 操作记录和回滚
