# 功能修复验证清单

## 1. 版本号显示加 "v" 前缀

**位置**: 作业列表页面（http://localhost:3000/）

**验证步骤**:
1. 打开作业列表页面
2. 查看"当前版本"和"发布版本"列
3. 确认显示为 "v1", "v2" 等格式（不是纯数字 "1", "2"）

**修改文件**: `frontend/src/views/JobList.vue`
- 第139行: `{{ 'v' + row.currentVersion }}`  
- 第144行: `{{ 'v' + row.publishedVersion }}`

---

## 2. "作业英文名"改为"作业名称"

**位置**: 新建/编辑作业对话框

**验证步骤**:
1. 点击"新建作业"或编辑某个作业
2. 查看第一步"参数配置"中的表单标签
3. 确认标签显示为"作业名称"（不是"作业英文名"）

**修改文件**: `frontend/src/views/JobCreateDialog.vue`
- 第88行: `<el-form-item label="作业名称"`

---

## 3. 数据库字段更新

**验证步骤**:
```sql
-- 检查etl_job表新增字段
DESCRIBE etl_job;
-- 应包含: create_by_id, update_by_id, last_publish_by, last_publish_by_id, last_publish_time, online_version

-- 检查etl_job_version表新增字段
DESCRIBE etl_job_version;
-- 应包含: status, job_name, job_cn_name, source_*, target_*, 
--         schedule_frequency, description, change_summary,
--         submit_by, submit_time, publish_by, publish_time,
--         is_rollback, rollback_from_version, update_time, deleted
```

---

## 4. 批量保存字段元数据API修复

**API**: `POST /api/field-metadata/batch`

**请求格式**:
```json
{
  "jobVersionId": 1,
  "fields": [
    {
      "fieldName": "id",
      "fieldComment": "字段注释",
      "fieldType": "string",
      "isPrimaryKey": false,
      "isPartitionKey": false
    }
  ]
}
```

**验证步骤**:
```bash
curl -X POST http://localhost:8089/api/field-metadata/batch \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{
    "jobVersionId": 1,
    "fields": [
      {
        "fieldName": "test_field",
        "fieldComment": "测试字段",
        "fieldType": "string",
        "isPrimaryKey": false,
        "isPartitionKey": false
      }
    ]
  }'
```

**期望响应**:
```json
{"code":200,"message":"success","data":null}
```

**修改文件**: 
- `src/main/java/com/dataplatform/dto/FieldMetadataRequest.java`
  - FieldInfo内部类的字段名从 `dataType, comment` 改为 `fieldType, fieldComment` 以匹配前端

---

## 5. 前端功能测试

### 5.1 创建作业并保存字段
1. 打开 http://localhost:3000/
2. 点击"新建作业"
3. 填写作业信息（作业名称、作业类型等）
4. 第二步配置SQL
5. 第三步添加字段元数据，确保字段名、注释、类型都填写完整
6. 点击"创建作业"
7. 确认创建成功且字段已保存

### 5.2 验证版本号显示
1. 返回作业列表
2. 查看新创建的作业
3. 确认"当前版本"显示为 "v1"
4. 确认"发布版本"显示为 "v0"（未发布）

---

## 技术细节

### 字段映射关系

| 前端字段 | 后端DTO字段 | 数据库字段 | 说明 |
|---------|------------|-----------|------|
| fieldName | fieldName | field_name | 字段名 |
| fieldComment | fieldComment | field_comment | 字段注释 |
| fieldType | fieldType | field_type | 字段类型 |
| isPrimaryKey | isPrimaryKey | is_primary_key | 是否主键 |
| isPartitionKey | isPartitionKey | is_partition_key | 是否分区键 |

### API错误排查

如果遇到 500 错误：
1. 检查请求Content-Type是否包含 `charset=UTF-8`
2. 检查字段名是否匹配（fieldType vs dataType）
3. 查看后端日志：`tail -f backend.log`
4. 确认jobVersionId对应的版本存在

---

## 已知问题

无

---

## 修改总结

1. ✅ 前端显示: 版本号加 "v" 前缀
2. ✅ 前端标签: "作业英文名" → "作业名称"  
3. ✅ 数据库结构: 添加多个新字段支持版本管理和操作人追踪
4. ✅ 后端DTO: 字段名统一为 fieldType/fieldComment 匹配前端
5. ✅ API修复: 批量保存字段元数据正常工作
