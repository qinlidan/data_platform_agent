package com.dataplatform.ai;

import com.dataplatform.knowledge.dto.CitationDetail;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具返回值里夹带引用数据的编解码。
 * 工具把引用以一段标记块附在返回文本末尾；回调层剥掉它、转成 citations 事件，模型看到的是干净文本。
 */
class CitationPayloadTest {

    private static CitationDetail ref(int idx, long id, String text, String path) {
        return CitationDetail.builder().index(idx).chunkId(id).text(text).sectionPath(path).build();
    }

    @Test
    void roundTripPreservesEveryFieldIncludingChineseQuotesAndNewlines() {
        List<CitationDetail> refs = List.of(
            ref(1, 3376, "个别 task 长时间运行，\n日志报 `Java heap space`，含 \"双引号\" 和 {花括号}", "文档一 / 3. 数据倾斜"),
            ref(2, 3373, "第二条", "文档一 / 1.2 shuffle"));

        String wrapped = CitationPayload.attach("[参考资料 1]\n正文", refs);
        CitationPayload.Extracted out = CitationPayload.extract(wrapped);

        assertEquals("[参考资料 1]\n正文", out.cleanText());
        assertEquals(2, out.citations().size());
        assertEquals(1, out.citations().get(0).getIndex());
        assertEquals(3376L, out.citations().get(0).getChunkId());
        assertEquals(refs.get(0).getText(), out.citations().get(0).getText());
        assertEquals("文档一 / 1.2 shuffle", out.citations().get(1).getSectionPath());
    }

    @Test
    void cleanTextNeverContainsTheMarker() {
        String wrapped = CitationPayload.attach("答案", List.of(ref(1, 1, "t", "p")));
        assertTrue(wrapped.length() > "答案".length());
        assertFalse(CitationPayload.extract(wrapped).cleanText().contains(CitationPayload.START));
        assertFalse(CitationPayload.extract(wrapped).cleanText().contains(CitationPayload.END));
    }

    @Test
    void textWithoutMarkerIsReturnedUnchangedWithNoCitations() {
        CitationPayload.Extracted out = CitationPayload.extract("普通工具返回，没有引用");
        assertEquals("普通工具返回，没有引用", out.cleanText());
        assertTrue(out.citations().isEmpty());
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertNull(CitationPayload.extract(null).cleanText());
        assertTrue(CitationPayload.extract(null).citations().isEmpty());
        assertEquals("x", CitationPayload.attach("x", List.of()));
        assertEquals("x", CitationPayload.attach("x", null));
    }

    @Test
    void corruptedPayloadIsStrippedAndIgnoredInsteadOfLeakingToTheModelOrThrowing() {
        String broken = "答案" + CitationPayload.START + "{这不是合法 json" + CitationPayload.END;

        CitationPayload.Extracted out = CitationPayload.extract(broken);

        assertEquals("答案", out.cleanText());
        assertTrue(out.citations().isEmpty());
    }

    @Test
    void unterminatedMarkerIsStrippedToo() {
        CitationPayload.Extracted out = CitationPayload.extract("答案" + CitationPayload.START + "[{\"index\":1");
        assertEquals("答案", out.cleanText());
        assertTrue(out.citations().isEmpty());
    }

    @Test
    void markersAreDistinctNonEmptyAndNotPrintableText() {
        // 回归：END 曾经是空串，indexOf("") 永远返回 0，标记完全失效；START 曾是可打印字面量，正文里写出来就会误触发
        assertFalse(CitationPayload.START.isEmpty());
        assertFalse(CitationPayload.END.isEmpty());
        assertFalse(CitationPayload.START.equals(CitationPayload.END));
        assertTrue(CitationPayload.START.chars().allMatch(Character::isISOControl), "START 应只含控制字符");
        assertTrue(CitationPayload.END.chars().allMatch(Character::isISOControl), "END 应只含控制字符");
    }

    @Test
    void aBodyThatMerelyMentionsTheWordCitationsIsNotTreatedAsAPayload() {
        String body = "参考资料里有一节叫 CITATIONS 的说明";
        CitationPayload.Extracted out = CitationPayload.extract(body);
        assertEquals(body, out.cleanText());
        assertTrue(out.citations().isEmpty());
    }

    @Test
    void userContentContainingBracketsOrJsonLookalikeDoesNotConfuseExtraction() {
        String body = "参考资料里写着 [1] 和 {\"index\":9} 这种内容";
        String wrapped = CitationPayload.attach(body, List.of(ref(1, 7, "x", "p")));

        CitationPayload.Extracted out = CitationPayload.extract(wrapped);

        assertEquals(body, out.cleanText());
        assertEquals(1, out.citations().size());
        assertEquals(7L, out.citations().get(0).getChunkId());
    }
}
