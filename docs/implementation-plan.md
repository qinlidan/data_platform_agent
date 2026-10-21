# 数据开发平台实施计划

## Context（背景）

用户需要构建一个完整的数据开发平台，用于管理和配置数据同步作业。该平台需要支持：

1. **作业类型**: DB2Hive、Hive2Hive 两种数据同步作业
2. **核心功能**: 作业的创建、修改、发布，以及完整的版本管理
3. **配置管理**: 数据源配置、SQL映射逻辑、执行引擎选择（Hive/Spark）、Spark参数配置
4. **元数据管理**: SQL字段解析、字段元数据信息维护
5. **用户系统**: 登录认证、权限控制
6. **查询功能**: 作业列表展示、多条件筛选

技术栈要求：Spring Boot 3、MySQL 5.7、MyBatis-Plus、Java 17

## 实施方案

### 第一阶段：项目初始化和基础架构搭建

#### 1.1 创建 Spring Boot 项目结构

**项目基本信息**:
- GroupId: `com.dataplatform`
- ArtifactId: `data-platform`
- Java 版本: 17
- Spring Boot 版本: 3.2.x

**核心依赖** (pom.xml):
```xml
<!-- Spring Boot Web -->
spring-boot-starter-web

<!-- Spring Security + JWT -->
spring-boot-starter-security
jjwt-api, jjwt-impl, jjwt-jackson (0.12.x)

<!-- MyBatis-Plus -->
mybatis-plus-boot-starter (3.5.x)

<!-- MySQL Driver -->
mysql-connector-j (8.0.x)

<!-- 工具类 -->
lombok
hutool-all (5.8.x)

<!-- API文档 -->
springdoc-openapi-starter-webmvc-ui (2.x)

<!-- SQL解析 -->
jsqlparser (4.x)
```

**目录结构**:
```
src/main/java/com/dataplatform/
├── DataPlatformApplication.java
├── config/              # 配置类
├── controller/          # REST API控制器
├── service/            # 业务逻辑层
├── mapper/             # 数据访问层
├── entity/             # 数据库实体
├── dto/                # 请求/响应对象
├── enums/              # 枚举定义
├── common/             # 公共类
├── exception/          # 异常处理
├── security/           # 安全组件
└── util/               # 工具类
```

#### 1.2 配置文件设置

**application.yml** 配置项：
- 数据库连接配置（MySQL 5.7）
- MyBatis-Plus 配置（逻辑删除、自动填充）
- JWT配置（密钥、过期时间）
- 日志配置
- Swagger配置

### 第二阶段：数据库设计和初始化

#### 2.1 数据库表设计

创建以下核心表：

1. **sys_user** - 用户表
   - 字段：id, username, password, real_name, email, phone, status
   - 索引：username唯一索引

2. **data_source** - 数据源表
   - 字段：id, source_code, source_name, source_type, jdbc_url, username, password
   - 索引：source_code唯一索引

3. **etl_job** - 作业主表
   - 字段：id, job_code, job_name, job_type, source_datasource_id, source_database, source_table, target_database, target_table, engine_type, current_version, published_version, status
   - 索引：job_code唯一索引、job_type索引、status索引

4. **etl_job_version** - 作业版本表
   - 字段：id, job_id, version, sql_content, spark_config(JSON), is_published, publish_time, change_log
   - 索引：(job_id, version)联合唯一索引

5. **etl_field_metadata** - 字段元数据表
   - 字段：id, job_version_id, field_name, field_type, field_comment, is_partition, is_primary, is_nullable, default_value, field_order
   - 索引：job_version_id索引

**建表脚本位置**: `src/main/resources/db/schema.sql`

#### 2.2 初始化数据

- 创建默认管理员账户（admin/admin123）
- 插入常用数据源类型枚举数据

### 第三阶段：核心实体和枚举类

#### 3.1 创建实体类

使用 Lombok 注解简化代码，继承 MyBatis-Plus 的 `BaseEntity`：

**关键实体**:
- `User.java` - 用户实体
- `DataSource.java` - 数据源实体
- `Job.java` - 作业实体
- `JobVersion.java` - 作业版本实体
- `FieldMetadata.java` - 字段元数据实体

**公共字段**（通过MyBatis-Plus自动填充）:
- createTime, updateTime, createBy, updateBy

#### 3.2 创建枚举类

- `JobType` - 作业类型（DB2HIVE, HIVE2HIVE）
- `EngineType` - 执行引擎（HIVE, SPARK）
- `JobStatus` - 作业状态（DRAFT, PUBLISHED, ARCHIVED）
- `DataSourceType` - 数据源类型（MYSQL, HIVE, ORACLE等）

### 第四阶段：安全认证模块

#### 4.1 Spring Security 配置

**SecurityConfig.java**:
- 配置 JWT 过滤器链
- 放行登录、Swagger等公开接口
- 配置密码加密器（BCryptPasswordEncoder）
- 配置跨域CORS

#### 4.2 JWT 工具类

**JwtUtil.java**:
- `generateToken(String username)` - 生成Token
- `parseToken(String token)` - 解析Token
- `validateToken(String token)` - 验证Token有效性
- Token过期时间：24小时

#### 4.3 JWT 认证过滤器

**JwtAuthenticationFilter.java**:
- 拦截请求提取Token
- 验证Token并设置Security上下文
- 处理Token过期和无效情况

#### 4.4 认证服务和API

**AuthController.java**:
- `POST /api/auth/login` - 用户登录，返回JWT Token
- `POST /api/auth/logout` - 用户登出（客户端删除Token）
- `GET /api/auth/info` - 获取当前登录用户信息

### 第五阶段：数据源管理模块

#### 5.1 数据源服务

**IDataSourceService.java** / **DataSourceServiceImpl.java**:
- 数据源CRUD操作
- 数据源连接测试（实际连接JDBC验证）
- 密码加密存储（AES加密）

#### 5.2 数据源API

**DataSourceController.java**:
- `POST /api/datasources` - 创建数据源
- `PUT /api/datasources/{id}` - 更新数据源
- `DELETE /api/datasources/{id}` - 删除数据源（检查是否被作业引用）
- `GET /api/datasources` - 获取数据源列表
- `GET /api/datasources/{id}` - 获取数据源详情
- `POST /api/datasources/{id}/test` - 测试连接

### 第六阶段：作业管理核心模块

#### 6.1 作业服务

**IJobService.java** / **JobServiceImpl.java**:
- `createJob(JobCreateRequest)` - 创建作业
  - 校验job_code唯一性
  - 保存作业基本信息
  - 自动创建版本1（草稿状态）
  
- `updateJob(Long id, JobUpdateRequest)` - 更新作业
  - 只允许更新未发布或草稿状态的作业基本信息
  
- `deleteJob(Long id)` - 删除作业
  - 级联删除所有版本和字段元数据
  
- `getJobDetail(Long id)` - 获取作业详情
  - 包含当前版本和已发布版本信息
  
- `pageQueryJobs(JobQueryRequest)` - 分页查询作业
  - 支持按作业名称、作业类型、状态、创建人筛选
  - 支持模糊搜索

#### 6.2 作业API

**JobController.java**:
- `POST /api/jobs` - 创建作业
- `PUT /api/jobs/{id}` - 更新作业
- `DELETE /api/jobs/{id}` - 删除作业
- `GET /api/jobs/{id}` - 获取作业详情
- `GET /api/jobs` - 分页查询（支持多条件筛选）

**请求参数示例**:
```java
JobCreateRequest {
    String jobCode;           // 作业英文名
    String jobName;           // 作业中文名
    JobType jobType;          // DB2HIVE/HIVE2HIVE
    Long sourceDatasourceId;  // 源数据源ID（DB2HIVE时必填）
    String sourceDatabase;    // 源库名
    String sourceTable;       // 源表名
    String targetDatabase;    // 目标库名
    String targetTable;       // 目标表名
    EngineType engineType;    // HIVE/SPARK
    String description;       // 描述
}
```

### 第七阶段：版本管理模块

#### 7.1 版本服务

**IJobVersionService.java** / **JobVersionServiceImpl.java**:
- `createVersion(Long jobId)` - 创建新版本
  - 基于当前最新版本复制
  - 版本号自增
  
- `updateVersion(Long jobId, Integer version, VersionUpdateRequest)` - 更新版本
  - 更新SQL内容和Spark配置
  - 只允许更新未发布的版本
  
- `publishVersion(Long jobId, Integer version, String changeLog)` - 发布版本
  - 校验SQL和字段元数据完整性
  - 更新is_published标志
  - 更新作业的published_version字段
  - 更新作业状态为PUBLISHED
  
- `rollbackVersion(Long jobId, Integer targetVersion)` - 回滚版本
  - 将指定历史版本设置为当前发布版本
  
- `getVersionList(Long jobId)` - 获取版本列表
  - 按版本号倒序排列

- `getVersionDetail(Long jobId, Integer version)` - 获取版本详情
  - 包含SQL、Spark配置、字段元数据

#### 7.2 版本API

**JobVersionController.java**:
- `POST /api/jobs/{jobId}/versions` - 创建新版本
- `PUT /api/jobs/{jobId}/versions/{version}` - 更新版本
- `GET /api/jobs/{jobId}/versions` - 获取版本列表
- `GET /api/jobs/{jobId}/versions/{version}` - 获取版本详情
- `POST /api/jobs/{jobId}/versions/{version}/publish` - 发布版本
- `POST /api/jobs/{jobId}/versions/{version}/rollback` - 回滚到指定版本

### 第八阶段：SQL解析和字段元数据管理

#### 8.1 SQL解析工具

**SqlParserUtil.java**:
- 使用 JSqlParser 库解析SQL
- `parseFieldsFromSql(String sql)` - 提取字段列表
  - 解析 SELECT 子句中的字段
  - 识别字段别名
  - 提取表达式字段
  - 返回字段名列表

#### 8.2 字段元数据服务

**IFieldMetadataService.java** / **FieldMetadataServiceImpl.java**:
- `batchSaveFields(Long versionId, List<FieldMetadataRequest>)` - 批量保存字段
  - 先删除该版本的所有字段
  - 再批量插入新字段
  
- `getFieldsByVersion(Long versionId)` - 获取版本的字段列表
  - 按field_order排序

- `parseSqlFields(String sql)` - 解析SQL获取字段
  - 调用SqlParserUtil解析
  - 返回字段名列表供前端使用

#### 8.3 字段元数据API

**FieldMetadataController.java**:
- `POST /api/jobs/{jobId}/versions/{version}/parse-sql` - 解析SQL
  - 请求体：SQL字符串
  - 返回：字段名列表
  
- `POST /api/jobs/{jobId}/versions/{version}/fields` - 批量保存字段元数据
  - 请求体：字段列表（包含字段名、类型、注释等）
  
- `GET /api/jobs/{jobId}/versions/{version}/fields` - 获取字段列表

**字段元数据请求示例**:
```java
FieldMetadataRequest {
    String fieldName;       // 字段名
    String fieldType;       // 字段类型（如：STRING, INT, BIGINT等）
    String fieldComment;    // 字段注释
    Boolean isPartition;    // 是否分区字段
    Boolean isPrimary;      // 是否主键
    Boolean isNullable;     // 是否可为空
    String defaultValue;    // 默认值
    Integer fieldOrder;     // 字段顺序
}
```

### 第九阶段：公共组件和异常处理

#### 9.1 统一响应封装

**Result.java**:
```java
Result<T> {
    Integer code;      // 状态码
    String message;    // 消息
    T data;           // 数据
    Long timestamp;   // 时间戳
}
```

**PageResult.java**:
```java
PageResult<T> {
    List<T> records;   // 数据列表
    Long total;        // 总记录数
    Long current;      // 当前页
    Long size;         // 每页大小
}
```

#### 9.2 全局异常处理

**GlobalExceptionHandler.java**:
- 处理业务异常（BizException）
- 处理参数校验异常（MethodArgumentNotValidException）
- 处理SQL异常
- 处理认证授权异常
- 统一返回Result格式

**BizException.java**:
- 自定义业务异常类
- 包含错误码和错误信息

#### 9.3 参数校验

使用 Jakarta Validation (javax.validation):
- DTO类添加校验注解（@NotNull, @NotBlank, @Size等）
- Controller方法参数添加@Valid注解
- 校验失败自动返回400错误

### 第十阶段：MyBatis-Plus配置优化

#### 10.1 配置类

**MyBatisPlusConfig.java**:
- 配置分页插件（PaginationInnerInterceptor）
- 配置乐观锁插件（如需要）
- 配置逻辑删除（全局配置）

#### 10.2 自动填充

**MetaObjectHandler**:
- 自动填充createTime、updateTime
- 自动填充createBy、updateBy（从Security上下文获取当前用户）

#### 10.3 Mapper基类

所有Mapper继承 `BaseMapper<T>`，获得CRUD基础方法

### 第十一阶段：Swagger API文档配置

#### 11.1 配置类

**SwaggerConfig.java**:
- 配置OpenAPI 3.0文档信息
- 配置JWT认证（在请求头添加Authorization）
- 配置分组（用户认证、作业管理、版本管理等）

#### 11.2 注解使用

在Controller和DTO类上添加：
- `@Tag` - API分组
- `@Operation` - 接口说明
- `@Schema` - 字段说明

访问地址：`http://localhost:8080/swagger-ui.html`

### 第十二阶段：测试和验证

#### 12.1 单元测试

为核心服务编写单元测试：
- UserService测试
- JobService测试
- JobVersionService测试
- SqlParserUtil测试

使用 JUnit 5 + Mockito

#### 12.2 集成测试

- 测试完整的作业创建流程
- 测试版本发布流程
- 测试SQL解析功能
- 测试权限控制

#### 12.3 API测试

使用Postman或Swagger测试所有API端点：
1. 用户登录获取Token
2. 创建数据源
3. 创建作业
4. 编写SQL并解析字段
5. 填写字段元数据
6. 发布版本
7. 查询作业列表（各种筛选条件）

## 核心文件清单

### 配置文件
- `pom.xml` - Maven依赖配置
- `application.yml` - 应用配置
- `schema.sql` - 数据库建表脚本

### 实体类（entity包）
- `User.java`
- `DataSource.java`
- `Job.java`
- `JobVersion.java`
- `FieldMetadata.java`

### 枚举类（enums包）
- `JobType.java`
- `EngineType.java`
- `JobStatus.java`
- `DataSourceType.java`

### Mapper接口（mapper包）
- `UserMapper.java`
- `DataSourceMapper.java`
- `JobMapper.java`
- `JobVersionMapper.java`
- `FieldMetadataMapper.java`

### 服务接口和实现（service包）
- `IUserService.java` / `UserServiceImpl.java`
- `IDataSourceService.java` / `DataSourceServiceImpl.java`
- `IJobService.java` / `JobServiceImpl.java`
- `IJobVersionService.java` / `JobVersionServiceImpl.java`
- `IFieldMetadataService.java` / `FieldMetadataServiceImpl.java`

### 控制器（controller包）
- `AuthController.java`
- `DataSourceController.java`
- `JobController.java`
- `JobVersionController.java`
- `FieldMetadataController.java`

### 安全组件（security包）
- `SecurityConfig.java`
- `JwtUtil.java`
- `JwtAuthenticationFilter.java`
- `UserDetailsServiceImpl.java`

### 工具类（util包）
- `SqlParserUtil.java`
- `EncryptUtil.java`

### 公共类（common包）
- `Result.java`
- `PageResult.java`
- `Constants.java`

### 异常处理（exception包）
- `GlobalExceptionHandler.java`
- `BizException.java`

## 验证计划

### 功能验证
1. **用户认证**：登录成功返回Token，Token验证正确
2. **数据源管理**：创建、测试连接、查询数据源
3. **作业创建**：填写完整信息创建作业，自动生成版本1
4. **SQL编写**：编写SQL，解析字段成功
5. **字段配置**：批量保存字段元数据
6. **版本发布**：发布版本，作业状态变更为PUBLISHED
7. **版本管理**：创建新版本、版本列表查询、版本回滚
8. **作业查询**：分页查询、按类型筛选、按状态筛选、按名称搜索

### 安全验证
1. 未登录访问API返回401
2. 密码加密存储验证
3. 数据源密码加密验证
4. Token过期处理验证

### 性能验证
1. 作业列表分页性能（模拟1000+条数据）
2. 版本查询性能
3. 字段元数据批量保存性能

## 扩展建议

完成核心功能后，可以考虑以下扩展：

1. **调度功能**：集成XXL-JOB实现作业定时调度
2. **执行监控**：记录作业执行历史和日志
3. **数据质量**：添加数据质量校验规则
4. **血缘分析**：分析表和字段的血缘关系
5. **依赖管理**：作业间的依赖关系配置
6. **审计日志**：记录所有操作的审计日志
7. **角色权限**：更细粒度的RBAC权限控制
8. **通知告警**：作业执行失败通知
