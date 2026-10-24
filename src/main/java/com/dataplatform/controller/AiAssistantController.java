package com.dataplatform.controller;

import com.dataplatform.ai.ToolMonitorConfig;
import com.dataplatform.ai.confirmation.NeedConfirmationException;
import com.dataplatform.ai.tools.DataSourceTool;
import com.dataplatform.ai.tools.FaqTool;
import com.dataplatform.ai.tools.JobTool;
import com.dataplatform.common.ApiResponse;
import com.dataplatform.mapper.FaqMapper;
import com.dataplatform.service.DataSourceService;
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
    private final FaqMapper faqMapper;
    private final ChatMemory chatMemory;
    private final SystemPromptTemplate pureConversationSystemPrompt;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiAssistantController(
            ChatModel chatModel,
            JobService jobService,
            DataSourceService dataSourceService,
            FaqMapper faqMapper,
            ChatMemory chatMemory,
            @Qualifier("pureConversationSystemPrompt") SystemPromptTemplate pureConversationSystemPrompt
    ) {
        this.chatModel = chatModel;
        this.jobService = jobService;
        this.dataSourceService = dataSourceService;
        this.faqMapper = faqMapper;
        this.chatMemory = chatMemory;
        this.pureConversationSystemPrompt = pureConversationSystemPrompt;
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
                    .call()
                    .content();

            log.info("AI 响应：{}", response);

            Map<String, Object> result = new HashMap<>();
            result.put("message", response);
            result.put("needConfirmation", false);

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("AI对话失败", e);
            return ApiResponse.error("AI对话失败：" + e.getMessage());
        }
    }

    @Operation(summary = "AI 对话流式输出 - SSE（支持逐行表格渲染 + 工具调用可见）")
@GetMapping(value = "/chat/stream")
public Flux<ServerSentEvent<Map<String, Object>>> chatStream(@RequestParam String message) {
    if (message == null || message.trim().isEmpty()) {
        return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                .event("error")
                .data(Map.of("message", "消息不能为空"))
                .build());
    }

    try {
        log.info("收到用户消息（流式）：{}", message);

        // 为当前请求创建独立的 UI 事件通道
        Sinks.Many<Map<String, Object>> uiEventSink = Sinks.many().unicast().onBackpressureBuffer();

        // 构建工具并注入 Sink
        JobTool jobTool = new JobTool(jobService, dataSourceService);
        jobTool.setUiEventSink(uiEventSink);
        DataSourceTool dataSourceTool = new DataSourceTool(dataSourceService);
        FaqTool faqTool = new FaqTool(faqMapper);

        // 使用工厂方法包装工具（装饰器模式）
        ToolCallback[] monitoredTools = ToolMonitorConfig.wrapTools(uiEventSink, jobTool, dataSourceTool, faqTool);

        // 使用 ChatClient.Builder，传入包装后的工具回调
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultToolCallbacks(monitoredTools)
                .build();

        // 文本流：模型逐 token 生成
        Flux<ServerSentEvent<Map<String, Object>>> textStream = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .stream()
                .chatResponse()
                .mapNotNull(resp -> {
                    String content = resp.getResult() != null && resp.getResult().getOutput() != null
                            ? resp.getResult().getOutput().getText()
                            : null;
                    if (content == null || content.isEmpty()) {
                        return null;
                    }
                    return ServerSentEvent.<Map<String, Object>>builder()
                            .event("text")
                            .data(Map.of("content", content))
                            .build();
                })
                .doOnComplete(() -> uiEventSink.tryEmitComplete())
                .doOnError(e -> uiEventSink.tryEmitComplete());

        // UI 事件流：工具方法推送的表格数据 + 工具调用状态
        Flux<ServerSentEvent<Map<String, Object>>> uiStream = uiEventSink.asFlux()
                .map(event -> {
                    String eventName = (String) event.get("event");
                    Object payload = event.get("payload");
                    return ServerSentEvent.<Map<String, Object>>builder()
                            .event(eventName)
                            .data((Map<String, Object>) payload)
                            .build();
                });

        // 合并两条流，并在最后发送 done 事件
        return Flux.merge(textStream, uiStream)
                .concatWith(Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                        .event("done")
                        .data(Map.of())
                        .build()))
                .onErrorResume(e -> {
                    log.error("AI 对话流式失败", e);
                    return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                            .event("error")
                            .data(Map.of("message", "AI对话失败：" + e.getMessage()))
                            .build());
                });
    } catch (NeedConfirmationException e) {
        log.warn("[Confirmation Required] {}", e.getMessage());
        return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                .event("confirmation_required")
                .data(Map.of(
                        "operation", e.getOperation(),
                        "riskLevel", e.getRiskLevel(),
                        "confirmationToken", e.getConfirmationToken(),
                        "message", e.getMessage()
                ))
                .build());
    } catch (Exception e) {
        log.error("AI 对话流式失败", e);
        return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                .event("error")
                .data(Map.of("message", "AI对话失败：" + e.getMessage()))
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

            // 构建 ChatClient（不带工具，启用 ChatMemory）
            ChatClient client = ChatClient.builder(chatModel)
                    .defaultSystem(pureConversationSystemPrompt.getTemplate())
                    .defaultAdvisors(
                            MessageChatMemoryAdvisor.builder(chatMemory).build()
                    )
                    .build();

            // 流式调用（逐 token 返回）
            Flux<String> contentStream = client.prompt()
                    .user(message)
                    .advisors(a -> a.param("conversationId", conversationId))
                    .stream()
                    .content();

            // 包装成 SSE 事件
            Flux<ServerSentEvent<Map<String, Object>>> textStream = contentStream
                    .filter(content -> content != null && !content.isEmpty())
                    .map(content -> ServerSentEvent.<Map<String, Object>>builder()
                            .event("text")
                            .data(Map.of("content", content))
                            .build());

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
                        return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                                .event("error")
                                .data(Map.of("message", "对话失败：" + e.getMessage()))
                                .build());
                    });

        } catch (Exception e) {
            log.error("[纯对话流式] 失败", e);
            return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                    .event("error")
                    .data(Map.of("message", "对话失败：" + e.getMessage()))
                    .build());
        }
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
            5. 删除和发布作业必须二次确认：
               - 先调用 deleteJob/publishJob/deleteDataSource 并传 confirmed=false，拿到确认提示后向用户说明风险；
               - 用户明确同意后，再调用一次并传 confirmed=true 执行。
            6. 查询类工具（listRecentJobs、searchJobs）会以表格形式流式推送到前端，你只需返回简单的摘要文字。
            7. 查询某个作业的数据源信息时：先调用 getJobDetails 获取数据源ID，再调用 getDataSourceDetails 获取详情，不要调用 searchDataSources 或 listAllDataSources。
            8. searchDataSources 和 listAllDataSources 只用于"搜索数据源"或"列出所有数据源"这类场景，不要用于查询某个作业的数据源。
            9. 创建数据源时支持的类型：MYSQL、POSTGRESQL、ORACLE、SQLSERVER、DB2、HIVE。
            10. 操作完成后用友好的语言反馈结果；失败时给出清晰的原因和建议。
            """;
}
