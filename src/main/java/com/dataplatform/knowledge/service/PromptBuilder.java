package com.dataplatform.knowledge.service;

import com.dataplatform.knowledge.dto.RerankResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prompt 构建器 - 首尾相关布局
 * 传入列表按相关度降序：最相关放开头，次相关放结尾，其余居中。
 * 参考资料编号从 1 开始，与传入顺序一致（第 i 条即 [i]），引用溯源依赖这一点。
 */
@Component
public class PromptBuilder {

    public String buildPromptWithSearchResults(String query, List<RerankResult> results) {
        if (results == null || results.isEmpty()) {
            return "无相关知识";
        }

        StringBuilder prompt = new StringBuilder();
        int n = results.size();

        // 展示顺序：第 1 条、第 3..n 条、第 2 条（编号保持原始名次）
        appendReference(prompt, 1, results.get(0));
        for (int i = 2; i < n; i++) {
            appendReference(prompt, i + 1, results.get(i));
        }
        if (n > 1) {
            appendReference(prompt, 2, results.get(1));
        }

        prompt.append("用户问题: ").append(query).append("\n");
        prompt.append("请基于以上参考资料回答，并在回答中标注引用编号 [1][2] 等。");
        return prompt.toString();
    }

    private static void appendReference(StringBuilder prompt, int number, RerankResult r) {
        prompt.append("[参考资料 ").append(number).append("]\n");
        prompt.append(r.getText()).append("\n");
        if (r.getSectionPath() != null) {
            prompt.append("来源: ").append(r.getSectionPath()).append("\n");
        }
        prompt.append("\n");
    }
}
