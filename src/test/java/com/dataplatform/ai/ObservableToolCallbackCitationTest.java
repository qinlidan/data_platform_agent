package com.dataplatform.ai;

import com.dataplatform.ai.executor.ToolExecutor;
import com.dataplatform.knowledge.dto.CitationDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 回调层：工具返回里夹带的引用要被剥掉并变成 citations 事件，模型和 tool_end 事件里都只能看到干净文本。
 */
class ObservableToolCallbackCitationTest {

    private Sinks.Many<Map<String, Object>> sink;
    private List<Map<String, Object>> events;
    private ToolExecutor executor;
    private ToolCallback delegate;
    private ObservableToolCallback callback;

    private static CitationDetail ref(int i, long id) {
        return CitationDetail.builder().index(i).chunkId(id).text("正文" + i).sectionPath("路径" + i).build();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        sink = Sinks.many().unicast().onBackpressureBuffer();
        events = new ArrayList<>();
        sink.asFlux().subscribe(events::add);

        delegate = mock(ToolCallback.class);
        ToolDefinition def = ToolDefinition.builder().name("queryFaq").description("d").inputSchema("{}").build();
        when(delegate.getToolDefinition()).thenReturn(def);

        // 拦截器链在这里不是被测对象：直接执行传入的动作
        executor = mock(ToolExecutor.class);
        when(executor.execute(any(), any())).thenAnswer(inv -> {
            Function<Object, String> action = inv.getArgument(1);
            return action.apply(inv.getArgument(0));
        });
        callback = new ObservableToolCallback(delegate, sink, executor);
    }

    private Map<String, Object> eventNamed(String name) {
        return events.stream().filter(e -> name.equals(e.get("event"))).findFirst().orElse(null);
    }

    @Test
    void citationsAreStrippedFromTheModelFacingResultAndEmittedAsAnEvent() throws Exception {
        String raw = CitationPayload.attach("[参考资料 1]\n正文1", List.of(ref(1, 3376), ref(2, 3373)));
        when(delegate.call(any(), any(ToolContext.class))).thenReturn(raw);

        String forModel = callback.call("{\"keyword\":\"倾斜\"}", new ToolContext(Map.of("conversationId", "5")));

        assertEquals("[参考资料 1]\n正文1", forModel, "模型只能看到干净文本");
        assertFalse(forModel.contains(CitationPayload.START));

        Map<String, Object> ev = eventNamed("citations");
        assertNotNull(ev, "应推送 citations 事件");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) ev.get("payload");
        @SuppressWarnings("unchecked")
        List<CitationDetail> items = (List<CitationDetail>) payload.get("citations");
        assertEquals(2, items.size());
        assertEquals(1, items.get(0).getIndex());
        assertEquals(3376L, items.get(0).getChunkId());
        assertEquals("路径1", items.get(0).getSectionPath());
        assertEquals("正文2", items.get(1).getText());

        // 前端收到的是 JSON：确认序列化后字段名正是前端要读的 index / chunkId / text / sectionPath
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        assertTrue(json.contains("\"index\":1") && json.contains("\"sectionPath\":\"路径1\"")
            && json.contains("\"text\":\"正文2\"") && json.contains("\"chunkId\":3376"), json);
    }

    @Test
    void toolEndEventNeverCarriesTheRawMarker() {
        String raw = CitationPayload.attach("答案", List.of(ref(1, 1)));
        when(delegate.call(any(), any(ToolContext.class))).thenReturn(raw);

        callback.call("{}", new ToolContext(Map.of()));

        Map<String, Object> end = eventNamed("tool_end");
        assertNotNull(end);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) end.get("payload");
        String result = String.valueOf(payload.get("result"));
        assertEquals("答案", result);
        assertFalse(result.contains(CitationPayload.START));
    }

    @Test
    void toolWithoutCitationsEmitsNoCitationsEventAndIsUnchanged() {
        when(delegate.call(any(), any(ToolContext.class))).thenReturn("普通工具结果");

        String out = callback.call("{}", new ToolContext(Map.of()));

        assertEquals("普通工具结果", out);
        assertTrue(events.stream().noneMatch(e -> "citations".equals(e.get("event"))));
    }

    @Test
    void corruptedPayloadStillReturnsCleanTextAndDoesNotBreakTheToolCall() {
        when(delegate.call(any(), any(ToolContext.class)))
            .thenReturn("答案" + CitationPayload.START + "{坏json" + CitationPayload.END);

        String out = callback.call("{}", new ToolContext(Map.of()));

        assertEquals("答案", out);
        assertTrue(events.stream().noneMatch(e -> "citations".equals(e.get("event"))));
        assertNotNull(eventNamed("tool_end"));
    }

    @Test
    void citationsEventComesBeforeToolEndSoTheFrontendHasThemWhenTextArrives() {
        when(delegate.call(any(), any(ToolContext.class)))
            .thenReturn(CitationPayload.attach("答案", List.of(ref(1, 1))));

        callback.call("{}", new ToolContext(Map.of()));

        List<Object> names = events.stream().map(e -> e.get("event")).toList();
        assertTrue(names.indexOf("citations") >= 0);
        assertTrue(names.indexOf("citations") < names.indexOf("tool_end"), "实际顺序: " + names);
    }

    @Test
    void anotherToolReturningTextThatLooksLikeCitationsIsNotTouchedWithoutMarker() {
        String lookalike = "[{\"index\":1,\"chunkId\":9}]";
        when(delegate.call(any(), any(ToolContext.class))).thenReturn(lookalike);

        assertEquals(lookalike, callback.call("{}", new ToolContext(Map.of())));
        assertTrue(events.stream().noneMatch(e -> "citations".equals(e.get("event"))));
    }
}
