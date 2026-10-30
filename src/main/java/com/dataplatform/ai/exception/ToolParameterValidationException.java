package com.dataplatform.ai.exception;

import lombok.Getter;

import java.util.List;

/**
 * 工具参数校验异常
 *
 * @author Claude
 * @since 1.0
 */
@Getter
public class ToolParameterValidationException extends ToolGovernanceException {

    private final String toolName;
    private final List<ValidationError> errors;

    public ToolParameterValidationException(String toolName, List<ValidationError> errors) {
        super(String.format("Tool '%s' parameter validation failed: %s", toolName, errors));
        this.toolName = toolName;
        this.errors = errors;
    }

    @Getter
    public static class ValidationError {
        private final String path;
        private final String message;

        public ValidationError(String path, String message) {
            this.path = path;
            this.message = message;
        }

        @Override
        public String toString() {
            return path + ": " + message;
        }
    }
}
