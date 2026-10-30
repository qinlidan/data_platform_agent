package com.dataplatform.ai.example;

import com.dataplatform.ai.context.ToolExecutionContext;
import com.dataplatform.ai.exception.NeedConfirmationException;
import com.dataplatform.ai.exception.ToolParameterValidationException;
import com.dataplatform.ai.exception.ToolPermissionDeniedException;
import com.dataplatform.ai.executor.ToolExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 工具治理示例
 * <p>
 * 演示如何使用 ToolExecutor 和拦截器系统执行工具调用。
 * </p>
 *
 * <h3>示例场景</h3>
 * <ol>
 *   <li>普通工具调用 - 正常流程</li>
 *   <li>需要确认的工具 - 二次确认流程</li>
 *   <li>权限不足 - 拒绝访问</li>
 *   <li>参数校验失败 - 返回错误</li>
 * </ol>
 *
 * @author Claude
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolGovernanceExample {

    private final ToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    /**
     * 场景1：普通工具调用
     */
    public void example1_NormalCall() {
        try {
            ToolExecutionContext context = ToolExecutionContext.builder()
                    .toolName("queryJob")
                    .toolInput("{\"jobId\": 123}")
                    .userId("user001")
                    .conversationId("conv001")
                    .roles(Set.of("USER"))
                    .toolDefinition(Map.of(
                            "name", "queryJob",
                            "description", "Query job details",
                            "inputSchema", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "jobId", Map.of("type", "integer")
                                    ),
                                    "required", new String[]{"jobId"}
                            )
                    ))
                    .build();

            String result = toolExecutor.execute(context, ctx -> {
                // 实际工具调用逻辑
                log.info("Executing queryJob with input: {}", ctx.getToolInput());
                return "{\"jobId\": 123, \"status\": \"RUNNING\"}";
            });

            log.info("Result: {}", result);

        } catch (Exception e) {
            log.error("Error:", e);
        }
    }

    /**
     * 场景2：需要确认的工具（首次调用）
     */
    public void example2_NeedConfirmation() {
        try {
            ToolExecutionContext context = ToolExecutionContext.builder()
                    .toolName("deleteJob")
                    .toolInput("{\"jobId\": 123}")
                    .userId("user001")
                    .conversationId("conv001")
                    .roles(Set.of("ADMIN"))
                    .toolDefinition(Map.of("name", "deleteJob"))
                    .build();

            toolExecutor.execute(context, ctx -> {
                log.info("Deleting job: {}", ctx.getToolInput());
                return "{\"success\": true}";
            });

        } catch (NeedConfirmationException e) {
            log.info("Confirmation required!");
            log.info("Message: {}", e.getMessage());
            log.info("Token: {}", e.getConfirmationToken());

            // 前端应该保存此 token，并在用户确认后重新调用
        } catch (Exception e) {
            log.error("Error:", e);
        }
    }

    /**
     * 场景3：提供确认 token（二次调用）
     */
    public void example3_WithConfirmation(String confirmationToken) {
        try {
            ToolExecutionContext context = ToolExecutionContext.builder()
                    .toolName("deleteJob")
                    .toolInput("{\"jobId\": 123}")
                    .userId("user001")
                    .conversationId("conv001")
                    .roles(Set.of("ADMIN"))
                    .confirmationToken(confirmationToken) // 提供确认 token
                    .toolDefinition(Map.of("name", "deleteJob"))
                    .build();

            String result = toolExecutor.execute(context, ctx -> {
                log.info("Deleting job: {}", ctx.getToolInput());
                return "{\"success\": true}";
            });

            log.info("Job deleted: {}", result);

        } catch (Exception e) {
            log.error("Error:", e);
        }
    }

    /**
     * 场景4：权限不足
     */
    public void example4_PermissionDenied() {
        try {
            ToolExecutionContext context = ToolExecutionContext.builder()
                    .toolName("deleteJob")
                    .toolInput("{\"jobId\": 123}")
                    .userId("user001")
                    .conversationId("conv001")
                    .roles(Set.of("USER")) // 普通用户，无删除权限
                    .toolDefinition(Map.of("name", "deleteJob"))
                    .build();

            toolExecutor.execute(context, ctx -> {
                return "{\"success\": true}";
            });

        } catch (ToolPermissionDeniedException e) {
            log.error("Permission denied!");
            log.error("Tool: {}", e.getToolName());
            log.error("User roles: {}", e.getUserRoles());
            log.error("Required roles: {}", e.getRequiredRoles());
        } catch (Exception e) {
            log.error("Error:", e);
        }
    }

    /**
     * 场景5：参数校验失败
     */
    public void example5_ValidationFailed() {
        try {
            ToolExecutionContext context = ToolExecutionContext.builder()
                    .toolName("createJob")
                    .toolInput("{\"jobName\": \"\"}") // 空名称，不符合规则
                    .userId("user001")
                    .conversationId("conv001")
                    .roles(Set.of("USER"))
                    .toolDefinition(Map.of(
                            "name", "createJob",
                            "inputSchema", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "jobName", Map.of(
                                                    "type", "string",
                                                    "minLength", 1
                                            )
                                    ),
                                    "required", new String[]{"jobName"}
                            )
                    ))
                    .build();

            toolExecutor.execute(context, ctx -> {
                return "{\"success\": true}";
            });

        } catch (ToolParameterValidationException e) {
            log.error("Validation failed!");
            log.error("Tool: {}", e.getToolName());
            log.error("Errors: {}", e.getErrors());
        } catch (Exception e) {
            log.error("Error:", e);
        }
    }
}
