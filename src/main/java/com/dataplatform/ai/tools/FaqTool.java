package com.dataplatform.ai.tools;

import com.dataplatform.ai.CitationPayload;
import com.dataplatform.ai.retry.RetryableQuery;
import com.dataplatform.knowledge.dto.CitationDetail;
import com.dataplatform.knowledge.dto.RerankResult;
import com.dataplatform.knowledge.service.KnowledgeRetrieval;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库问答工具：走 混合检索 → 重排 → 拒答 管线，不再用 LIKE 查 faq 表。
 *
 * 工具内不再调第二次大模型：把带编号的参考资料返回给外层 Agent 模型，
 * 由它组织回答并标注 [1][2]；编号与 /api/chat/citation/{n} 查到的引用一致。
 */
@Component("faqTool")
@Slf4j
public class FaqTool {

    private static final String INSTRUCTION = """

        请只依据以上参考资料回答用户，并在回答中用 [1][2] 标注引用来源，编号必须与参考资料的编号一致。
        参考资料不足以回答时，如实告诉用户知识库中没有相关信息，不要编造。""";

    private final KnowledgeRetrieval knowledgeRetrieval;
    private final double vectorSimThreshold;
    private final double rerankThreshold;

    @Autowired
    public FaqTool(KnowledgeRetrieval knowledgeRetrieval,
                   @Value("${knowledge.rag.vector-sim-threshold:0.6}") double vectorSimThreshold,
                   @Value("${knowledge.rag.rerank-threshold:0.5}") double rerankThreshold) {
        this.knowledgeRetrieval = knowledgeRetrieval;
        this.vectorSimThreshold = vectorSimThreshold;
        this.rerankThreshold = rerankThreshold;
    }

    @Tool(description = """
        查询知识库（Spark 调优、数据研发平台使用手册、故障诊断、性能优化等），返回带编号的参考资料。

        参数：
        - keyword: 用户的问题或关键词。尽量保留完整问题和型号、版本号、报错信息等原词
        - category: 可选，问题分类（如"作业报错"、"作业执行变慢"、"作业未执行"），会作为检索的补充语境

        返回：带 [参考资料 n] 编号的知识片段。回答用户时请用 [n] 标注引用来源。
        返回"知识库中没有相关信息"时，如实告知用户，不要编造。
        """)
    @RetryableQuery
    public String queryFaq(
        @ToolParam(description = "用户的问题或搜索关键词") String keyword,
        @ToolParam(description = "问题分类，可选", required = false) String category,
        ToolContext toolContext
    ) {
        log.info("[AI Tool] queryFaq - keyword: {}, category: {}", keyword, category);

        if (keyword == null || keyword.isBlank()) {
            return "请提供要查询的关键词或完整问题。";
        }
        String query = category == null || category.isBlank() ? keyword.trim() : keyword.trim() + " " + category.trim();
        Long conversationId = conversationIdOf(toolContext);

        // 检索/重排故障原样抛出，由工具拦截器链处理；不能伪装成"知识库没有答案"
        KnowledgeRetrieval.Result result =
            knowledgeRetrieval.retrieve(query, conversationId, vectorSimThreshold, rerankThreshold);

        if (result.refused()) {
            return result.refusalMessage();
        }
        // 命中后立即保存引用（与 RAGService 不同：这里没有"模型自评拒答"的二次判断，外层模型自己决定是否采用）
        knowledgeRetrieval.saveCitations(conversationId, result.references());
        // 同时把引用夹带在返回值末尾：ObservableToolCallback 会剥掉它并作为 citations 事件推给前端。
        // 引用数据随这条消息走，而不是靠"会话最近一次"去查，所以翻看历史回答也不会对错号。
        return CitationPayload.attach(result.prompt() + INSTRUCTION, toCitations(result.references()));
    }

    /** 下标 0 对应回答里的 [1]，与 PromptBuilder 的编号一致。 */
    private static List<CitationDetail> toCitations(List<RerankResult> references) {
        List<CitationDetail> out = new ArrayList<>(references.size());
        for (int i = 0; i < references.size(); i++) {
            RerankResult r = references.get(i);
            out.add(CitationDetail.builder()
                .index(i + 1)
                .chunkId(r.getChunkId())
                .text(r.getText())
                .sectionPath(r.getSectionPath())
                .build());
        }
        return out;
    }

    /** ToolContext 由 AiAssistantController 注入 conversationId；缺失或非数字时不绑定会话，检索照常进行。 */
    private static Long conversationIdOf(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object raw = toolContext.getContext().get("conversationId");
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw.toString().trim());
        } catch (NumberFormatException e) {
            log.warn("[AI Tool] queryFaq: conversationId 不是数字，不绑定会话: {}", raw);
            return null;
        }
    }
}
