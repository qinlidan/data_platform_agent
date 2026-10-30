package com.dataplatform.ai.interceptor;

import com.dataplatform.ai.context.ToolExecutionContext;
import com.dataplatform.ai.exception.NeedConfirmationException;
import com.dataplatform.ai.exception.ToolExecutionTimeoutException;
import com.dataplatform.ai.exception.ToolParameterValidationException;
import com.dataplatform.ai.exception.ToolPermissionDeniedException;
import com.dataplatform.entity.ToolAuditLog;
import com.dataplatform.mapper.ToolAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * 审计拦截器
 * <p>
 * 作为最外层拦截器，记录所有工具调用的审计日志。
 * 异步保存审计记录，避免阻塞工具调用。
 * </p>
 *
 * <h3>审计状态</h3>
 * <ul>
 *   <li>SUCCESS - 执行成功</li>
 *   <li>VALIDATION_FAILED - 参数校验失败</li>
 *   <li>PERMISSION_DENIED - 权限拒绝</li>
 *   <li>NEED_CONFIRMATION - 需要二次确认</li>
 *   <li>TIMEOUT - 执行超时</li>
 *   <li>FAILED - 执行失败</li>
 * </ul>
 *
 * @author Claude
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditInterceptor implements ToolInterceptor {

    private final ToolAuditLogMapper auditLogMapper;

    private static final int MAX_TEXT_LENGTH = 10000;

    @Override
    public String intercept(ToolExecutionContext context, InterceptorChain chain) throws Exception {
        long startTime = System.currentTimeMillis();
        String result = null;
        String status = "SUCCESS";
        Exception exception = null;

        try {
            result = chain.proceed(context);
            return result;
        } catch (ToolParameterValidationException e) {
            status = "VALIDATION_FAILED";
            exception = e;
            throw e;
        } catch (ToolPermissionDeniedException e) {
            status = "PERMISSION_DENIED";
            exception = e;
            throw e;
        } catch (NeedConfirmationException e) {
            status = "NEED_CONFIRMATION";
            exception = e;
            throw e;
        } catch (ToolExecutionTimeoutException e) {
            status = "TIMEOUT";
            exception = e;
            throw e;
        } catch (Exception e) {
            status = "FAILED";
            exception = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            saveAuditLogAsync(context, result, status, duration, exception);
        }
    }

    /**
     * 异步保存审计日志
     */
    private void saveAuditLogAsync(ToolExecutionContext context, String result,
                                     String status, long duration, Exception exception) {
        CompletableFuture.runAsync(() -> {
            try {
                ToolAuditLog auditLog = new ToolAuditLog();
                auditLog.setToolName(context.getToolName());
                auditLog.setToolInput(truncate(context.getToolInput()));
                auditLog.setToolResult(truncate(result));
                auditLog.setStatus(status);
                auditLog.setDuration(duration);
                auditLog.setUserId(context.getUserId());

                // 转换 conversationId: String -> Long
                String convId = context.getConversationId();
                if (convId != null && !convId.isEmpty()) {
                    try {
                        auditLog.setConversationId(Long.parseLong(convId));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid conversationId format: {}", convId);
                        auditLog.setConversationId(null);
                    }
                }

                auditLog.setErrorMessage(exception != null ? truncate(exception.getMessage()) : null);
                auditLog.setCreateTime(LocalDateTime.now());

                auditLogMapper.insert(auditLog);

                log.debug("Audit log saved: tool={}, status={}, duration={}ms",
                         context.getToolName(), status, duration);
            } catch (Exception e) {
                log.error("Failed to save audit log for tool: {}", context.getToolName(), e);
            }
        });
    }

    /**
     * 截断过长的文本，防止数据库字段溢出
     */
    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH) + "... (truncated)";
    }
}
