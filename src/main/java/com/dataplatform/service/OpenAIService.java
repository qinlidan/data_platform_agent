//package com.dataplatform.service;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.Builder;
//import lombok.Data;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.MediaType;
//import org.springframework.stereotype.Service;
//import org.springframework.web.reactive.function.client.WebClient;
//import reactor.core.publisher.Mono;
//
//import java.util.*;
//import java.util.stream.Collectors;
//
///**
// * OpenAI API 调用封装
// *
// * 支持：
// * 1. 普通对话
// * 2. Function Calling（工具调用）
// * 3. 流式输出
// */
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class OpenAIService {
//
//    @Value("${openai.api.key:}")
//    private String apiKey;
//
//    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
//    private String apiUrl;
//
//    @Value("${openai.model:gpt-4}")
//    private String model;
//
//    private final ObjectMapper objectMapper;
//
//    /**
//     * 调用 OpenAI API（支持 Function Calling）
//     *
//     * @param messages 消息历史
//     * @param tools    工具定义列表
//     * @return 响应对象
//     */
//    public Mono<ChatResponse> chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
//        WebClient client = WebClient.builder()
//                .baseUrl(apiUrl)
//                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
//                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
//                .build();
//
//        Map<String, Object> requestBody = new HashMap<>();
//        requestBody.put("model", model);
//        requestBody.put("messages", messages);
//
//        if (tools != null && !tools.isEmpty()) {
//            List<Map<String, Object>> toolsJson = tools.stream()
//                    .map(this::convertToolDefinition)
//                    .collect(Collectors.toList());
//            requestBody.put("tools", toolsJson);
//            requestBody.put("tool_choice", "auto");
//        }
//
//        log.info("[OpenAI] 发送请求：messages={}, tools={}", messages.size(), tools != null ? tools.size() : 0);
//
//        return client.post()
//                .bodyValue(requestBody)
//                .retrieve()
//                .bodyToMono(String.class)
//                .map(this::parseResponse)
//                .doOnError(e -> log.error("[OpenAI] 调用失败", e));
//    }
//
//    /**
//     * 转换工具定义为 OpenAI Function 格式
//     */
//    private Map<String, Object> convertToolDefinition(ToolDefinition tool) {
//        Map<String, Object> function = new HashMap<>();
//        function.put("name", tool.getName());
//        function.put("description", tool.getDescription());
//
//        Map<String, Object> parameters = new HashMap<>();
//        parameters.put("type", "object");
//        parameters.put("properties", tool.getParameters());
//        parameters.put("required", tool.getRequired());
//
//        function.put("parameters", parameters);
//
//        return Map.of("type", "function", "function", function);
//    }
//
//    /**
//     * 解析 OpenAI 响应
//     */
//    private ChatResponse parseResponse(String json) {
//        try {
//            JsonNode root = objectMapper.readTree(json);
//            JsonNode choice = root.path("choices").get(0);
//            JsonNode message = choice.path("message");
//
//            String role = message.path("role").asText();
//            String content = message.path("content").asText(null);
//
//            List<ToolCall> toolCalls = new ArrayList<>();
//            if (message.has("tool_calls")) {
//                for (JsonNode toolCallNode : message.path("tool_calls")) {
//                    String id = toolCallNode.path("id").asText();
//                    String name = toolCallNode.path("function").path("name").asText();
//                    String arguments = toolCallNode.path("function").path("arguments").asText();
//
//                    Map<String, Object> params = objectMapper.readValue(arguments, Map.class);
//                    toolCalls.add(new ToolCall(id, name, params));
//                }
//            }
//
//            return ChatResponse.builder()
//                    .role(role)
//                    .content(content)
//                    .toolCalls(toolCalls)
//                    .build();
//
//        } catch (Exception e) {
//            log.error("[OpenAI] 解析响应失败", e);
//            throw new RuntimeException("解析 OpenAI 响应失败", e);
//        }
//    }
//
//    // ========== 数据结构 ==========
//
//    @Data
//    @Builder
//    public static class ChatMessage {
//        private String role; // user, assistant, tool
//        private String content;
//        private String name; // 工具名称（role=tool 时）
//        private String toolCallId; // 工具调用ID（role=tool 时）
//        private List<Map<String, Object>> toolCalls; // 工具调用列表（role=assistant 时）
//    }
//
//    @Data
//    @Builder
//    public static class ChatResponse {
//        private String role;
//        private String content;
//        private List<ToolCall> toolCalls;
//    }
//
//    @Data
//    @Builder
//    public static class ToolCall {
//        private String id;
//        private String name;
//        private Map<String, Object> params;
//
//        public ToolCall(String id, String name, Map<String, Object> params) {
//            this.id = id;
//            this.name = name;
//            this.params = params;
//        }
//    }
//
//    @Data
//    @Builder
//    public static class ToolDefinition {
//        private String name;
//        private String description;
//        private Map<String, Object> parameters; // JSON Schema
//        private List<String> required;
//        private boolean requiresApproval; // 是否需要审批（自定义字段）
//    }
//}
