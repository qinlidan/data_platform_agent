package com.dataplatform.ai.exception;

/**
 * 工具治理异常基类
 * <p>
 * 所有工具治理相关的异常都继承此类。
 * </p>
 *
 * @author Claude
 * @since 1.0
 */
public class ToolGovernanceException extends Exception {

    public ToolGovernanceException(String message) {
        super(message);
    }

    public ToolGovernanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
