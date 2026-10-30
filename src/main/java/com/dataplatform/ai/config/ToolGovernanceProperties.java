package com.dataplatform.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具治理配置属性
 *
 * <p>从 application.yml 的 tool.governance 节点加载配置，包括：
 * - 权限规则
 * - 二次确认规则
 * - 超时配置
 * - 重试配置
 *
 * <p>示例配置：
 * <pre>
 * tool:
 *   governance:
 *     permission:
 *       deleteJob: [ADMIN]
 *       publishJob: [ADMIN, OPERATOR]
 *     confirmation:
 *       enabled: true
 *       tools: [deleteJob, publishJob]
 *     timeout:
 *       default: 30000
 *       per-tool:
 *         executeSQL: 60000
 *     retry:
 *       enabled: true
 *       max-attempts: 1
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "tool.governance")
@Data
public class ToolGovernanceProperties {

    /**
     * 权限规则：工具名 -> 允许的角色列表
     */
    private Map<String, List<String>> permission = new HashMap<>();

    /**
     * 二次确认配置
     */
    private ConfirmationConfig confirmation = new ConfirmationConfig();

    /**
     * 超时配置
     */
    private TimeoutConfig timeout = new TimeoutConfig();

    /**
     * 重试配置
     */
    private RetryConfig retry = new RetryConfig();

    /**
     * 二次确认配置
     */
    @Data
    public static class ConfirmationConfig {
        /**
         * 是否启用二次确认
         */
        private boolean enabled = true;

        /**
         * 需要二次确认的工具列表
         */
        private List<String> tools = new ArrayList<>();
    }

    /**
     * 超时配置
     */
    @Data
    public static class TimeoutConfig {
        /**
         * 默认超时时间（毫秒）
         */
        private long defaultMs = 30000;

        /**
         * 写操作专用超时时间（毫秒），如 create/update/delete
         */
        private long writeOperationMs = 60000;

        /**
         * 单个工具的超时配置：工具名 -> 超时时间
         */
        private Map<String, Long> perTool = new HashMap<>();

        /**
         * 获取指定工具的超时时间
         * @deprecated Use timeout logic in TimeoutRetryInterceptor
         */
        @Deprecated
        public long getTimeoutForTool(String toolName) {
            return perTool.getOrDefault(toolName, defaultMs);
        }
    }

    /**
     * 重试配置
     */
    @Data
    public static class RetryConfig {
        /**
         * 是否启用重试
         */
        private boolean enabled = true;

        /**
         * 最大重试次数
         */
        private int maxAttempts = 1;

        /**
         * 是否对写操作进行重试（默认 false，写操作不重试）
         */
        private boolean writeOperations = false;
    }
}
