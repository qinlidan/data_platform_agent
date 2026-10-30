# 读操作超时重试实现详解

## 核心实现：TimeoutRetryInterceptor

**位置**: `com.dataplatform.ai.interceptor.TimeoutRetryInterceptor`

## 一、重试次数的配置

### 1. 配置文件（application.yml）

```yaml
tool:
  governance:
    retry:
      enabled: true           # 是否启用重试
      max-attempts: 2         # 最大尝试次数（包括首次调用）
      write-operations: false # 写操作是否重试（默认不重试）
```

**注意**：`max-attempts: 2` 表示：
- 第1次：首次调用
- 第2次：如果失败，重试1次
- 总共最多执行 2 次

### 2. 读操作 vs 写操作的区分

```java
private boolean isWriteOperation(String toolName) {
    String lowerName = toolName.toLowerCase();
    return lowerName.contains("create") ||
           lowerName.contains("update") ||
           lowerName.contains("delete") ||
           lowerName.contains("publish") ||
           lowerName.contains("execute");
}
```

**逻辑**：
- 工具名包含 `create/update/delete/publish/execute` → 写操作
- 其他（如 `query/get/list/search`）→ 读操作

**结果**：
- 读操作（如 `queryData`）：重试 2 次
- 写操作（如 `updateJob`）：默认不重试（避免重复操作）

## 二、重试实现的核心代码

### 1. 获取最大尝试次数

```java
private int getMaxAttempts(ToolExecutionContext context) {
    ToolGovernanceProperties.RetryConfig config = properties.getRetry();
    
    // 检查是否启用重试
    if (!config.isEnabled()) {
        return 1; // 不重试，只执行一次
    }
    
    // 写操作默认不重试
    if (isWriteOperation(context.getToolName()) && !config.isWriteOperations()) {
        return 1;
    }
    
    // 读操作：返回配置的最大尝试次数
    return config.getMaxAttempts(); // 例如：2
}
```

### 2. 执行带超时和重试的调用

```java
private String executeWithTimeoutAndRetry(ToolExecutionContext context,
                                           InterceptorChain chain,
                                           long timeoutMs,
                                           int maxAttempts) throws Exception {
    int attempt = 0;
    Exception lastException = null;
    
    // 循环尝试，最多 maxAttempts 次
    while (attempt < maxAttempts) {
        attempt++;  // 第1次：attempt = 1；第2次：attempt = 2
        
        try {
            // 使用 CompletableFuture 实现超时控制
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return chain.proceed(context);  // 调用下一个拦截器或实际工具
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, executor);
            
            // 等待结果，最多 timeoutMs 毫秒
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            
        } catch (TimeoutException e) {
            // 超时异常
            log.warn("Tool {} execution timed out after {}ms (attempt {}/{})",
                    context.getToolName(), timeoutMs, attempt, maxAttempts);
            lastException = new ToolExecutionTimeoutException(...);
            
            // 如果还有重试机会，等待1秒后继续
            if (attempt < maxAttempts) {
                Thread.sleep(1000); // 固定延迟 1 秒
            }
            
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            
            // 检查是否为可重试异常（网络异常）
            if (isRetryableException(cause) && attempt < maxAttempts) {
                log.warn("Tool {} execution failed with retryable error (attempt {}/{}): {}",
                        context.getToolName(), attempt, maxAttempts, cause.getMessage());
                lastException = (Exception) cause;
                Thread.sleep(1000); // 等待1秒后重试
                continue;
            }
            
            // 不可重试异常，直接抛出
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException(cause);
        }
    }
    
    // 所有尝试都失败，抛出最后一个异常
    log.error("Tool {} execution failed after {} attempts", context.getToolName(), maxAttempts);
    throw lastException;
}
```

### 3. 可重试异常的判断

```java
private boolean isRetryableException(Throwable throwable) {
    if (throwable instanceof SocketTimeoutException) {
        return true;  // 网络超时
    }
    if (throwable instanceof ConnectException) {
        return true;  // 连接失败
    }
    // 可以根据需要添加更多可重试异常类型
    return false;
}
```

## 三、完整执行流程示例

### 场景：查询数据超时

假设配置：
- `max-attempts: 2`
- `default-ms: 30000`（30秒超时）

#### 第一次尝试（attempt = 1）

```
1. 调用 chain.proceed(context) → 执行 SQL 查询
2. 等待最多 30 秒
3. 如果 30 秒内返回 → 成功，返回结果 ✓
4. 如果 30 秒后超时 → 捕获 TimeoutException
   - 记录警告日志："attempt 1/2"
   - 等待 1 秒
   - 继续循环
```

#### 第二次尝试（attempt = 2）

```
1. 再次调用 chain.proceed(context) → 重新执行 SQL 查询
2. 等待最多 30 秒
3. 如果 30 秒内返回 → 成功，返回结果 ✓
4. 如果 30 秒后超时 → 捕获 TimeoutException
   - 记录错误日志："failed after 2 attempts"
   - 跳出循环
   - 抛出 ToolExecutionTimeoutException ✗
```

## 四、关键设计点

### 1. 为什么使用 CompletableFuture？

```java
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    return chain.proceed(context);
}, executor);

return future.get(timeoutMs, TimeUnit.MILLISECONDS);
```

**原因**：
- `CompletableFuture.get(timeout, unit)` 提供了超时控制
- 如果超过 timeout，立即抛出 `TimeoutException`
- 底层任务在线程池中执行，主线程等待结果

### 2. 重试间隔为什么是 1 秒？

```java
Thread.sleep(1000); // 固定延迟 1 秒
```

**原因**：
- **避免立即重试**：给系统一点恢复时间
- **固定延迟策略**：简单可靠，不需要复杂的退避算法
- **适用于临时性故障**：网络抖动、数据库连接池满等

**替代方案**（未实现）：
- **指数退避**：第1次等1秒，第2次等2秒，第3次等4秒...
- **随机抖动**：在固定时间上加随机偏移，避免"惊群效应"

### 3. 为什么写操作不重试？

```java
if (isWriteOperation(context.getToolName()) && !config.isWriteOperations()) {
    return 1; // 不重试
}
```

**原因**：
- **幂等性问题**：重复执行写操作可能导致数据错误
  - 例如：`createJob` 执行两次 → 创建了两个作业
  - 例如：`deleteJob` 超时但实际成功 → 重试会报"作业不存在"

**解决方案**（如果需要重试写操作）：
1. 实现幂等性（如使用唯一ID）
2. 在配置中显式启用：`write-operations: true`

### 4. 只有网络异常才重试

```java
if (isRetryableException(cause) && attempt < maxAttempts) {
    // 重试
}
```

**原因**：
- **网络异常是临时性的**：重试有意义
- **业务异常不应重试**：
  - 参数错误 → 重试也会失败
  - 权限拒绝 → 重试也会失败
  - 数据不存在 → 重试也会失败

## 五、实际例子

### 例子1：queryData（读操作）

```
工具名：queryData
是否写操作：否（不包含 create/update/delete...）
重试配置：max-attempts = 2
```

**执行过程**：
```
[Attempt 1] 开始查询...
[Attempt 1] 超时（30秒）→ TimeoutException
[等待] 暂停 1 秒
[Attempt 2] 开始查询...
[Attempt 2] 成功，返回结果 ✓
```

### 例子2：updateJob（写操作）

```
工具名：updateJob
是否写操作：是（包含 "update"）
重试配置：write-operations = false
```

**执行过程**：
```
[Attempt 1] 开始更新...
[Attempt 1] 超时（60秒）→ TimeoutException
[结束] 不重试，直接抛出异常 ✗
```

### 例子3：网络异常的重试

```
工具名：queryData
异常类型：SocketTimeoutException（网络超时）
重试配置：max-attempts = 2
```

**执行过程**：
```
[Attempt 1] 开始查询...
[Attempt 1] 网络超时 → SocketTimeoutException
[判断] 可重试异常 ✓
[等待] 暂停 1 秒
[Attempt 2] 开始查询...
[Attempt 2] 成功，返回结果 ✓
```

### 例子4：参数错误不重试

```
工具名：queryData
异常类型：ToolParameterValidationException（参数校验失败）
重试配置：max-attempts = 2
```

**执行过程**：
```
[Attempt 1] 开始查询...
[Attempt 1] 参数校验失败 → ToolParameterValidationException
[判断] 不可重试异常 ✗
[结束] 直接抛出异常，不重试
```

## 六、配置建议

### 开发环境

```yaml
tool:
  governance:
    timeout:
      default-ms: 10000        # 10秒（快速失败）
      write-operation-ms: 20000
    retry:
      enabled: true
      max-attempts: 2          # 最多重试1次
      write-operations: false
```

### 生产环境

```yaml
tool:
  governance:
    timeout:
      default-ms: 30000        # 30秒
      write-operation-ms: 60000 # 写操作更长超时
    retry:
      enabled: true
      max-attempts: 3          # 最多重试2次
      write-operations: false  # 写操作仍然不重试
```

### 特殊工具配置

```yaml
tool:
  governance:
    timeout:
      default-ms: 30000
      per-tool:
        complexQuery: 120000   # 复杂查询允许2分钟
        quickCheck: 5000       # 快速检查只给5秒
```

## 七、监控指标

在 `AuditInterceptor` 中记录了详细的执行信息：

```json
{
  "toolName": "queryData",
  "duration": 31245,
  "status": "success",
  "retries": 1,              // 重试了1次
  "userId": "user123",
  "timestamp": "2026-10-29T22:00:00"
}
```

可以基于这些数据分析：
- 重试率：`retries > 0` 的比例
- 超时率：`status = timeout` 的比例
- 平均重试次数：`avg(retries)`

## 总结

**读操作超时重试两次的实现**：

1. **配置**: `max-attempts: 2` 表示最多执行2次
2. **判断**: 工具名不包含写操作关键词 → 读操作
3. **循环**: `while (attempt < 2)` 最多执行2次
4. **超时**: 使用 `CompletableFuture.get(timeout)` 控制超时
5. **重试**: 超时或网络异常 → 等待1秒后重试
6. **终止**: 成功返回 或 达到最大次数后抛出异常
