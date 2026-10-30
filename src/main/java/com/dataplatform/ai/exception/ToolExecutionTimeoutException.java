package com.dataplatform.ai.exception;

import lombok.Getter;

/**
 * 工具执行超时异常
 *
 * @author Claude
 * @since 1.0
 */
@Getter
public class ToolExecutionTimeoutException extends ToolGovernanceException {

    private final String toolName;
    private final long timeoutMs;

    public ToolExecutionTimeoutException(String toolName, long timeoutMs, String message) {
        super(String.format("Tool '%s' execution timed out after %dms: %s", toolName, timeoutMs, message));
        this.toolName = toolName;
        this.timeoutMs = timeoutMs;
    }
}
