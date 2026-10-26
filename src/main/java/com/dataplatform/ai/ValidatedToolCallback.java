package com.dataplatform.ai;

import com.dataplatform.ai.validation.ToolValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Sinks;

import java.util.Map;

/**
 * 带校验的工具回调包装器 - 在执行前校验参数
 *
 * @author Data Platform Team
 */
@Slf4j
public class ValidatedToolCallback extends ObservableToolCallback {

    private final ToolValidator validator;

    public ValidatedToolCallback(
            ToolCallback delegate,
            Sinks.Many<Map<String, Object>> eventSink,
            ToolValidator validator
    ) {
        super(delegate, eventSink);
        this.validator = validator;
    }

    @Override
    public String call(String functionArguments) {
        // 提取工具名
        String toolName = getToolDefinition().name();

        // 1. 先校验参数
        if (validator != null) {
            ToolValidator.ValidationResult result = validator.validate(toolName, functionArguments);
            if (!result.isValid()) {
                log.warn("[ValidatedToolCallback] 工具 {} 参数校验失败: {}", toolName, result.getErrorMessage());
                throw new IllegalArgumentException(result.getErrorMessage());
            }
        }

        // 2. 调用父类（ObservableToolCallback）执行并发射事件
        return super.call(functionArguments);
    }
}
