package com.dataplatform.ai.interceptor;

import com.dataplatform.ai.config.ToolGovernanceProperties;
import com.dataplatform.ai.context.ToolExecutionContext;
import com.dataplatform.ai.exception.ToolExecutionTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.*;

/**
 * 超时重试拦截器
 * <p>
 * 为工具调用提供超时控制和自动重试能力。
 * 支持全局默认超时、单个工具超时配置、写操作特殊超时。
 * </p>
 *
 * <h3>配置示例</h3>
 * <pre>
 * tool:
 *   governance:
 *     timeout:
 *       default-ms: 30000
 *       write-operation-ms: 60000
 *       per-tool:
 *         publishJob: 60000
 *     retry:
 *       enabled: true
 *       max-attempts: 2
 *       write-operations: false
 * </pre>
 *
 * <h3>超时优先级</h3>
 * <ol>
 *   <li>per-tool 配置（最高优先级）</li>
 *   <li>write-operation-ms（写操作）</li>
 *   <li>default-ms（默认值）</li>
 * </ol>
 *
 * <h3>重试策略</h3>
 * <ul>
 *   <li>仅对网络异常重试（SocketTimeoutException、ConnectException）</li>
 *   <li>写操作默认不重试（避免副作用）</li>
 *   <li>每次重试等待 1 秒（固定延迟）</li>
 * </ul>
 *
 * @author Claude
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeoutRetryInterceptor implements ToolInterceptor {

    private final ToolGovernanceProperties properties;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public String intercept(ToolExecutionContext context, InterceptorChain chain) throws Exception {
        String toolName = context.getToolName();

        // 确定超时时间
        long timeoutMs = getTimeoutForTool(toolName, context);

        // 确定最大重试次数
        int maxAttempts = getMaxAttempts(context);

        // 执行工具调用（带超时和重试）
        return executeWithTimeoutAndRetry(context, chain, timeoutMs, maxAttempts);
    }

    /**
     * 执行工具调用，带超时和重试
     */
    private String executeWithTimeoutAndRetry(ToolExecutionContext context,
                                               InterceptorChain chain,
                                               long timeoutMs,
                                               int maxAttempts) throws Exception {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                // 使用 CompletableFuture 实现超时控制
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return chain.proceed(context);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, executor);

                return future.get(timeoutMs, TimeUnit.MILLISECONDS);

            } catch (TimeoutException e) {
                log.warn("Tool {} execution timed out after {}ms (attempt {}/{})",
                        context.getToolName(), timeoutMs, attempt, maxAttempts);
                lastException = new ToolExecutionTimeoutException(
                        context.getToolName(),
                        timeoutMs,
                        attempt == maxAttempts ? "Maximum retry attempts exceeded" :
                                "Retrying with longer timeout"
                );

                if (attempt < maxAttempts) {
                    Thread.sleep(1000); // 重试前等待 1 秒
                }

            } catch (ExecutionException e) {
                Throwable cause = e.getCause();

                // 检查是否为可重试异常
                if (isRetryableException(cause) && attempt < maxAttempts) {
                    log.warn("Tool {} execution failed with retryable error (attempt {}/{}): {}",
                            context.getToolName(), attempt, maxAttempts, cause.getMessage());
                    lastException = (Exception) cause;
                    Thread.sleep(1000); // 重试前等待 1 秒
                    continue;
                }

                // 不可重试异常，直接抛出
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                throw new RuntimeException(cause);
            }
        }

        // 所有尝试都失败
        log.error("Tool {} execution failed after {} attempts", context.getToolName(), maxAttempts);
        throw lastException;
    }

    /**
     * 获取工具的超时时间
     */
    private long getTimeoutForTool(String toolName, ToolExecutionContext context) {
        ToolGovernanceProperties.TimeoutConfig config = properties.getTimeout();

        // 1. 检查 per-tool 配置
        if (config.getPerTool() != null && config.getPerTool().containsKey(toolName)) {
            return config.getPerTool().get(toolName);
        }

        // 2. 检查是否为写操作
        if (isWriteOperation(toolName) && config.getWriteOperationMs() > 0) {
            return config.getWriteOperationMs();
        }

        // 3. 使用默认值
        return config.getDefaultMs();
    }

    /**
     * 判断是否为写操作
     */
    private boolean isWriteOperation(String toolName) {
        String lowerName = toolName.toLowerCase();
        return lowerName.contains("create") ||
               lowerName.contains("update") ||
               lowerName.contains("delete") ||
               lowerName.contains("publish") ||
               lowerName.contains("execute");
    }

    /**
     * 获取最大尝试次数
     */
    private int getMaxAttempts(ToolExecutionContext context) {
        ToolGovernanceProperties.RetryConfig config = properties.getRetry();

        if (!config.isEnabled()) {
            return 1; // 不重试
        }

        // 写操作默认不重试
        if (isWriteOperation(context.getToolName()) && !config.isWriteOperations()) {
            return 1;
        }

        return config.getMaxAttempts();
    }

    /**
     * 判断异常是否可重试
     */
    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof SocketTimeoutException) {
            return true;
        }
        if (throwable instanceof ConnectException) {
            return true;
        }
        // 可以根据需要添加更多可重试异常类型
        return false;
    }
}
