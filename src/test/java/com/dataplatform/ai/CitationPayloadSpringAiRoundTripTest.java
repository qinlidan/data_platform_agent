package com.dataplatform.ai;

import com.dataplatform.knowledge.dto.CitationDetail;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 不用 mock：让引用负载真的穿过 Spring AI 的 @Tool 包装（ToolCallbacks.from）。
 * 回归：Spring AI 会把 String 返回值序列化成 JSON 字符串（首尾加引号，控制字符转义成 \\u0002），
 * 以前直接用 indexOf(控制字符) 找标记，在真实链路里永远找不到，单元测试因为 mock 了 delegate 而没发现。
 */
class CitationPayloadSpringAiRoundTripTest {

    static class FakeTool {
        @Tool(description = "returns text with citations attached")
        public String lookup(String keyword) {
            return CitationPayload.attach("[参考资料 1]\n含 \"引号\" 和 \\反斜杠 的正文\n第二行", List.of(
                CitationDetail.builder().index(1).chunkId(3376L).text("个别 task 很慢\n日志里有 \"quote\"")
                    .sectionPath("文档二 / 2. 数据倾斜").build()));
        }

        @Tool(description = "plain text, no citations")
        public String plain(String keyword) {
            return "普通返回，含 \"引号\"";
        }
    }

    private static ToolCallback tool(String name) {
        for (ToolCallback cb : ToolCallbacks.from(new FakeTool())) {
            if (cb.getToolDefinition().name().equals(name)) {
                return cb;
            }
        }
        throw new IllegalStateException("no tool " + name);
    }

    @Test
    void citationsSurviveSpringAiToolResultSerialization() {
        String fromSpringAi = tool("lookup").call("{\"keyword\":\"x\"}", new ToolContext(Map.of()));

        CitationPayload.Extracted ex = CitationPayload.extract(fromSpringAi);

        assertEquals(1, ex.citations().size(), "穿过 Spring AI 包装后引用丢失，原始返回: " + fromSpringAi);
        assertEquals(3376L, ex.citations().get(0).getChunkId());
        assertEquals("个别 task 很慢\n日志里有 \"quote\"", ex.citations().get(0).getText());
        assertEquals("文档二 / 2. 数据倾斜", ex.citations().get(0).getSectionPath());
    }

    @Test
    void cleanTextKeepsTheSameJsonStringShapeSpringAiHandedUsSoDownstreamIsUnaffected() throws Exception {
        String fromSpringAi = tool("lookup").call("{\"keyword\":\"x\"}", new ToolContext(Map.of()));

        String clean = CitationPayload.extract(fromSpringAi).cleanText();

        // 回调层的返回值会交回 Spring AI 继续处理，形态必须和改造前一致：仍是 JSON 字符串
        assertTrue(clean.startsWith("\"") && clean.endsWith("\""), clean);
        // 解码后恰好是原始正文：不含标记，也不含任何引用 JSON
        String body = new com.fasterxml.jackson.databind.ObjectMapper().readValue(clean, String.class);
        assertEquals("[参考资料 1]\n含 \"引号\" 和 \\反斜杠 的正文\n第二行", body);
        assertFalse(clean.contains("u0002"));
        assertFalse(clean.contains("chunkId"));
    }

    @Test
    void plainToolResultIsStillReadableAndHasNoCitations() {
        String fromSpringAi = tool("plain").call("{\"keyword\":\"x\"}", new ToolContext(Map.of()));

        CitationPayload.Extracted ex = CitationPayload.extract(fromSpringAi);

        assertTrue(ex.citations().isEmpty());
        // 无标记时不改动内容，保持与改造前一致（Spring AI 的 JSON 字符串形态原样交给上层）
        assertEquals(fromSpringAi, ex.cleanText());
    }
}
