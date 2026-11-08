package com.dataplatform.knowledge.service;

import com.dataplatform.knowledge.dto.RerankResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    private static RerankResult r(int n, String section) {
        return RerankResult.builder()
            .chunkId((long) n).text("正文" + n).sectionPath(section)
            .relevanceScore(1.0 - n * 0.1).vectorSimilarity(0.8).build();
    }

    private static List<RerankResult> results(int count) {
        List<RerankResult> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(r(i, "章节/" + i));
        }
        return list;
    }

    @Test
    void emptyOrNullReturnsAgreedText() {
        assertEquals("无相关知识", builder.buildPromptWithSearchResults("q", List.of()));
        assertEquals("无相关知识", builder.buildPromptWithSearchResults("q", null));
    }

    @Test
    void numberingStartsAtOneAndMatchesInputOrder() {
        String p = builder.buildPromptWithSearchResults("问题", results(5));

        for (int i = 1; i <= 5; i++) {
            String block = "[参考资料 " + i + "]\n正文" + i + "\n";
            assertTrue(p.contains(block), "编号 " + i + " 应对应传入第 " + i + " 条: \n" + p);
        }
        assertFalse(p.contains("[参考资料 0]"));
        assertFalse(p.contains("[参考资料 6]"));
        assertTrue(p.contains("来源: 章节/3"));
    }

    @Test
    void mostRelevantFirstAndSecondMostRelevantLast() {
        String p = builder.buildPromptWithSearchResults("问题", results(5));

        int pos1 = p.indexOf("[参考资料 1]");
        int pos2 = p.indexOf("[参考资料 2]");
        int pos3 = p.indexOf("[参考资料 3]");
        int pos4 = p.indexOf("[参考资料 4]");
        int pos5 = p.indexOf("[参考资料 5]");
        int question = p.indexOf("用户问题:");

        // 最相关(1)在最前，次相关(2)紧挨问题放最后，其余在中间
        assertTrue(pos1 >= 0 && pos1 < pos3 && pos1 < pos4 && pos1 < pos5);
        assertTrue(pos2 > pos3 && pos2 > pos4 && pos2 > pos5);
        assertTrue(pos2 < question);
    }

    @Test
    void singleResult() {
        String p = builder.buildPromptWithSearchResults("问题", results(1));
        assertEquals(1, countOccurrences(p, "[参考资料 "));
        assertTrue(p.contains("[参考资料 1]\n正文1\n"));
    }

    @Test
    void twoResultsKeepBestFirstSecondLast() {
        String p = builder.buildPromptWithSearchResults("问题", results(2));
        assertTrue(p.indexOf("[参考资料 1]") < p.indexOf("[参考资料 2]"));
        assertEquals(2, countOccurrences(p, "[参考资料 "));
    }

    @Test
    void nullSectionPathOmitsSourceLine() {
        List<RerankResult> list = List.of(r(1, null));
        String p = builder.buildPromptWithSearchResults("问题", list);
        assertFalse(p.contains("来源:"));
    }

    @Test
    void containsQueryAndCitationInstruction() {
        String p = builder.buildPromptWithSearchResults("如何配置数据源", results(3));
        assertTrue(p.contains("用户问题: 如何配置数据源"));
        assertTrue(p.contains("[1][2]"));
    }

    private static int countOccurrences(String s, String sub) {
        int c = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) {
            c++;
            i += sub.length();
        }
        return c;
    }
}
