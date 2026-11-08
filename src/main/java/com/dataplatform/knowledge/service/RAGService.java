package com.dataplatform.knowledge.service;

import com.dataplatform.knowledge.dto.RerankResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * RAG Service：检索（委托 {@link KnowledgeRetrieval}）→ 生成 → 模型自评拒答 → 保存引用。
 *
 * 拒答规则只在 KnowledgeRetrieval 里有一份，这里不重复实现。
 * 失败策略：RerankException、检索或大模型调用异常都属于系统故障，直接向上抛出，
 * 既不当作"知识库没有答案"，也不写入低置信度池，避免污染数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGService {

    public static final String REFUSAL_NO_INFO = "抱歉，知识库中没有相关信息";
    public static final String REFUSAL_LOW_RERANK = "抱歉，我对这个问题的理解不够确定，无法给出可靠的答案。";

    static final String SOURCE_SELF_CHECK = "self_check";

    private static final int SHORT_ANSWER_LENGTH = 50;
    private static final String[] SELF_REFUSAL_MARKERS = {"没有相关信息", "无法回答"};

    static final String SYSTEM_PROMPT = """
        你是知识库问答助手。只能依据用户消息中给出的"参考资料"回答问题。
        如果参考资料不足以回答问题，请直接回复"抱歉，知识库中没有相关信息"，禁止编造或使用参考资料之外的知识。
        回答时用 [1][2] 这样的编号标注引用来源，编号必须与参考资料的编号一致。""";

    private final KnowledgeRetrieval knowledgeRetrieval;
    private final ChatClient.Builder chatClientBuilder;

    /**
     * @param vectorSimThreshold 向量相似度阈值：首位候选 vectorSimilarity &lt; 阈值则拒答（等于阈值通过）
     * @param rerankThreshold    重排阈值：首位 relevanceScore &lt; 阈值则拒答（等于阈值通过）
     * @return 答案或拒答提示
     * @throws RerankService.RerankException 重排服务故障时原样抛出
     */
    public String answer(String query, Long conversationId,
                         double vectorSimThreshold, double rerankThreshold) {

        KnowledgeRetrieval.Result retrieved =
            knowledgeRetrieval.retrieve(query, conversationId, vectorSimThreshold, rerankThreshold);
        if (retrieved.refused()) {
            return retrieved.refusalMessage();
        }

        // 生成异常同样向上抛出
        String answer = chatClientBuilder.build()
            .prompt()
            .system(SYSTEM_PROMPT)
            .user(retrieved.prompt())
            .call()
            .content();

        // 模型自己拒答 → self_check；不保存引用，避免覆盖上一次的有效引用
        if (isSelfRefusal(answer)) {
            log.warn("Model self-refused, query: {}", query);
            knowledgeRetrieval.recordLowConfidence(conversationId, query, SOURCE_SELF_CHECK,
                "模型自评参考资料不足，首位重排分 " + String.format("%.4f", retrieved.topRelevance()));
            return answer == null || answer.isBlank() ? REFUSAL_NO_INFO : answer;
        }

        // 保存引用，下标 0 对应回答里的 [1]，与 PromptBuilder 的编号一致
        knowledgeRetrieval.saveCitations(conversationId, retrieved.references());
        log.info("Generated answer for query: {}", query);
        return answer;
    }

    /** 空回复，或带"没有相关信息/无法回答"且以"抱歉"开头或很短，视为模型自己拒答。 */
    private static boolean isSelfRefusal(String answer) {
        if (answer == null || answer.isBlank()) {
            return true;
        }
        String a = answer.strip();
        for (String marker : SELF_REFUSAL_MARKERS) {
            if (a.contains(marker) && (a.startsWith("抱歉") || a.length() <= SHORT_ANSWER_LENGTH)) {
                return true;
            }
        }
        return false;
    }
}
