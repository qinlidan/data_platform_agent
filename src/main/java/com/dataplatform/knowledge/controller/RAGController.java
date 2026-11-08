package com.dataplatform.knowledge.controller;

import com.dataplatform.knowledge.dto.RAGQuestionRequest;
import com.dataplatform.knowledge.dto.RAGQuestionResponse;
import com.dataplatform.knowledge.service.RAGService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * RAG 问答 API 控制器
 */
@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
@Tag(name = "RAG 问答", description = "基于知识库的问答接口")
public class RAGController {

    private final RAGService ragService;

    @PostMapping("/answer")
    @Operation(summary = "回答用户问题", description = "基于知识库检索和重排生成答案")
    public RAGQuestionResponse answer(@RequestBody RAGQuestionRequest request) {

        // 默认值
        // 不再用 -1 兜底：所有匿名请求共用同一个会话 key 会导致引用互相覆盖/串读；null 表示不保存引用
        Long conversationId = request.getConversationId();
        double vectorSimThreshold = request.getVectorSimThreshold() != null ? request.getVectorSimThreshold() : 0.3;
        double rerankThreshold = request.getRerankThreshold() != null ? request.getRerankThreshold() : 0.5;

        String answer = ragService.answer(
            request.getQuery(),
            conversationId,
            vectorSimThreshold,
            rerankThreshold
        );

        // 判断是否拒答
        boolean refused = answer.startsWith("抱歉");

        return RAGQuestionResponse.builder()
            .answer(answer)
            .refused(refused)
            .refusalReason(refused ? "检索结果不足或置信度过低" : null)
            .build();
    }
}
