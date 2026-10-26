package com.dataplatform.ai.tools;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.dataplatform.ai.retry.RetryableQuery;
import com.dataplatform.entity.Faq;
import com.dataplatform.service.FaqService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component("faqTool")
@RequiredArgsConstructor
@Slf4j
public class FaqTool {

    private final FaqService faqService;

    @Tool(description = """
        查询常见问题知识库。根据关键词搜索故障诊断、性能优化等问题的答案。

        参数：
        - keyword: 搜索关键词，在问题和答案中模糊匹配（如"OOM"、"慢"、"报错"）
        - category: 可选，问题分类（"作业报错"、"作业执行变慢"、"作业未执行"）

        返回：匹配的 FAQ 列表（最多 5 条），包含问题、答案、分类
        """)
    @RetryableQuery
    public String queryFaq(
        @ToolParam(description = "搜索关键词") String keyword,
        @ToolParam(description = "问题分类，可选", required = false) String category
    ) {
        log.info("[AI Tool] queryFaq - keyword: {}, category: {}", keyword, category);

        List<Faq> faqs = faqService.lambdaQuery()
                .eq(Faq::getIsEnabled, true)
                .and(w -> w.like(Faq::getQuestion, keyword).or().like(Faq::getAnswer, keyword))
                .eq(StringUtils.isNotBlank(category), Faq::getCategory, category)
                .orderByAsc(Faq::getDisplayOrder)
                .last("LIMIT 5")
                .list();

        if (faqs.isEmpty()) {
            return "未找到匹配的FAQ";
        }

        return faqs.stream()
            .map(faq -> String.format("""
                【问题】%s
                【答案】%s
                【分类】%s
                """, faq.getQuestion(), faq.getAnswer(), faq.getCategory()))
            .collect(Collectors.joining("\n---\n"));
    }
}
