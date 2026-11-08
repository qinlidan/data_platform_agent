package com.dataplatform.ai.interceptor;

import com.dataplatform.ai.context.ToolExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试模拟拦截器
 * <p>
 * 用于测试超时重试等功能，可以模拟：
 * - 超时异常
 * - 网络异常
 * - 延迟响应
 * </p>
 *
 * <h3>配置示例</h3>
 * <pre>
 * tool:
 *   test:
 *     simulation:
 *       enabled: true
 *       mode: timeout  # timeout | delay | network-error
 *       target-tools: searchJobs,listRecentJobs
 *       fail-attempts: 2  # 前2次失败，第3次成功
 *       delay-ms: 5000
 * </pre>
 */
@Slf4j
@Component
@Order(1000)  // 最内层，在所有拦截器之后执行（包括超时重试拦截器）
public class TestSimulationInterceptor implements ToolInterceptor {

    @Value("${tool.test.simulation.enabled:false}")
    private boolean enabled;

    @Value("${tool.test.simulation.mode:timeout}")
    private String mode;

    @Value("${tool.test.simulation.target-tools:}")
    private String targetTools;

    @Value("${tool.test.simulation.fail-attempts:2}")
    private int failAttempts;

    @Value("${tool.test.simulation.delay-ms:5000}")
    private long delayMs;

    // 记录每个工具的调用次数
    private final java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> attemptCounters =
        new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public String intercept(ToolExecutionContext context, InterceptorChain chain) throws Exception {
        if (!enabled) {
            return chain.proceed(context);
        }

        String toolName = context.getToolName();

        // 检查是否为目标工具
        if (!isTargetTool(toolName)) {
            return chain.proceed(context);
        }

        // 获取当前工具的调用次数
        AtomicInteger counter = attemptCounters.computeIfAbsent(toolName, k -> new AtomicInteger(0));
        int currentAttempt = counter.incrementAndGet();

        log.warn("🧪 [TEST] 模拟测试 - 工具: {}, 模式: {}, 第 {} 次调用", toolName, mode, currentAttempt);

        // 判断是否需要模拟失败
        if (currentAttempt <= failAttempts) {
            return simulateFailure(toolName, currentAttempt);
        }

        // 前N次失败后，重置计数器并正常执行
        counter.set(0);
        log.info("🧪 [TEST] 模拟测试结束，正常执行工具: {}", toolName);
        return chain.proceed(context);
    }

    /**
     * 模拟失败场景
     */
    private String simulateFailure(String toolName, int attempt) throws Exception {
        switch (mode.toLowerCase()) {
            case "timeout":
                log.error("🧪 [TEST] 模拟超时 - 工具: {}, 第 {} 次调用", toolName, attempt);
                // 模拟超时：让线程睡眠很长时间
                Thread.sleep(60000); // 睡眠60秒，超过默认超时时间
                return "timeout";

            case "network-error":
                log.error("🧪 [TEST] 模拟网络错误 - 工具: {}, 第 {} 次调用", toolName, attempt);
                throw new SocketTimeoutException("Simulated network timeout for testing");

            case "delay":
                log.warn("🧪 [TEST] 模拟延迟 {}ms - 工具: {}, 第 {} 次调用", delayMs, toolName, attempt);
                Thread.sleep(delayMs);
                throw new SocketTimeoutException("Simulated delay timeout");

            default:
                throw new RuntimeException("Unknown simulation mode: " + mode);
        }
    }

    /**
     * 检查是否为目标工具
     */
    private boolean isTargetTool(String toolName) {
        if (targetTools == null || targetTools.trim().isEmpty()) {
            return true; // 没有配置目标工具，则对所有工具生效
        }

        Set<String> targets = new HashSet<>();
        for (String tool : targetTools.split(",")) {
            targets.add(tool.trim());
        }
        return targets.contains(toolName);
    }
}
