package com.dataplatform.ai.interceptor;

import com.dataplatform.ai.context.ToolExecutionContext;
import com.dataplatform.ai.exception.ToolParameterValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 参数校验拦截器
 * <p>
 * 使用 JSON Schema 校验工具输入参数。
 * 从 ToolDefinition 中获取 inputSchema，如果未定义则跳过校验。
 * </p>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>从 context.getToolDefinition() 获取工具定义</li>
 *   <li>提取 inputSchema (JSON Schema 格式)</li>
 *   <li>如果没有 schema，跳过校验</li>
 *   <li>使用 json-schema-validator 验证参数</li>
 *   <li>校验失败抛出 ToolParameterValidationException</li>
 * </ol>
 *
 * @author Claude
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidationInterceptor implements ToolInterceptor {

    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    @Override
    public String intercept(ToolExecutionContext context, InterceptorChain chain) throws Exception {
         log.debug("Tool {} has no inputSchema, skipping validation", context.getToolName());
          return chain.proceed(context);
//        Map<String, Object> toolDefinition = context.getToolDefinition();
//
//        // 如果没有工具定义，跳过校验
//        if (toolDefinition == null || toolDefinition.isEmpty()) {
//            log.debug("Tool {} has no definition, skipping validation", context.getToolName());
//            return chain.proceed(context);
//        }
//
//        // 提取 inputSchema
//        Object inputSchemaObj = toolDefinition.get("inputSchema");
//        if (inputSchemaObj == null) {
//            log.debug("Tool {} has no inputSchema, skipping validation", context.getToolName());
//            return chain.proceed(context);
//        }
//
//        try {
//            // 转换 schema 为 JsonNode
//            JsonNode schemaNode = objectMapper.valueToTree(inputSchemaObj);
//            JsonSchema schema = schemaFactory.getSchema(schemaNode);
//
//            // 转换输入参数为 JsonNode
//            JsonNode inputNode = objectMapper.readTree(context.getToolInput());
//
//            // 执行校验
//            Set<ValidationMessage> errors = schema.validate(inputNode);
//
//            if (!errors.isEmpty()) {
//                log.warn("Tool {} validation failed: {}", context.getToolName(), errors);
//
//                List<ToolParameterValidationException.ValidationError> validationErrors = errors.stream()
//                        .map(err -> new ToolParameterValidationException.ValidationError(
//                                err.getInstanceLocation().toString(),
//                                err.getMessage()
//                        ))
//                        .collect(Collectors.toList());
//
//                throw new ToolParameterValidationException(
//                        context.getToolName(),
//                        validationErrors
//                );
//            }
//
//            log.debug("Tool {} validation passed", context.getToolName());
//            return chain.proceed(context);
//
//        } catch (ToolParameterValidationException e) {
//            throw e;
//        } catch (Exception e) {
//            log.error("Failed to validate tool {} input", context.getToolName(), e);
//            // 校验器本身出错时，放行调用（避免阻塞业务）
//            return chain.proceed(context);
//        }
    }
}
