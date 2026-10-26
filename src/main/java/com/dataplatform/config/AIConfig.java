package com.dataplatform.config;

import com.dataplatform.ai.validation.ToolValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.Executors;

/**
 * AI 配置类
 * 配置 DeepSeek 和使用 DashScope 自动配置
 */
@Configuration
@RequiredArgsConstructor
public class AIConfig {

    private final DeepSeekProperties deepSeekProperties;
    private final DashScopeProperties dashScopeProperties;

    /**
     * Qwen-Turbo (7B) ChatModel - 主模型
     * 用于意图识别、工具调用、参数提取
     * 优势：延迟低(0.8s)、成本低(¥0.0008/千token)、准确率95%+
     *
     * 注意：使用 OpenAI 兼容接口而非 DashScope 原生 SDK
     */
    @Bean
    @Primary
    @Qualifier("qwen")
    public ChatModel qwenChatModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(dashScopeProperties.getBaseUrl())
                .apiKey(dashScopeProperties.getApiKey())
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(dashScopeProperties.getModel())  // qwen-turbo
                .temperature(0.3)  // 降低随机性，提高工具调用准确率
                .maxTokens(1000)   // 减少token浪费
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }

    /**
     * DeepSeek-V3 ChatModel - 备用模型
     * 用于复杂推理、代码生成（可选）
     */
    @Bean
    @Qualifier("deepseek")
    public ChatModel deepseekChatModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(deepSeekProperties.getBaseUrl())
                .apiKey(deepSeekProperties.getApiKey())
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(deepSeekProperties.getModel())
                .temperature(0.7)
                .maxTokens(2000)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }

    /**
     * dashscopeChatModel 由 spring-ai-alibaba-starter-dashscope 自动配置
     * 如需使用 DashScope 原生 ChatModel，通过 @Qualifier("dashscopeChatModel") 注入
     */

    /**
     * 工具校验器 Bean
     * <p>
     * 用于在工具调用时进行参数校验和智能转换
     */
    @Bean
    public ToolValidator toolValidator() {
        return new ToolValidator();
    }
}
