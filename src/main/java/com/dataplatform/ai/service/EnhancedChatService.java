package com.dataplatform.ai.service;

import com.dataplatform.knowledge.dto.RerankResult;
import com.dataplatform.knowledge.service.KnowledgeRetrieval;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 增强对话服务 - 集成知识库检索
 * 在 Job 列表页助手中使用，提供基于知识库的问答能力
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EnhancedChatService {

    private final KnowledgeRetrieval knowledgeRetrieval;

    /**
     * 知识库增强的对话上下文构建（带默认阈值）
     * @param userQuestion 用户问题
     * @param topK 检索文档数量
     * @return 增强后的系统提示和引用元数据
     */
    public ChatContext enhanceWithKnowledgeBase(String userQuestion, int topK) {
        // 使用默认阈值：向量相似度 0.7，重排相关度 0.5
        return enhanceWithKnowledgeBase(userQuestion, topK, 0.7, 0.5);
    }

    /**
     * 知识库增强的对话上下文构建（完整参数）
     * @param userQuestion 用户问题
     * @param topK 检索文档数量
     * @param vectorSimThreshold 向量相似度阈值
     * @param rerankThreshold 重排阈值
     * @return 增强后的系统提示和引用元数据
     */
    public ChatContext enhanceWithKnowledgeBase(
            String userQuestion,
            int topK,
            double vectorSimThreshold,
            double rerankThreshold
    ) {
        log.info("[EnhancedChat] 开始知识库检索，问题: {}", userQuestion);

        try {
            // 1. 使用 KnowledgeRetrieval 检索相关文档
            KnowledgeRetrieval.Result result = knowledgeRetrieval.retrieve(
                    userQuestion,
                    null,  // conversationId 在 Job 列表助手中不传
                    vectorSimThreshold,
                    rerankThreshold
            );

            // 2. 如果检索失败（拒答），返回空上下文
            if (result.refused()) {
                log.info("[EnhancedChat] 知识库检索拒答: {}", result.refusalMessage());
                return ChatContext.empty();
            }

            // 3. 从检索结果中提取引用信息
            List<RerankResult> references = result.references();
            if (references.isEmpty()) {
                return ChatContext.empty();
            }

            // 4. 构建增强的系统提示（直接使用 KnowledgeRetrieval 生成的提示）
            String enhancedSystemPrompt = buildEnhancedPromptFromRetrieval(result);

            // 5. 提取引用元数据（用于前端展示引用来源）
            List<CitationMetadata> citations = extractCitationsFromRerankResults(references);

            log.info("[EnhancedChat] 知识库检索成功，找到 {} 个相关片段，首位重排分: {}",
                    references.size(), result.topRelevance());
            return new ChatContext(enhancedSystemPrompt, citations, true);

        } catch (Exception e) {
            log.error("[EnhancedChat] 知识库检索失败", e);
            return ChatContext.empty();
        }
    }

    /**
     * 从 KnowledgeRetrieval 结果构建增强提示
     */
    private String buildEnhancedPromptFromRetrieval(KnowledgeRetrieval.Result result) {
        StringBuilder context = new StringBuilder();
        context.append("# 知识库参考资料\n\n");
        context.append("以下是从平台知识库中检索到的相关文档片段，请参考这些内容回答用户问题：\n\n");
        context.append(result.prompt());  // KnowledgeRetrieval 已经格式化好的提示
        context.append("\n\n");

        context.append("# 回答指南\n");
        context.append("1. 优先使用上述知识库内容回答用户问题\n");
        context.append("2. 如果引用了某个片段，请在回答中标注 [1]、[2] 等引用标记\n");
        context.append("3. 如果知识库内容不足以回答问题，请结合你的知识补充回答\n");
        context.append("4. 保持回答的准确性和专业性\n");

        return context.toString();
    }

    /**
     * 从 RerankResult 提取引用元数据
     */
    private List<CitationMetadata> extractCitationsFromRerankResults(List<RerankResult> rerankResults) {
        List<CitationMetadata> citations = new ArrayList<>();
        for (int i = 0; i < rerankResults.size(); i++) {
            RerankResult result = rerankResults.get(i);
            CitationMetadata citation = new CitationMetadata();
            citation.setIndex(i + 1);
            citation.setTitle(result.getSectionPath());  // 使用 sectionPath 作为标题
            citation.setSectionPath(result.getSectionPath());  // 设置章节路径
            citation.setContent(result.getText());        // 使用 text 作为内容
            citation.setScore(result.getRelevanceScore());
            citation.setChunkId(result.getChunkId());
            citations.add(citation);
        }
        return citations;
    }

    /**
     * 从 AI 回答中提取实际使用的引用（分析回答文本中的 [1], [2] 标记）
     */
    public List<CitationMetadata> extractUsedCitations(String aiResponse, List<CitationMetadata> allCitations) {
        // 匹配 [数字] 格式的引用
        Pattern pattern = Pattern.compile("\\[(\\d+)\\]");
        Matcher matcher = pattern.matcher(aiResponse);

        Map<Integer, CitationMetadata> usedCitations = new HashMap<>();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            // 找到对应的引用元数据
            allCitations.stream()
                    .filter(c -> c.getIndex() == index)
                    .findFirst()
                    .ifPresent(citation -> usedCitations.put(index, citation));
        }

        // 按引用顺序返回
        return usedCitations.values().stream()
                .sorted((a, b) -> Integer.compare(a.getIndex(), b.getIndex()))
                .collect(Collectors.toList());
    }

    /**
     * 引用元数据（用于前端渲染引用气泡）
     */
    public static class CitationMetadata {
        private int index;
        private String title;
        private String content;
        private String sectionPath;  // 章节路径，用于前端显示
        private Double score;
        private Long chunkId;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        // 前端期望的字段名（与 CitationDetail 保持一致）
        public String getText() {
            return content;
        }

        public String getSectionPath() {
            return sectionPath;
        }

        public void setSectionPath(String sectionPath) {
            this.sectionPath = sectionPath;
        }

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public Long getChunkId() {
            return chunkId;
        }

        public void setChunkId(Long chunkId) {
            this.chunkId = chunkId;
        }
    }

    /**
     * 对话上下文（包含增强的系统提示和引用元数据）
     */
    public static class ChatContext {
        private final String enhancedSystemPrompt;
        private final List<CitationMetadata> citations;
        private final boolean hasKnowledgeBase;

        public ChatContext(String enhancedSystemPrompt, List<CitationMetadata> citations, boolean hasKnowledgeBase) {
            this.enhancedSystemPrompt = enhancedSystemPrompt;
            this.citations = citations;
            this.hasKnowledgeBase = hasKnowledgeBase;
        }

        public static ChatContext empty() {
            return new ChatContext("", new ArrayList<>(), false);
        }

        public String getEnhancedSystemPrompt() {
            return enhancedSystemPrompt;
        }

        public List<CitationMetadata> getCitations() {
            return citations;
        }

        public boolean hasKnowledgeBase() {
            return hasKnowledgeBase;
        }
    }
}
