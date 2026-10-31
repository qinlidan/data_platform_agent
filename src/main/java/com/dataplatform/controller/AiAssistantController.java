package com.dataplatform.controller;

import com.dataplatform.ai.ToolMonitorConfig;
import com.dataplatform.ai.confirmation.ConfirmationService;
import com.dataplatform.ai.event.MessageSaveEvent;
import com.dataplatform.ai.executor.ToolExecutor;
import com.dataplatform.ai.resolution.CoreferenceResolutionService;
import com.dataplatform.ai.tools.DataSourceTool;
import com.dataplatform.ai.tools.FaqTool;
import com.dataplatform.ai.tools.JobTool;
import com.dataplatform.common.ApiResponse;
import com.dataplatform.service.DataSourceService;
import com.dataplatform.service.FaqService;
import com.dataplatform.service.JobService;
import com.dataplatform.util.SqlParserUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 助手控制器
 * 基于 Spring AI @Tool 自动调用，支持自然语言创建/修改/发布/删除作业
 */
@Tag(name = "AI助手")
@RestController
@RequestMapping("/api/ai")
@Slf4j
public class AiAssistantController {

    private final ChatModel chatModel;
    private final JobService jobService;
    private final DataSourceService dataSourceService;
    private final FaqService faqService;
    private final ChatMemory chatMemory;
    private final SystemPromptTemplate pureConversationSystemPrompt;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final com.dataplatform.service.ConversationService conversationService;
    private final com.dataplatform.service.MessageService messageService;
    private final ApplicationEventPublisher eventPublisher;
    private final ToolExecutor toolExecutor;
    private final ConfirmationService confirmationService;
    private final CoreferenceResolutionService coreferenceResolutionService;

    public AiAssistantController(
            ChatModel chatModel,
            JobService jobService,
            DataSourceService dataSourceService,
            FaqService faqService,
            ChatMemory chatMemory,
            @Qualifier("pureConversationSystemPrompt") SystemPromptTemplate pureConversationSystemPrompt,
            com.dataplatform.service.ConversationService conversationService,
            com.dataplatform.service.MessageService messageService,
            ApplicationEventPublisher eventPublisher,
            ToolExecutor toolExecutor,
            ConfirmationService confirmationService,
            CoreferenceResolutionService coreferenceResolutionService
    ) {
        this.chatModel = chatModel;
        this.jobService = jobService;
        this.dataSourceService = dataSourceService;
        this.faqService = faqService;
        this.chatMemory = chatMemory;
        this.pureConversationSystemPrompt = pureConversationSystemPrompt;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.eventPublisher = eventPublisher;
        this.toolExecutor = toolExecutor;
        this.confirmationService = confirmationService;
        this.coreferenceResolutionService = coreferenceResolutionService;
    }

    @Operation(summary = "AI对话 - 自然语言创建/管理作业")
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ApiResponse.error("消息不能为空");
        }

        try {
            log.info("收到用户消息：{}", userMessage);

            // 创建会话（使用临时用户ID，实际应从认证上下文获取）
            String userId = request.getOrDefault("userId", "anonymous");
            Long conversationId = conversationService.getOrCreateConversation(userId, null);

            // 异步落表：用户消息
            eventPublisher.publishEvent(new MessageSaveEvent(this, conversationId, "user", userMessage));

            // 构建工具（普通对话不需要 Sink）
            JobTool jobTool = new JobTool(jobService, dataSourceService);
            DataSourceTool dataSourceTool = new DataSourceTool(dataSourceService);

            // 构建带工具调用的 ChatClient（@Tool 自动调用）
            ChatClient client = ChatClient.builder(chatModel)
                    .defaultTools(jobTool, dataSourceTool)
                    .build();

            String response = client.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userMessage)
                    .toolContext(Map.of("userId", userId))  // 传递 userId 到工具
                    .call()
                    .content();

            log.info("AI 响应：{}", response);

            // 异步落表：助手消息
            eventPublisher.publishEvent(new MessageSaveEvent(this, conversationId, "assistant", response));

            Map<String, Object> result = new HashMap<>();
            result.put("message", response);
            result.put("needConfirmation", false);
            result.put("conversationId", conversationId);

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("AI对话失败", e);
            return ApiResponse.error("AI对话失败：" + e.getMessage());
        }
    }

    @Operation(summary = "AI 对话流式输出 - SSE（支持逐行表格渲染 + 工具调用可见）")
    @GetMapping(value = "/conversations/{conversationId}/chat/stream")
    public Flux<ServerSentEvent<Map<String, Object>>> chatStreamByConversation(
            @PathVariable Long conversationId,
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "anonymous") String userId,
            @RequestHeader(value = "X-Confirmation-Token", required = false) String confirmationToken
    ) {
        return chatStream(message, userId, conversationId, confirmationToken);
    }

    @GetMapping(value = "/chat/stream")
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(
        @RequestParam String message,
        @RequestParam(required = false, defaultValue = "anonymous") String userId,
        @RequestParam(required = false) Long conversationId,
        @RequestHeader(value = "X-Confirmation-Token", required = false) String confirmationToken
) {
    if (message == null || message.trim().isEmpty()) {
        return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                .event("error")
                .data(Map.of("message", "消息不能为空"))
                .build());
    }

    try {
        log.info("收到用户消息（流式）：{}, userId: {}, conversationId: {}, hasToken: {}",
                message, userId, conversationId, confirmationToken != null);
        // 获取或创建会话
        if (conversationId == null) {
            // 首次对话，创建新会话
            conversationId = conversationService.getOrCreateConversation(userId, null);
            log.info("创建新会话: conversationId={}", conversationId);
        } else {
            // 验证会话是否存在且属于该用户
            // TODO: 添加会话验证逻辑（可选）
        }

        final Long finalConversationId = conversationId;

        // 指代消解：改写用户消息
        String originalMessage = message;
        String resolvedMessage =message;
//        String resolvedMessage = coreferenceResolutionService.resolveAndRewrite(
//                originalMessage,
//                String.valueOf(finalConversationId)  // 使用纯数字 conversationId，与主对话一致
//        );
//        log.info("指代消解: conversationId={}, 原始='{}', 改写='{}'",
//                finalConversationId, originalMessage, resolvedMessage);

        // 异步落表：用户消息（保存原始消息）
        eventPublisher.publishEvent(new MessageSaveEvent(this, finalConversationId, "user", originalMessage));

        // 为当前请求创建独立的 UI 事件通道
        Sinks.Many<Map<String, Object>> uiEventSink = Sinks.many().unicast().onBackpressureBuffer();

        // 构建工具并注入 Sink
        JobTool jobTool = new JobTool(jobService, dataSourceService);
        jobTool.setUiEventSink(uiEventSink);
        DataSourceTool dataSourceTool = new DataSourceTool(dataSourceService);
        FaqTool faqTool = new FaqTool(faqService);

        // 使用 ToolMonitorConfig 直接包装工具（集成拦截器链）
        ToolCallback[] monitoredTools = ToolMonitorConfig.wrapTools(uiEventSink, toolExecutor, jobTool, dataSourceTool, faqTool);

        // 使用 ChatClient.Builder，传入包装后的工具回调
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolCallbacks(monitoredTools)
                .build();

        // 用于收集助手完整响应内容
        StringBuilder assistantResponse = new StringBuilder();

        // 文本流：模型逐 token 生成，在 prompt() 时绑定 conversationId
        Flux<ServerSentEvent<Map<String, Object>>> textStream = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(resolvedMessage)  // 使用改写后的消息
                .toolContext(buildToolContext(userId, confirmationToken))  // 传递 userId 和 confirmationToken
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, String.valueOf(finalConversationId)))
                .stream()
                .chatResponse()
                .mapNotNull(resp -> {
                    String content = resp.getResult() != null && resp.getResult().getOutput() != null
                            ? resp.getResult().getOutput().getText()
                            : null;
                    if (content == null || content.isEmpty()) {
                        return null;
                    }
                    // 累积助手响应
                    assistantResponse.append(content);
                    return ServerSentEvent.<Map<String, Object>>builder()
                            .event("text")
                            .data(Map.of("content", content))
                            .build();
                })
                .doOnComplete(() -> {
                    // 异步落表：助手完整响应
                    if (assistantResponse.length() > 0) {
                        eventPublisher.publishEvent(new MessageSaveEvent(
                            this, finalConversationId, "assistant", assistantResponse.toString()
                        ));
                        log.debug("[AiAssistantController] Published assistant message event for conversation: {}", finalConversationId);
                    }
                    uiEventSink.tryEmitComplete();
                })
                .doOnError(e -> uiEventSink.tryEmitComplete());

        // UI 事件流：工具方法推送的表格数据 + 工具调用状态
        Flux<ServerSentEvent<Map<String, Object>>> uiStream = uiEventSink.asFlux()
                .map(event -> {
                    String eventName = (String) event.get("event");
                    Object payload = event.get("payload");

                    // 只转发事件到前端，不落库 tool 结果
                    return ServerSentEvent.<Map<String, Object>>builder()
                            .event(eventName)
                            .data((Map<String, Object>) payload)
                            .build();
                });

        // 合并两条流，先发送 conversationId，然后是内容，最后发送 done 事件
        Flux<ServerSentEvent<Map<String, Object>>> conversationIdEvent = Flux.just(
            ServerSentEvent.<Map<String, Object>>builder()
                .event("conversation_id")
                .data(Map.of("conversationId", finalConversationId))
                .build()
        );

        return Flux.concat(conversationIdEvent, Flux.merge(textStream, uiStream))
                .concatWith(Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                        .event("done")
                        .data(Map.of())
                        .build()))
                .onErrorResume(e -> {
                    log.error("AI 对话流式失败", e);
                    // 返回错误事件到前端
                    Map<String, Object> errorData = new HashMap<>();
                    errorData.put("message", e.getMessage() != null ? e.getMessage() : "AI对话失败");
                    errorData.put("type", e.getClass().getSimpleName());

                    return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                            .event("error")
                            .data(errorData)
                            .build());
                });
    } catch (Exception e) {
        log.error("AI 对话流式失败", e);
        Map<String, Object> errorData = new HashMap<>();
        errorData.put("message", e.getMessage() != null ? e.getMessage() : "AI对话失败");
        errorData.put("type", e.getClass().getSimpleName());

        return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                .event("error")
                .data(errorData)
                .build());
    }
}

    @Operation(summary = "SQL语法检查")
    @PostMapping("/check-sql-syntax")
    public ApiResponse<Map<String, Object>> checkSqlSyntax(@RequestBody Map<String, String> request) {
        String sql = request.get("sqlContent");
        Map<String, Object> result = new HashMap<>();

        if (sql == null || sql.trim().isEmpty()) {
            result.put("valid", false);
            result.put("message", "SQL不能为空");
            return ApiResponse.success(result);
        }

        if (SqlParserUtil.containsWildcardSelect(sql)) {
            result.put("valid", false);
            result.put("message", "不允许使用 SELECT * 操作，请明确列出字段");
            return ApiResponse.success(result);
        }

        result.put("valid", true);
        result.put("message", "语法检查通过");
        return ApiResponse.success(result);
    }



    @Operation(summary = "纯对话 - 流式输出（支持多轮上下文）")
    @GetMapping(value = "/chat/pure/stream")
    public Flux<ServerSentEvent<Map<String, Object>>> chatPureStream(
            @RequestParam String message,
            @RequestParam String conversationId
    ) {
        if (message == null || message.trim().isEmpty()) {
            return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                    .event("error")
                    .data(Map.of("message", "消息不能为空"))
                    .build());
        }

        if (conversationId == null || conversationId.trim().isEmpty()) {
            return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                    .event("error")
                    .data(Map.of("message", "conversationId 不能为空"))
                    .build());
        }

        try {
            log.info("[纯对话流式] conversationId={}, message={}", conversationId, message);

            // 异步落表：用户消息
            Long convId = Long.parseLong(conversationId);
            eventPublisher.publishEvent(new MessageSaveEvent(this, convId, "user", message));

            // 构建 ChatClient（不带工具，启用 ChatMemory）
            ChatClient client = ChatClient.builder(chatModel)
                    .defaultSystem(pureConversationSystemPrompt.getTemplate())
                    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .build();

            // 用于收集助手完整响应内容
            StringBuilder assistantResponse = new StringBuilder();

            // 流式调用（逐 token 返回），在 prompt() 时绑定 conversationId
            Flux<String> contentStream = client.prompt()
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .stream()
                    .content();

            // 包装成 SSE 事件
            Flux<ServerSentEvent<Map<String, Object>>> textStream = contentStream
                    .filter(content -> content != null && !content.isEmpty())
                    .doOnNext(content -> assistantResponse.append(content))
                    .map(content -> ServerSentEvent.<Map<String, Object>>builder()
                            .event("text")
                            .data(Map.of("content", content))
                            .build())
                    .doOnComplete(() -> {
                        // 异步落表：助手完整响应
                        if (assistantResponse.length() > 0) {
                            eventPublisher.publishEvent(new MessageSaveEvent(
                                this, convId, "assistant", assistantResponse.toString()
                            ));
                            log.debug("[纯对话流式] Published assistant message event for conversation: {}", convId);
                        }
                    });

            // 最后发送 done 事件
            Flux<ServerSentEvent<Map<String, Object>>> doneEvent = Flux.just(
                    ServerSentEvent.<Map<String, Object>>builder()
                            .event("done")
                            .data(Map.of())
                            .build()
            );

            return textStream.concatWith(doneEvent)
                    .onErrorResume(e -> {
                        log.error("[纯对话流式] 失败", e);
                        Map<String, Object> errorData = new HashMap<>();
                        errorData.put("message", e.getMessage() != null ? e.getMessage() : "对话失败");
                        errorData.put("type", e.getClass().getSimpleName());

                        return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                                .event("error")
                                .data(errorData)
                                .build());
                    });

        } catch (Exception e) {
            log.error("[纯对话流式] 失败", e);
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("message", e.getMessage() != null ? e.getMessage() : "对话失败");
            errorData.put("type", e.getClass().getSimpleName());

            return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                    .event("error")
                    .data(errorData)
                    .build());
        }
    }
    /**
     * 构建工具上下文（包含用户信息和确认令牌）
     */
    private Map<String, Object> buildToolContext(String userId, String confirmationToken) {
        Map<String, Object> context = new HashMap<>();
        context.put("userId", userId);
        if (confirmationToken != null && !confirmationToken.isEmpty()) {
            context.put("confirmationToken", confirmationToken);
        }
        return context;
    }

    private static final String SYSTEM_PROMPT = """
            你是一个数据平台的 AI 助手，专门帮助用户管理 ETL 作业和数据源。

            你可以调用以下工具：

            【作业管理工具】
            1. getJobDetails - 查询作业详情（参数：jobCode）
            2. listRecentJobs - 查询最近的作业列表（参数：limit）
            3. searchJobs - 按关键词/类型/状态搜索作业（参数：keyword、jobType、status）
            4. createJob - 创建作业（参数：jobCode、jobName、jobType、sourceDatasourceCode、sourceDatabase、sourceTable、targetDatabase、targetTable、engineType、description）
               注意：目标表名（targetTable）会自动设置为作业编码（jobCode），无需传值
            5. updateJobConfig - 修改作业配置（参数：jobCode、jobName、sourceDatabase、sourceTable、targetDatabase、targetTable、description）
            6. deleteJob - 删除作业（参数：jobCode、confirmed）
            7. publishJob - 发布作业（参数：jobCode、version、confirmed）

            【数据源管理工具】
            8. getDataSourceDetails - 查询数据源详情（参数：datasourceId）
            9. listAllDataSources - 查询所有数据源列表
            10. searchDataSources - 搜索数据源（参数：keyword、pageNum、pageSize）
            11. createDataSource - 创建数据源（参数：sourceName、sourceCode、sourceType、jdbcUrl、username、password、description）
            12. updateDataSource - 修改数据源配置（参数：datasourceId、sourceName、jdbcUrl、username、password、description）
            13. deleteDataSource - 删除数据源（参数：datasourceId、confirmed）
            14. testDataSourceConnection - 测试数据源连接（参数：datasourceId）

            【FAQ知识库工具】
            15. queryFaq - 查询常见问题知识库（参数：keyword、category）
               用于查询故障诊断、性能优化等常见问题的答案

            重要规则：
            1. 创建作业时，如果用户没有提供作业编码，自动生成一个（格式：job_表名_序号）。
            2. 如果用户没有提供作业名称，根据表名自动生成。
            3. 目标表名（targetTable）会自动设置为作业编码（jobCode），createJob 时传 null 或空字符串即可。
            4. 如果缺少必要参数（如源数据源编码），先询问用户，不要编造。
            5. 删除和发布等危险操作需要二次确认：
               - 系统会自动弹出确认按钮供用户点击
               - 你需要明确告知用户"请点击确认按钮以继续操作"，不要说"回复确认删除"
               - 用户点击确认后，操作将自动执行
            6. 查询类工具（listRecentJobs、searchJobs）会以表格形式流式推送到前端，你只需返回简单的摘要文字。
            7. 查询某个作业的数据源信息时：先调用 getJobDetails 获取数据源ID，再调用 getDataSourceDetails 获取详情，不要调用 searchDataSources 或 listAllDataSources。
            8. searchDataSources 和 listAllDataSources 只用于"搜索数据源"或"列出所有数据源"这类场景，不要用于查询某个作业的数据源。
            9. 创建数据源时支持的类型：MYSQL、POSTGRESQL、ORACLE、SQLSERVER、DB2、HIVE。
            10. 操作完成后用友好的语言反馈结果；失败时给出清晰的原因和建议。
            """;
}
