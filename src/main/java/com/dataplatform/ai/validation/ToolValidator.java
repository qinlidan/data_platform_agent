package com.dataplatform.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具调用校验器
 * <p>
 * 职责：
 * 1. 校验工具名称是否存在
 * 2. 校验参数类型和必需性
 * 3. 尝试智能参数转换
 * 4. 生成错误反馈供大模型重试
 *
 * @author Data Platform Team
 */
@Slf4j
@Component
public class ToolValidator {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ToolSchema> toolSchemas = new HashMap<>();

    /**
     * 注册工具的 schema（参数定义）
     *
     * @param toolName 工具名称
     * @param schema   参数 schema
     */
    public void registerTool(String toolName, ToolSchema schema) {
        toolSchemas.put(toolName, schema);
        log.info("[ToolValidator] Registered tool: {}, params: {}", toolName, schema.getParameters().keySet());
    }

    /**
     * 校验工具调用
     *
     * @param toolName  工具名称
     * @param arguments 参数（JSON 字符串）
     * @return 校验结果
     */
    public ValidationResult validate(String toolName, String arguments) {
        log.debug("[ToolValidator] Validating tool call: {}, arguments: {}", toolName, arguments);

        // 1. 校验工具是否存在
        if (!toolSchemas.containsKey(toolName)) {
            String errorMsg = String.format(
                    "工具 '%s' 不存在。可用工具: %s",
                    toolName,
                    String.join(", ", toolSchemas.keySet())
            );
            log.warn("[ToolValidator] Tool not found: {}", toolName);
            return ValidationResult.error(errorMsg);
        }

        ToolSchema schema = toolSchemas.get(toolName);

        // 2. 解析参数 JSON
        Map<String, Object> params;
        try {
            JsonNode jsonNode = objectMapper.readTree(arguments);
            params = objectMapper.convertValue(jsonNode, Map.class);
        } catch (Exception e) {
            String errorMsg = String.format(
                    "参数 JSON 解析失败: %s。请检查 JSON 格式。",
                    e.getMessage()
            );
            log.error("[ToolValidator] JSON parse error for tool {}: {}", toolName, e.getMessage());
            return ValidationResult.error(errorMsg);
        }

        // 3. 校验必需参数
        List<String> missingParams = new ArrayList<>();
        for (Map.Entry<String, ParamDef> entry : schema.getParameters().entrySet()) {
            String paramName = entry.getKey();
            ParamDef paramDef = entry.getValue();

            if (paramDef.isRequired() && !params.containsKey(paramName)) {
                missingParams.add(String.format("%s (%s)", paramName, paramDef.getDescription()));
            }
        }

        if (!missingParams.isEmpty()) {
            String errorMsg = String.format(
                    "工具 '%s' 缺少必需参数: %s",
                    toolName,
                    String.join(", ", missingParams)
            );
            log.warn("[ToolValidator] Missing required params for tool {}: {}", toolName, missingParams);
            return ValidationResult.error(errorMsg);
        }

        // 4. 校验和转换参数类型
        Map<String, Object> convertedParams = new HashMap<>();
        List<String> typeErrors = new ArrayList<>();

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String paramName = entry.getKey();
            Object paramValue = entry.getValue();

            ParamDef paramDef = schema.getParameters().get(paramName);
            if (paramDef == null) {
                // 允许额外参数（宽松模式）
                convertedParams.put(paramName, paramValue);
                continue;
            }

            // 尝试类型转换
            try {
                Object converted = convertParam(paramValue, paramDef.getType(), paramName);
                convertedParams.put(paramName, converted);
            } catch (Exception e) {
                typeErrors.add(String.format(
                        "%s: 期望类型 %s，实际值 '%s' (类型: %s)",
                        paramName,
                        paramDef.getType(),
                        paramValue,
                        paramValue.getClass().getSimpleName()
                ));
                log.warn("[ToolValidator] Type conversion failed for param {}: {}", paramName, e.getMessage());
            }
        }

        if (!typeErrors.isEmpty()) {
            String errorMsg = String.format(
                    "工具 '%s' 参数类型错误: %s",
                    toolName,
                    String.join("; ", typeErrors)
            );
            return ValidationResult.error(errorMsg);
        }

        // 5. 校验通过
        log.info("[ToolValidator] Validation passed for tool: {}", toolName);
        return ValidationResult.success(convertedParams);
    }

    /**
     * 智能参数转换
     * 支持：String -> Integer, Long, Boolean, Double 等
     */
    private Object convertParam(Object value, String targetType, String paramName) throws Exception {
        if (value == null) {
            return null;
        }

        // 已经是目标类型
        String actualType = value.getClass().getSimpleName();
        if (actualType.equalsIgnoreCase(targetType)) {
            return value;
        }

        // 智能转换
        switch (targetType.toLowerCase()) {
            case "integer":
            case "int":
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                if (value instanceof String) {
                    String str = (String) value;
                    // 尝试去除空格和前导零
                    str = str.trim();
                    return Integer.parseInt(str);
                }
                throw new IllegalArgumentException("Cannot convert to Integer: " + value);

            case "long":
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
                if (value instanceof String) {
                    return Long.parseLong(((String) value).trim());
                }
                throw new IllegalArgumentException("Cannot convert to Long: " + value);

            case "double":
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                if (value instanceof String) {
                    return Double.parseDouble(((String) value).trim());
                }
                throw new IllegalArgumentException("Cannot convert to Double: " + value);

            case "boolean":
                if (value instanceof Boolean) {
                    return value;
                }
                if (value instanceof String) {
                    String str = ((String) value).trim().toLowerCase();
                    if ("true".equals(str) || "yes".equals(str) || "1".equals(str)) {
                        return true;
                    }
                    if ("false".equals(str) || "no".equals(str) || "0".equals(str)) {
                        return false;
                    }
                }
                throw new IllegalArgumentException("Cannot convert to Boolean: " + value);

            case "string":
                return value.toString();

            default:
                // 未知类型，保持原样
                log.warn("[ToolValidator] Unknown target type '{}' for param '{}', keeping original value",
                        targetType, paramName);
                return value;
        }
    }

    /**
     * 获取所有已注册工具名称
     */
    public Set<String> getRegisteredTools() {
        return toolSchemas.keySet();
    }

    /**
     * 工具 Schema 定义
     */
    public static class ToolSchema {
        private final Map<String, ParamDef> parameters = new LinkedHashMap<>();

        public ToolSchema addParam(String name, String type, String description, boolean required) {
            parameters.put(name, new ParamDef(type, description, required));
            return this;
        }

        public Map<String, ParamDef> getParameters() {
            return parameters;
        }
    }

    /**
     * 参数定义
     */
    public static class ParamDef {
        private final String type;
        private final String description;
        private final boolean required;

        public ParamDef(String type, String description, boolean required) {
            this.type = type;
            this.description = description;
            this.required = required;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public boolean isRequired() {
            return required;
        }
    }

    /**
     * 校验结果
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final Map<String, Object> convertedParams;

        private ValidationResult(boolean valid, String errorMessage, Map<String, Object> convertedParams) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.convertedParams = convertedParams;
        }

        public static ValidationResult success(Map<String, Object> convertedParams) {
            return new ValidationResult(true, null, convertedParams);
        }

        public static ValidationResult error(String errorMessage) {
            return new ValidationResult(false, errorMessage, null);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Map<String, Object> getConvertedParams() {
            return convertedParams;
        }

        /**
         * 生成回灌给大模型的错误提示
         */
        public String toFeedbackMessage() {
            return String.format(
                    "工具调用失败: %s\n请根据错误信息修正参数后重试。",
                    errorMessage
            );
        }
    }
}
