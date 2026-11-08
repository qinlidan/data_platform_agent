package com.dataplatform.workflow.service;

import com.dataplatform.workflow.config.WorkflowProperties;
import com.dataplatform.workflow.dto.Domain;
import com.dataplatform.workflow.dto.IntentClassificationResult;
import com.dataplatform.workflow.dto.IntentType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentClassificationService {

    private static final String SYSTEM_PROMPT = """
        你是意图分类助手。根据用户消息，输出 JSON 格式的分类结果。

        【分类规则】
        一级分类（domain）：
        - DATAPLATFORM_MANAGER：涉及作业管理（查询、创建、修改、删除、发布作业）或数据源管理（查询、创建、修改、删除、测试连接）
        - QA：询问 Spark 参数调优、平台使用方法、故障诊断、性能优化等知识问答
        - CHITCHAT：打招呼、闲聊、问候
        - OTHER：以上都不是

        二级分类（intent_type）：
        - 仅当 domain=DATAPLATFORM_MANAGER 时才分类：
          - CREATE_SUBMIT_PUBLISH：明确要求"创建、提交并发布"或"一键上线"作业的复合操作
          - OTHER：其他操作（查询、单独创建、单独发布、修改、删除等）
        - 其他 domain 统一返回 NONE

        【输出格式】
        严格输出 JSON，不要输出任何其他内容：
        {"domain": "...", "intent_type": "..."}

        【示例】
        用户: Spark 的 executor 内存怎么设置？
        输出: {"domain": "QA", "intent_type": "NONE"}

        用户: 查询作业 ID 为 3 的详情
        输出: {"domain": "DATAPLATFORM_MANAGER", "intent_type": "OTHER"}

        用户: 帮我创建、提交并发布作业 user_sync
        输出: {"domain": "DATAPLATFORM_MANAGER", "intent_type": "CREATE_SUBMIT_PUBLISH"}

        用户: 你好
        输出: {"domain": "CHITCHAT", "intent_type": "NONE"}

        用户: 今天天气怎么样
        输出: {"domain": "OTHER", "intent_type": "NONE"}
        """;

    private final ChatClient.Builder chatClientBuilder;
    private final WorkflowProperties workflowProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IntentClassificationResult classify(String message) {
        if (!workflowProperties.getIntentClassification().isEnabled()) {
            log.debug("Intent classification disabled, using fallback");
            return IntentClassificationResult.fallback();
        }

        try {
            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .call()
                .content();

            log.debug("Intent classification response: {}", response);

            return parseResponse(response);

        } catch (Exception e) {
            log.warn("Intent classification failed for message: {}, error: {}", message, e.getMessage());
            return IntentClassificationResult.fallback();
        }
    }

    private IntentClassificationResult parseResponse(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);
            String domainStr = node.get("domain").asText();
            String intentTypeStr = node.get("intent_type").asText();

            Domain domain = Domain.valueOf(domainStr);
            IntentType intentType = IntentType.valueOf(intentTypeStr);

            return new IntentClassificationResult(domain, intentType);

        } catch (Exception e) {
            log.warn("Failed to parse intent classification response: {}, error: {}", response, e.getMessage());
            return IntentClassificationResult.fallback();
        }
    }
}
