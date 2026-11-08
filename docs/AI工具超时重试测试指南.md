# AI 助手工具调用超时重试测试指南

## 测试场景

测试 AI 助手在调用工具（如作业查询）时遇到网络超时，系统是否能自动重试并最终成功。

## 测试方法

### 方法1：使用测试模拟拦截器（推荐）

#### 1. 启用测试模拟

编辑 `src/main/resources/application.yml`，修改以下配置：

```yaml
tool:
  test:
    simulation:
      enabled: true  # 改为 true 启用模拟
      mode: network-error  # 模拟模式：timeout | network-error | delay
      target-tools: searchJobs,listRecentJobs  # 要测试的工具
      fail-attempts: 2  # 前2次失败，第3次成功
      delay-ms: 5000
```

#### 2. 重启应用

```bash
mvn spring-boot:run
```

#### 3. 测试步骤

1. 打开浏览器访问 http://localhost:3000
2. 在右侧 AI 助手中输入：**"查询所有作业"** 或 **"搜索用户相关的作业"**
3. 观察日志输出

#### 4. 预期结果

**后端日志应该显示：**

```
🧪 [TEST] 模拟测试 - 工具: searchJobs, 模式: network-error, 第 1 次调用
🧪 [TEST] 模拟网络错误 - 工具: searchJobs, 第 1 次调用
Tool searchJobs execution failed with retryable error (attempt 1/3): Simulated network timeout for testing

🧪 [TEST] 模拟测试 - 工具: searchJobs, 模式: network-error, 第 2 次调用
🧪 [TEST] 模拟网络错误 - 工具: searchJobs, 第 2 次调用
Tool searchJobs execution failed with retryable error (attempt 2/3): Simulated network timeout for testing

🧪 [TEST] 模拟测试 - 工具: searchJobs, 模式: network-error, 第 3 次调用
🧪 [TEST] 模拟测试结束，正常执行工具: searchJobs
✅ 工具执行成功
```

**前端应该显示：**
- 工具调用徽章：🔧 搜索作业（状态从 warning 变为 success）
- 最终显示作业列表（说明重试成功）

#### 5. 测试完成后

将配置改回：

```yaml
tool:
  test:
    simulation:
      enabled: false  # 恢复为 false
```

---

## 模拟模式说明

### 1. network-error（推荐）

模拟网络超时异常 `SocketTimeoutException`。

- **特点**：立即抛出异常，不占用实际时间
- **适用**：快速测试重试逻辑
- **日志**：`🧪 [TEST] 模拟网络错误`

### 2. timeout

模拟真实超时场景，让线程睡眠60秒。

- **特点**：真实等待超时，每次重试耗时较长
- **适用**：测试超时控制
- **日志**：`🧪 [TEST] 模拟超时`
- **注意**：第一次调用会等待60秒才超时，测试时间较长

### 3. delay

模拟延迟后超时。

- **特点**：睡眠指定时间后抛出异常
- **适用**：测试可控延迟
- **配置**：通过 `delay-ms` 设置延迟时间

---

## 配置参数说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `enabled` | 是否启用测试模拟 | `false` |
| `mode` | 模拟模式 | `network-error` |
| `target-tools` | 目标工具列表（逗号分隔），为空则对所有工具生效 | `searchJobs,listRecentJobs` |
| `fail-attempts` | 失败次数，第 N+1 次成功 | `2` |
| `delay-ms` | delay 模式下的延迟时间（毫秒） | `5000` |

---

## 验证重试功能的关键点

### 1. 日志验证

搜索关键字：
- `🧪 [TEST]` - 测试拦截器日志
- `Tool.*execution failed with retryable error` - 重试日志
- `attempt X/Y` - 尝试次数

### 2. 前端验证

- 工具徽章状态变化（warning → success）
- 最终显示正确结果（说明重试成功）

### 3. 时间验证

- network-error 模式：整个过程应该在几秒内完成（每次重试等待1秒）
- timeout 模式：会等待较长时间

---

## 测试案例

### 案例1：查询作业（读操作）

**配置：**
```yaml
target-tools: searchJobs,listRecentJobs
fail-attempts: 2
```

**测试语句：**
- "查询所有作业"
- "搜索包含 user 的作业"
- "显示最近的5个作业"

**预期：** 前2次失败，第3次成功，最终显示作业列表

### 案例2：创建作业（写操作）

**配置：**
```yaml
target-tools: createJob
fail-attempts: 2
```

**说明：** 写操作默认 `write-operations: false`，不会重试，第1次失败后直接报错

**验证：** 只调用1次，失败后不重试

---

## 方法2：手动模拟网络故障

如果想测试真实网络故障场景：

### Windows

1. **断开网络连接**
   - 禁用网卡
   - 或者使用防火墙阻止 Java 进程的网络访问

2. **测试**
   - 在 AI 助手中输入查询
   - 观察是否自动重试

3. **恢复网络**
   - 重新启用网卡
   - 观察第3次重试是否成功

---

## 方法3：修改超时时间

临时降低超时时间，更容易触发超时：

```yaml
tool:
  governance:
    timeout:
      per-tool:
        searchJobs: 100  # 改为100ms，很容易超时
```

**优点：** 容易复现  
**缺点：** 需要重启应用

---

## 故障排查

### 问题1：看不到测试日志

**原因：** 日志级别太高  
**解决：** 确认 `logging.level.com.dataplatform: debug`

### 问题2：测试模式不生效

**原因：** 配置未生效  
**解决：** 
1. 检查 YAML 缩进是否正确
2. 重启应用
3. 确认 `enabled: true`

### 问题3：工具没有被模拟

**原因：** 工具名称不匹配  
**解决：** 
- 查看日志中实际的工具名称
- 更新 `target-tools` 配置
- 或设置为空字符串对所有工具生效

---

## 生产环境注意事项

⚠️ **务必确保生产环境中 `tool.test.simulation.enabled: false`**

建议使用 Profile 分离配置：

**application-test.yml:**
```yaml
tool:
  test:
    simulation:
      enabled: true
```

**application-prod.yml:**
```yaml
tool:
  test:
    simulation:
      enabled: false
```

启动时指定：
```bash
# 测试环境
mvn spring-boot:run -Dspring.profiles.active=test

# 生产环境
mvn spring-boot:run -Dspring.profiles.active=prod
```
