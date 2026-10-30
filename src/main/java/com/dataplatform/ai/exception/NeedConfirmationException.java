package com.dataplatform.ai.exception;

import lombok.Getter;

/**
 * 需要确认异常
 * <p>
 * 当工具调用需要用户二次确认时抛出。
 * 包含确认 token，前端应保存并在确认后重新提交。
 * </p>
 *
 * @author Claude
 * @since 1.0
 */
@Getter
public class NeedConfirmationException extends ToolGovernanceException {

    private final String toolName;
    private final String confirmationToken;

    public NeedConfirmationException(String toolName, String message, String confirmationToken) {
        super(message);
        this.toolName = toolName;
        this.confirmationToken = confirmationToken;
    }
}
