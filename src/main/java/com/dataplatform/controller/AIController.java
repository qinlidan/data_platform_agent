//package com.dataplatform.controller;
//
//import com.dataplatform.common.Result;
//import com.dataplatform.dto.ChatRequest;
//import com.dataplatform.dto.ChatResponse;
//import com.dataplatform.dto.SqlGenerationRequest;
//import com.dataplatform.service.AIAssistantService;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.HashMap;
//import java.util.Map;
//import java.util.UUID;
//
///**
// * AI 助手控制器
// * 提供 AI 相关的 REST API
// */
//@RestController
//@RequestMapping("/api/ai")
//@RequiredArgsConstructor
//@Slf4j
//@Tag(name = "AI助手", description = "AI智能助手相关接口")
//public class AIController {
//
//    private final AIAssistantService aiAssistantService;
//
//    /**
//     * 通用对话接口
//     */
//    @PostMapping("/chat")
//    @Operation(summary = "AI对话", description = "与AI助手进行通用对话")
//    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
//        log.info("收到对话请求: {}", request.getMessage());
//
//        try {
//            String reply = aiAssistantService.chat(request.getMessage());
//
//            // 生成或使用会话ID
//            String conversationId = request.getConversationId() != null
//                    ? request.getConversationId()
//                    : "conv_" + UUID.randomUUID().toString().substring(0, 8);
//
//            ChatResponse response = ChatResponse.of(reply, conversationId);
//
//            return Result.success(response);
//        } catch (Exception e) {
//            log.error("对话处理失败", e);
//            return Result.error("对话处理失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * SQL 生成接口
//     */
//    @PostMapping("/generate-sql")
//    @Operation(summary = "生成SQL", description = "根据自然语言需求生成SQL语句")
//    public Result<Map<String, String>> generateSql(@RequestBody SqlGenerationRequest request) {
//        log.info("收到 SQL 生成请求: {}", request.getRequirement());
//
//        try {
//            String sql = aiAssistantService.generateSql(request);
//
//            Map<String, String> result = new HashMap<>();
//            result.put("sql", sql);
//            result.put("databaseType", request.getDatabaseType());
//
//            return Result.success(result);
//        } catch (Exception e) {
//            log.error("SQL 生成失败", e);
//            return Result.error("SQL 生成失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 作业配置提取接口
//     */
//    @PostMapping("/extract-job-config")
//    @Operation(summary = "提取作业配置", description = "从自然语言描述中提取作业配置信息")
//    public Result<String> extractJobConfig(@RequestParam String description) {
//        log.info("提取作业配置，描述: {}", description);
//
//        try {
//            String config = aiAssistantService.extractJobConfig(description);
//            return Result.success(config);
//        } catch (Exception e) {
//            log.error("配置提取失败", e);
//            return Result.error("配置提取失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * SQL 优化建议接口
//     */
//    @PostMapping("/optimize-sql")
//    @Operation(summary = "SQL优化建议", description = "分析SQL并给出优化建议")
//    public Result<String> optimizeSql(
//            @RequestParam String sql,
//            @RequestParam(required = false, defaultValue = "MySQL") String dbType) {
//        log.info("分析 SQL 优化");
//
//        try {
//            String optimization = aiAssistantService.optimizeSql(sql, dbType);
//            return Result.success(optimization);
//        } catch (Exception e) {
//            log.error("SQL 优化分析失败", e);
//            return Result.error("优化分析失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 智能问答接口
//     */
//    @PostMapping("/qa")
//    @Operation(summary = "智能问答", description = "回答关于数据平台的问题")
//    public Result<Map<String, String>> qa(
//            @RequestParam String question,
//            @RequestParam(required = false) String context) {
//        log.info("智能问答，问题: {}", question);
//
//        try {
//            String answer = aiAssistantService.answerQuestion(question, context);
//
//            Map<String, String> result = new HashMap<>();
//            result.put("question", question);
//            result.put("answer", answer);
//
//            return Result.success(result);
//        } catch (Exception e) {
//            log.error("问答处理失败", e);
//            return Result.error("问答处理失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 健康检查接口
//     */
//    @GetMapping("/health")
//    @Operation(summary = "AI服务健康检查", description = "检查AI服务是否正常")
//    public Result<Map<String, Object>> health() {
//        Map<String, Object> status = new HashMap<>();
//        status.put("status", "UP");
//        status.put("service", "AI Assistant");
//        status.put("models", Map.of(
//                "deepseek", "deepseek-chat",
//                "alibaba", "qwen-max"
//        ));
//
//        return Result.success(status);
//    }
//}
