# 用户ID类型统一化修改

## 修改日期
2026-10-29

## 修改目标
将作业相关的创建人ID字段从 `Long` 类型统一改为 `String` 类型，以便与 JWT 认证体系保持一致。

## 修改内容

### 1. 实体类修改
**文件**: `src/main/java/com/dataplatform/entity/Job.java`
- 将字段 `createById` (Long) 改为 `creatorId` (String)
- 更新字段注释为"创建人ID（用户ID）"

### 2. 服务接口修改
**文件**: `src/main/java/com/dataplatform/service/JobService.java`
- `update()` 方法的 `operatorUserId` 参数类型从 `Long` 改为 `String`
- `delete()` 方法的 `operatorUserId` 参数类型从 `Long` 改为 `String`

### 3. 服务实现修改
**文件**: `src/main/java/com/dataplatform/service/impl/JobServiceImpl.java`
- 更新所有权限检查逻辑，使用 String 类型比较
- 修改 `create()` 方法中设置创建人ID的逻辑
- 更新 `update()`, `delete()`, `publish()`, `archive()` 方法的权限验证

### 4. 控制器修改
**文件**: `src/main/java/com/dataplatform/controller/JobController.java`
- `update()` 方法使用 `SecurityUtils.getCurrentUserId()` 获取 String 类型用户ID
- `delete()`, `publish()`, `archive()` 方法同样改用 String 类型

### 5. 数据库迁移脚本
**文件**: `src/main/resources/db/migration/V1.4__change_creator_id_type.sql`
- 将 `etl_job` 表的 `create_by_id` 字段类型从 `BIGINT` 改为 `VARCHAR(50)`
- 同时将字段重命名为 `creator_id`
- 重建相关索引

## 影响范围
- 作业创建、更新、删除、发布、归档等所有需要权限验证的操作
- 不影响其他模块（数据源、用户等）

## 测试验证
- 编译通过：`mvn clean compile`
- 所有单元测试通过：`mvn test` (29 tests passed)

## 后续需要注意
1. 执行数据库迁移脚本前，需要备份现有数据
2. 如果数据库中已有数据，需要确保现有的 `create_by_id` 值可以安全转换为字符串
3. 其他使用 `createById` 字段的代码需要同步更新

## 兼容性
- 向后不兼容：需要执行数据库迁移脚本
- API兼容性：Controller接口签名未改变（仍从SecurityContext获取userId）
