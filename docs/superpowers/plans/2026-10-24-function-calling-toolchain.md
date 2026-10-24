# Function Calling 工具链与对话持久化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 AI 助手添加 FAQ 查询能力、对话持久化、二次确认机制和查询重试。

**Architecture:** Spring AI 的 `@Tool` 注解注册工具；AOP 拦截器处理二次确认；Flux 钩子异步落库对话记录；Spring Retry 处理查询超时重试。

**Tech Stack:** Spring Boot 3 + Spring AI 1.0.3 + MyBatis-Plus 3.5.5 + MySQL 8.0 + Flyway + JWT (jjwt 0.12.x)

**Spec:** `docs/superpowers/specs/2026-10-24-function-calling-toolchain-design.md`

## Global Constraints

- 不破坏现有 `/api/ai/chat/stream` 接口签名（GET + `@RequestParam String message`）
- SSE 事件格式保持兼容（`text`、`tool_start`、`tool_end`、`table_start`、`table_row`、`done`、`error`）
- user_id 硬编码为 `"qld_001"`（技术债务：ch03 改为从 JWT 解析）
- JWT token 5 分钟有效期，HS256 算法
- 查询工具最多重试 2 次（总共 3 次尝试），阶梯退避 1s、2s
- 写操作（删除、发布）不重试

## Review Focus

1. **AI 绕过二次确认**：如果 AI 在工具参数里传 `confirmed=true`，后端必须拒绝（token 不存在或签名不匹配）
2. **Token 重放攻击**：同一个 confirmation token 使用两次，第二次必须被拒绝（`used_confirmation_tokens` 表已有记录）
3. **对话落库失败不影响响应**：数据库宕机时，SSE 流式响应仍能正常返回给前端（落库异步 + 异常捕获）
4. **工具调用记录完整性**：`messages` 表的 tool 消息的 `tool_call_id` 必须能对上 assistant 消息的 `tool_calls[].id`
5. **查询超时后最终失败**：重试 3 次后仍失败，返回友好错误提示，不能让用户看到原始异常堆栈

---

## File Structure

### 新建文件
- `src/main/resources/db/migration/V1.2__create_conversation_tables.sql` - 数据库迁移脚本
- `src/main/java/com/dataplatform/entity/Conversation.java` - 会话实体
- `src/main/java/com/dataplatform/entity/Message.java` - 消息实体
- `src/main/java/com/dataplatform/entity/UsedConfirmationToken.java` - 已用令牌实体
- `src/main/java/com/dataplatform/mapper/ConversationMapper.java` - 会话 Mapper
- `src/main/java/com/dataplatform/mapper/MessageMapper.java` - 消息 Mapper
- `src/main/java/com/dataplatform/mapper/UsedConfirmationTokenMapper.java` - 令牌 Mapper
- `src/main/java/com/dataplatform/service/ConversationService.java` - 会话服务
- `src/main/java/com/dataplatform/service/MessageService.java` - 消息服务
- `src/main/java/com/dataplatform/ai/tools/FaqTool.java` - FAQ 查询工具
- `src/main/java/com/dataplatform/ai/confirmation/RequireConfirmation.java` - 二次确认注解
- `src/main/java/com/dataplatform/ai/confirmation/ConfirmationContext.java` - ThreadLocal 上下文
- `src/main/java/com/dataplatform/ai/confirmation/ConfirmationInterceptor.java` - AOP 拦截器
- `src/main/java/com/dataplatform/ai/confirmation/ConfirmationService.java` - Token 生成/验证服务
- `src/main/java/com/dataplatform/ai/confirmation/NeedConfirmationException.java` - 需要确认异常
- `src/main/java/com/dataplatform/ai/retry/RetryableQuery.java` - 查询重试注解
- `src/main/java/com/dataplatform/config/RetryConfig.java` - Retry 配置
- `src/main/resources/db/seed/V1.2.1__insert_faq_data.sql` - FAQ 测试数据

### 修改文件
- `src/main/java/com/dataplatform/controller/AiAssistantController.java:106-187` - chatStream 方法，添加对话落库 + 确认异常处理
- `src/main/java/com/dataplatform/ai/tools/JobTool.java` - deleteJob 和 publishJob 添加 @RequireConfirmation
- `src/main/resources/application.yml` - 添加 confirmation.jwt.secret 配置
- `pom.xml` - 添加 jjwt 依赖（如果未安装）

---

### Task 1: 数据库表创建与种子数据

**Files:**
- Create: `src/main/resources/db/migration/V1.2__create_conversation_tables.sql`
- Create: `src/main/resources/db/seed/V1.2.1__insert_faq_data.sql`

**Interfaces:**
- Consumes: 无（基础设施）
- Produces: 4 张表（conversations, messages, faq, used_confirmation_tokens）可用

- [ ] **Step 1: 编写 Flyway 迁移脚本创建 4 张表**

在 `src/main/resources/db/migration/V1.2__create_conversation_tables.sql` 中：

```sql
-- conversations 表（设计文档已给出结构）
CREATE TABLE conversations (...);

-- messages 表
CREATE TABLE messages (...);

-- faq 表
CREATE TABLE faq (...);

-- used_confirmation_tokens 表（新增，设计文档"已确定的设计决策"第 3 点）
CREATE TABLE used_confirmation_tokens (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  jti VARCHAR(64) NOT NULL UNIQUE COMMENT 'Token 唯一标识',
  tool_name VARCHAR(64) NOT NULL COMMENT '绑定的工具名',
  tool_params JSON NOT NULL COMMENT '绑定的参数',
  used_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '使用时间',
  expires_at DATETIME NOT NULL COMMENT '过期时间',
  PRIMARY KEY (id),
  KEY idx_jti (jti),
  KEY idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已用确认令牌';
```

使用设计文档提供的完整 DDL。

- [ ] **Step 2: 编写 FAQ 种子数据**

在 `src/main/resources/db/seed/V1.2.1__insert_faq_data.sql` 中：

```sql
INSERT INTO faq (question, answer, category) VALUES
('Spark 任务 OOM 内存溢出---map端OOM怎么办？', 'map 端 OOM：增加 spark.executor.memory 和 spark.executor.memoryOverhead；reduce 端 OOM：增加 spark.sql.shuffle.partitions 减少单分区数据量', '作业报错'),
('DB2 源库读取很慢', '检查：1. 是否有索引；2. 是否全表扫描；3. 网络带宽是否足够；4. 调整 fetchSize 参数；5. 使用增量读取而非全量', '作业执行变慢'),
('作业一直不执行', '检查：1. 调度状态是否启用；2. Cron 表达式是否正确；3. 依赖的上游作业是否完成；4. 资源队列是否有空闲资源', '作业未执行'),
('Hive 表分区过多导致元数据查询慢', '方案：1. 合并小分区；2. 使用动态分区裁剪；3. 升级 Hive MetaStore 版本；4. 调整 hive.exec.max.dynamic.partitions', '作业执行变慢'),
('作业日志提示权限不足', '检查：1. 数据源用户权限（GRANT SELECT）；2. HDFS 目录权限（chmod/chown）；3. Kerberos 认证是否过期', '作业报错');
```

- [ ] **Step 3: 运行 Flyway 迁移**

```bash
cd D:/alert4
mvn flyway:migrate
```

期望输出：`Successfully applied 1 migration` 且包含 `V1.2__create_conversation_tables`

- [ ] **Step 4: 验证表结构**

```bash
mysql -u root -p -D dataplatform -e "SHOW TABLES LIKE 'conversations'; SHOW TABLES LIKE 'faq'; SHOW TABLES LIKE 'used_confirmation_tokens';"
```

期望输出：3 张表都存在

- [ ] **Step 5: 验证 FAQ 数据**

```bash
mysql -u root -p -D dataplatform -e "SELECT COUNT(*) FROM faq;"
```

期望输出：`5`

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V1.2__create_conversation_tables.sql src/main/resources/db/seed/V1.2.1__insert_faq_data.sql
git commit -m "feat(db): 添加 conversations/messages/faq/used_confirmation_tokens 表及种子数据

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

