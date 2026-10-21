package com.dataplatform.service.impl;

import com.dataplatform.dto.JobOperationRequest;
import com.dataplatform.dto.JobOperationResponse;
import com.dataplatform.service.NaturalLanguageJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 自然语言作业管理服务实现类
 * 使用 DeepSeek 进行推理任务，DashScope 进行意图理解
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NaturalLanguageJobServiceImpl implements NaturalLanguageJobService {

    @Qualifier("deepseek")
    private final ChatModel deepseekChatModel;

    @Qualifier("dashscope")
    private final ChatModel dashscopeChatModel;

    @Override
    public JobOperationResponse chat(JobOperationRequest request) {
        log.info("处理自然语言对话: {}", request.getUserInput());

        try {
            // 使用 DashScope 进行通用对话
            String promptText = "你是一个数据平台助手，帮助用户管理数据作业。\n\n用户问题：" + request.getUserInput();

            Prompt prompt = new Prompt(promptText);
            ChatResponse response = dashscopeChatModel.call(prompt);

            String aiResponse = response.getResult().getOutput().getText();

            return JobOperationResponse.builder()
                    .success(true)
                    .message(aiResponse)
                    .build();
        } catch (Exception e) {
            log.error("对话失败", e);
            return JobOperationResponse.builder()
                    .success(false)
                    .message("对话失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public JobOperationResponse createJob(JobOperationRequest request) {
        log.info("处理创建作业请求: {}", request.getUserInput());

        try {
            // 使用 DeepSeek 进行作业配置分析
            String promptText = """
                你是一个数据平台专家，负责根据用户需求生成作业配置。

                用户需求：%s

                请分析用户需求，并生成作业配置建议。输出格式：
                1. 作业名称
                2. 作业类型（DB2HIVE/HIVE2HIVE等）
                3. 数据源配置
                4. 目标数据库和表
                5. 执行引擎（HIVE/SPARK_SQL）

                请给出具体、可执行的配置建议。
                """.formatted(request.getUserInput());

            Prompt prompt = new Prompt(promptText);
            ChatResponse response = deepseekChatModel.call(prompt);

            String aiResponse = response.getResult().getOutput().getText();

            return JobOperationResponse.builder()
                    .success(true)
                    .message(aiResponse)
                    .operationType("CREATE")
                    .build();
        } catch (Exception e) {
            log.error("创建作业失败", e);
            return JobOperationResponse.builder()
                    .success(false)
                    .message("创建作业失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public JobOperationResponse updateJob(JobOperationRequest request) {
        log.info("处理修改作业请求: {}", request.getUserInput());

        try {
            // 使用 DeepSeek 分析修改需求
            String promptText = """
                你是一个数据平台专家，负责分析用户的作业修改需求。

                用户需求：%s

                请分析用户想要修改的内容，并给出具体的修改建议。
                包括：
                1. 识别需要修改的作业
                2. 需要修改的配置项
                3. 新的配置值
                4. 修改的影响范围
                5. 注意事项

                请给出详细、清晰的修改方案。
                """.formatted(request.getUserInput());

            Prompt prompt = new Prompt(promptText);
            ChatResponse response = deepseekChatModel.call(prompt);

            String aiResponse = response.getResult().getOutput().getText();

            return JobOperationResponse.builder()
                    .success(true)
                    .message(aiResponse)
                    .operationType("UPDATE")
                    .build();
        } catch (Exception e) {
            log.error("修改作业失败", e);
            return JobOperationResponse.builder()
                    .success(false)
                    .message("修改作业失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public JobOperationResponse publishJob(JobOperationRequest request) {
        log.info("处理发布作业请求: {}", request.getUserInput());

        try {
            // 使用 DeepSeek 分析发布前检查
            String promptText = """
                你是一个数据平台专家，负责分析作业发布前的检查项。

                用户需求：%s

                请分析并列出发布前的检查项：
                1. 识别要发布的作业
                2. SQL配置完整性检查
                3. 字段元数据检查
                4. 依赖关系检查
                5. 潜在风险提示
                6. 发布建议

                请给出详细的检查报告。
                """.formatted(request.getUserInput());

            Prompt prompt = new Prompt(promptText);
            ChatResponse response = deepseekChatModel.call(prompt);

            String aiResponse = response.getResult().getOutput().getText();

            return JobOperationResponse.builder()
                    .success(true)
                    .message(aiResponse)
                    .operationType("PUBLISH")
                    .build();
        } catch (Exception e) {
            log.error("发布作业失败", e);
            return JobOperationResponse.builder()
                    .success(false)
                    .message("发布作业失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public JobOperationResponse deleteJob(JobOperationRequest request) {
        log.info("处理删除作业请求: {}", request.getUserInput());

        try {
            // 使用 DeepSeek 分析删除风险
            String promptText = """
                你是一个数据平台专家，负责分析作业删除的风险和影响。

                用户需求：%s

                请分析：
                1. 识别要删除的作业
                2. 作业当前状态检查
                3. 依赖关系分析（是否被其他作业引用）
                4. 删除影响范围
                5. 风险提示
                6. 删除建议（是否建议归档而不是删除）

                请给出详细的风险评估报告。
                """.formatted(request.getUserInput());

            Prompt prompt = new Prompt(promptText);
            ChatResponse response = deepseekChatModel.call(prompt);

            String aiResponse = response.getResult().getOutput().getText();

            return JobOperationResponse.builder()
                    .success(true)
                    .message(aiResponse)
                    .operationType("DELETE")
                    .build();
        } catch (Exception e) {
            log.error("删除作业失败", e);
            return JobOperationResponse.builder()
                    .success(false)
                    .message("删除作业失败: " + e.getMessage())
                    .build();
        }
    }
}
