package com.dataplatform.ai.tools;

import com.dataplatform.knowledge.dto.CitationDetail;
import com.dataplatform.knowledge.service.CitationStore;
import com.dataplatform.knowledge.service.RAGService;
import com.dataplatform.ai.ToolMonitorConfig;
import com.dataplatform.ai.executor.ToolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 Spring 上下文 + 真实 PostgreSQL / 重排接口：
 * 验证 queryFaq 作为模型工具能被正确注册、能收到 ToolContext 里的 conversationId，并走完整检索管线。
 * 库里没有数据时检索会拒答，这是合法结果；断言只依赖"管线跑通"，不依赖具体内容。
 */
@SpringBootTest
class FaqToolWiringIT {

    @Autowired FaqTool faqTool;
    @Autowired CitationStore citationStore;
    @Autowired ToolExecutor toolExecutor;

    @Test
    void toolCallbackExposesOnlyModelFacingParametersNotToolContext() {
        ToolCallback[] callbacks = ToolCallbacks.from(faqTool);

        assertEquals(1, callbacks.length);
        String schema = callbacks[0].getToolDefinition().inputSchema();
        assertEquals("queryFaq", callbacks[0].getToolDefinition().name());
        assertTrue(schema.contains("keyword"), schema);
        assertTrue(schema.contains("category"), schema);
        // ToolContext 由框架注入，绝不能出现在给模型看的参数 schema 里，否则模型会试图自己填
        assertTrue(!schema.contains("toolContext") && !schema.contains("ToolContext"), schema);
    }

    @Test
    void realCallThroughWrappedCallbackRunsTheWholePipeline() {
        ToolCallback[] wrapped = ToolMonitorConfig.wrapTools(null, toolExecutor, faqTool);
        long conversationId = 987_000_001L;

        String result = wrapped[0].call(
            "{\"keyword\":\"Spark 内存溢出 OOM 怎么处理\"}",
            new ToolContext(Map.of("userId", "it-user", "conversationId", String.valueOf(conversationId))));

        System.out.println("WIRING result=" + result.replace('\n', ' ').substring(0, Math.min(200, result.length())));
        assertNotNull(result);
        boolean hit = result.contains("[参考资料 1]");
        boolean refused = result.contains(RAGService.REFUSAL_NO_INFO) || result.contains(RAGService.REFUSAL_LOW_RERANK);
        assertTrue(hit || refused, "要么带编号命中，要么明确拒答，不应是别的内容: " + result);

        Optional<List<CitationDetail>> saved = citationStore.find(String.valueOf(conversationId));
        // 命中 ⇒ 引用已绑定到这次会话；拒答 ⇒ 不应留下引用
        assertEquals(hit, saved.isPresent(), "引用是否保存应与是否命中一致");
        if (hit) {
            assertEquals(1, saved.get().get(0).getIndex());
        }
    }

    @Test
    void realCallPushesACitationsEventWhoseContentMatchesWhatTheModelWasToldAndWhatIsStored() throws Exception {
        reactor.core.publisher.Sinks.Many<Map<String, Object>> sink =
            reactor.core.publisher.Sinks.many().unicast().onBackpressureBuffer();
        List<Map<String, Object>> events = new java.util.ArrayList<>();
        sink.asFlux().subscribe(events::add);
        ToolCallback[] wrapped = ToolMonitorConfig.wrapTools(sink, toolExecutor, faqTool);
        long conversationId = 987_000_002L;

        String forModel = wrapped[0].call(
            "{\"keyword\":\"只有个别 task 跑了几十分钟 其余几秒结束 数据倾斜\"}",
            new ToolContext(Map.of("userId", "it-user", "conversationId", String.valueOf(conversationId))));

        // 模型看到的文本里绝不能夹带标记
        assertTrue(!forModel.contains(com.dataplatform.ai.CitationPayload.START), "标记泄露给了模型");

        Map<String, Object> ev = events.stream().filter(e -> "citations".equals(e.get("event"))).findFirst().orElse(null);
        boolean hit = forModel.contains("[参考资料 1]");
        if (!hit) {
            assertTrue(ev == null, "拒答时不应推送 citations 事件");
            return;
        }
        assertNotNull(ev, "命中时必须推送 citations 事件");
        @SuppressWarnings("unchecked")
        List<CitationDetail> pushed = (List<CitationDetail>) ((Map<String, Object>) ev.get("payload")).get("citations");
        List<CitationDetail> stored = citationStore.find(String.valueOf(conversationId)).orElseThrow();

        // 推给前端的，和存在 CitationStore 里的、和提示词里编号的，必须是同一份
        assertEquals(stored.size(), pushed.size());
        for (int i = 0; i < pushed.size(); i++) {
            assertEquals(i + 1, pushed.get(i).getIndex());
            assertEquals(stored.get(i).getChunkId(), pushed.get(i).getChunkId());
            assertEquals(stored.get(i).getSectionPath(), pushed.get(i).getSectionPath());
            assertTrue(forModel.contains("[参考资料 " + (i + 1) + "]"), "提示词里缺少编号 " + (i + 1));
        }
        System.out.println("WIRING citations=" + pushed.size() + " first=" + pushed.get(0).getSectionPath());
    }
}
