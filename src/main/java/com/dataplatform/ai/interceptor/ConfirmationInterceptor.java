package com.dataplatform.ai.interceptor;

import com.dataplatform.ai.config.ToolGovernanceProperties;
import com.dataplatform.ai.confirmation.ConfirmationService;
import com.dataplatform.ai.context.ToolExecutionContext;
import com.dataplatform.ai.exception.NeedConfirmationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 确认拦截器
 * <p>
 * 对危险操作要求二次确认。
 * 未确认时生成 confirmation token 并抛出 NeedConfirmationException。
 * 已确认时验证 token 并允许执行。
 * </p>
 *
 * <h3>配置示例</h3>
 * <pre>
 * tool:
 *   governance:
 *     confirmation:
 *       enabled: true
 *       tools: [deleteJob, publishJob, deleteDataSource]
 * </pre>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>首次调用：检测到工具需要确认 → 生成 token → 抛出 NeedConfirmationException</li>
 *   <li>二次调用：用户提供 token → 验证 token → 通过后调用实际工具</li>
 * </ol>
 *
 * @author Claude
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfirmationInterceptor implements ToolInterceptor {

    private final ToolGovernanceProperties properties;
    private final ConfirmationService confirmationService;
    private final ObjectMapper objectMapper;

    @Override
    public String intercept(ToolExecutionContext context, InterceptorChain chain) throws Exception {
        String toolName = context.getToolName();

        // 检查是否启用确认功能
        if (!properties.getConfirmation().isEnabled()) {
            return chain.proceed(context);
        }

        // 检查工具是否需要确认
        List<String> requiredTools = properties.getConfirmation().getTools();
        if (requiredTools == null || !requiredTools.contains(toolName)) {
            log.debug("Tool {} does not require confirmation", toolName);
            return chain.proceed(context);
        }

        // 检查是否提供了确认 token
        String confirmationToken = context.getConfirmationToken();

        if (confirmationToken == null || confirmationToken.isEmpty()) {
            // 未提供 token，生成新 token 并要求确认
            log.info("Tool {} requires confirmation, generating token", toolName);
            Map<String, Object> parameters = parseToolInput(context.getToolInput());
            String token = confirmationService.generateToken(toolName, "execute", parameters);
            throw new NeedConfirmationException(
                    toolName,
                    "危险操作需要确认",
                    token
            );
        }

        // 验证提供的 token
        boolean valid = confirmationService.validateToken(confirmationToken, toolName, "execute");
        if (!valid) {
            log.warn("Invalid confirmation token for tool {}", toolName);
            throw new NeedConfirmationException(
                    toolName,
                    "确认令牌无效或已过期，请重新操作",
                    null
            );
        }

        log.info("Confirmation token validated for tool {}", toolName);
        return chain.proceed(context);
    }

    /**
     * 解析工具输入参数为 Map
     */
    private Map<String, Object> parseToolInput(String toolInput) {
        try {
            return objectMapper.readValue(toolInput, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse tool input as JSON: {}", toolInput, e);
            return Map.of("raw", toolInput);
        }
    }
}
