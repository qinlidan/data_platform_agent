# 作业查询功能改进计划

## 任务清单
1. ✅ 搜索框的作业频率支持下拉选择（日/月/周/年），使用枚举类型
2. ✅ 作业列表查询只保留一个接口，参数使用 JobQueryRequest，移除关键词查询
3. ✅ JobQueryRequest 的 jobType、status 等字段使用枚举控制
4. ✅ 去掉作业列表的发布版本字段（前后端+数据库）
5. ✅ 添加 langgraph4j 依赖

## 探索阶段总结

### 现有代码结构
1. **枚举类** (已存在)
   - `ScheduleFrequency`: DAILY, WEEKLY, MONTHLY, YEARLY
   - `JobType`: DB2HIVE, HIVE2HIVE (有 @EnumValue 和 @JsonValue 注解)
   - `JobStatus`: DRAFT, PUBLISHED, ARCHIVED (有 @EnumValue 和 @JsonValue 注解)

2. **后端**
   - `Job` 实体：包含 `publishedVersion` 字段（第69行）
   - `JobQueryRequest`: 目前字段都是 String 类型
   - `JobController`: GET /api/jobs 接口使用 JobQueryRequest

3. **前端** (JobList.vue)
   - 第61-68行：作业频率是输入框（需改为下拉）
   - 第144-148行：显示发布版本列（需删除）
   - searchForm.keyword 字段存在（需删除）

4. **数据库**
   - etl_job 表有 `published_version` 字段（需删除）

## 实施计划

### 第一步：修改后端 - JobQueryRequest 使用枚举
**文件**: `src/main/java/com/dataplatform/dto/JobQueryRequest.java`
- 将 `jobType` 从 String 改为 `JobType` 枚举
- 将 `status` 从 String 改为 `JobStatus` 枚举
- 将 `scheduleFrequency` 从 String 改为 `ScheduleFrequency` 枚举
- **删除 `keyword` 字段**

### 第二步：修改后端 Service 层查询逻辑
**文件**: `src/main/java/com/dataplatform/service/impl/JobServiceImpl.java`
- 更新查询逻辑以适配枚举类型
- 移除 keyword 相关查询逻辑

### 第三步：删除后端 - Job 实体的 publishedVersion 字段
**文件**: `src/main/java/com/dataplatform/entity/Job.java`
- 删除 `publishedVersion` 字段（第69行）
- 删除相关的辅助方法（hasPendingChanges, isNeverPublished, isPublished）
- 删除注释中提到的 publishedVersion 说明

### 第四步：数据库迁移 - 删除 published_version 字段
**操作**: 创建数据库迁移 SQL
- ALTER TABLE etl_job DROP COLUMN published_version;

### 第五步：修改前端 - 作业频率改为下拉选择
**文件**: `frontend/src/views/JobList.vue`
- 第61-68行：将 el-input 改为 el-select
- 添加选项：日(DAILY)、周(WEEKLY)、月(MONTHLY)、年(YEARLY)

### 第六步：修改前端 - 删除发布版本列
**文件**: `frontend/src/views/JobList.vue`
- 删除第144-148行的发布版本列

### 第七步：修改前端 - 删除关键词搜索
**文件**: `frontend/src/views/JobList.vue`
- 删除第8-15行的关键词搜索表单项
- 删除 searchForm.keyword 字段

### 第八步：添加 langgraph4j 依赖
**文件**: `pom.xml`
- 在 dependencies 中添加 langgraph4j

## 注意事项
1. 枚举类型需要确保前后端一致
2. 删除 publishedVersion 字段可能影响现有逻辑，需检查所有引用
3. 数据库字段删除需谨慎，建议先备份
4. 需要检查 JobVersion 相关逻辑是否依赖 publishedVersion

## 风险评估
- **高风险**: 删除 publishedVersion 字段会影响 JobServiceImpl.publish() 方法（第276行）
- **中风险**: 枚举类型改动需要测试所有查询场景
- **低风险**: 前端UI调整

## 关键发现
1. `JobServiceImpl.publish()` 方法第276行使用了 `job.setPublishedVersion(version)`
2. 需要删除该行代码并检查发布逻辑是否还需要调整
3. 变量名 `publishedVersions` 在代码中只是临时变量名，不影响删除字段

## 最终实施方案

### 后端改动
1. **JobQueryRequest.java** - 使用枚举类型
2. **Job.java** - 删除 publishedVersion 字段及相关方法
3. **JobServiceImpl.java** - 删除 publishedVersion 相关代码（第276行）
4. **数据库迁移** - 删除 published_version 列
5. **pom.xml** - 添加 langgraph4j 依赖

### 前端改动
1. **JobList.vue** - 作业频率改为下拉选择
2. **JobList.vue** - 删除发布版本列
3. **JobList.vue** - 删除关键词搜索


