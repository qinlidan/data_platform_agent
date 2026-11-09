package com.dataplatform.ai.memory;

import lombok.extern.slf4j.Slf4j;

/**
 * Token 预算计算器
 * 根据模型窗口大小，计算各层级（Layer 0/1/2）的 Token 预算分配
 */
@Slf4j
public class TokenBudgetCalculator {

    private final int modelWindow;

    // 固定成本
    private static final int SYSTEM_COST = 8000;      // 系统提示词
    private static final int RETRIEVAL_COST = 2000;   // RAG检索结果
    private static final int SUMMARY_COST = 500;      // 摘要段落
    private static final int OUTPUT_COST = 4000;      // 输出空间
    private static final int SAFETY_COST = 1000;      // 安全缓冲
    private static final int REACT_PEAK_COST = 5000;  // ReAct峰值开销

    // 中文 token 折算率（字/token）
    private static final double CHINESE_CHARS_PER_TOKEN = 1.3;

    public TokenBudgetCalculator(int modelWindow) {
        this.modelWindow = modelWindow;
    }

    /**
     * 预算配置
     * @param modelWindow 模型窗口大小
     * @param historyBudget 历史对话总预算
     * @param layer1Budget Layer 1（原文层）预算
     * @param layer2Budget Layer 2（半压层）预算
     */
    public record BudgetConfig(
        int modelWindow,
        int historyBudget,
        int layer1Budget,
        int layer2Budget
    ) {}

    /**
     * 计算各层预算
     * @return 预算配置
     * @throws IllegalStateException 如果模型窗口太小，无法容纳一轮对话
     */
    public BudgetConfig calculateBudget() {
        int historyBudget = modelWindow - SYSTEM_COST - RETRIEVAL_COST
                           - SUMMARY_COST - OUTPUT_COST - SAFETY_COST - REACT_PEAK_COST;

        if (historyBudget < 500) {
            throw new IllegalStateException(
                String.format("Model window %d too small, cannot fit even one conversation turn", modelWindow)
            );
        }

        // Layer 1 占 70%，Layer 2 占 30%
        int layer1Budget = (int) (historyBudget * 0.7);
        int layer2Budget = (int) (historyBudget * 0.3);

        log.info("[TokenBudget] model={} history={} layer1={} layer2={}",
                 modelWindow, historyBudget, layer1Budget, layer2Budget);

        return new BudgetConfig(modelWindow, historyBudget, layer1Budget, layer2Budget);
    }

    /**
     * 估算文本的 Token 数量
     * @param text 待估算文本
     * @return 估算的 Token 数量
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 简单估算：中文按字数 / 1.3
        return (int) Math.ceil(text.length() / CHINESE_CHARS_PER_TOKEN);
    }
}
