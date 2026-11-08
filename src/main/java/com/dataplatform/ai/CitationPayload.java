package com.dataplatform.ai;

import com.dataplatform.knowledge.dto.CitationDetail;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具返回值里夹带引用数据的编解码。
 *
 * 为什么要夹带：queryFaq 是 Spring 单例，拿不到"每次请求独立"的 SSE sink；
 * 而 {@link ObservableToolCallback} 按请求创建、持有 sink。所以工具把引用以一段标记块附在返回文本末尾，
 * 回调层剥掉它并转成 citations 事件推给前端，模型看到的始终是干净文本，不会把 JSON 当成参考资料。
 *
 * 标记用控制字符包起来，正文里即使有 [1]、JSON 形状的文字或 "CITATIONS" 字样都不会被误识别。
 * 解析失败一律"剥掉标记、忽略引用"，绝不把标记泄露给模型，也绝不抛异常打断工具调用。
 */
public final class CitationPayload {

    /**
     * 起止标记：纯 ASCII 控制字符（STX STX / ETX ETX），不含任何可打印字符。
     * 源码里用 (char) 构造，避免不可见字符在编辑器里被悄悄丢掉。
     */
    public static final String START = new String(new char[] {(char) 2, (char) 2});
    public static final String END = new String(new char[] {(char) 3, (char) 3});

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<CitationDetail>> TYPE = new TypeReference<>() {
    };

    private CitationPayload() {
    }

    /** 把引用附在文本末尾；引用为空时原样返回。 */
    public static String attach(String text, List<CitationDetail> citations) {
        if (citations == null || citations.isEmpty()) {
            return text;
        }
        try {
            return (text == null ? "" : text) + START + MAPPER.writeValueAsString(citations) + END;
        } catch (Exception e) {
            // 引用只是增强信息，序列化失败不能影响工具本身的返回
            return text;
        }
    }

    /**
     * 拆出干净文本和引用。
     *
     * Spring AI 的 @Tool 会把 String 返回值序列化成 JSON 字符串（首尾加引号，换行变 \n，控制字符变 ），
     * 所以真实链路里拿到的 raw 不是原文。这里先判断是不是这种 JSON 字符串编码：
     * 是则先解码回原文再找标记；找不到标记时，原样返回传入的 raw（不改动，保持与改造前一致）。
     */
    public static Extracted extract(String raw) {
        if (raw == null) {
            return new Extracted(null, List.of());
        }
        String decoded = decodeJsonStringIfNeeded(raw);
        if (decoded.indexOf(START) < 0) {
            return new Extracted(raw, List.of());
        }
        Extracted ex = split(decoded);
        // 模型之前拿到的是 Spring AI 序列化后的 JSON 字符串；剥离后要保持同样的形态，下游无需感知
        return decoded == raw ? ex : new Extracted(encodeJsonString(ex.cleanText()), ex.citations());
    }

    /** 若 raw 是被 JSON 编码过的字符串（以引号开头结尾且能解码），返回解码后的原文，否则返回 raw 本身。 */
    private static String decodeJsonStringIfNeeded(String raw) {
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            try {
                return MAPPER.readValue(raw, String.class);
            } catch (Exception ignore) {
                // 不是合法的 JSON 字符串：当作原文处理
            }
        }
        return raw;
    }

    private static String encodeJsonString(String s) {
        try {
            return MAPPER.writeValueAsString(s);
        } catch (Exception e) {
            return s;
        }
    }

    private static Extracted split(String raw) {
        int start = raw.indexOf(START);
        String clean = raw.substring(0, start);
        int jsonFrom = start + START.length();
        int end = raw.indexOf(END, jsonFrom);
        if (end < 0) {
            return new Extracted(clean, List.of());       // 标记没闭合：剥掉，忽略
        }
        try {
            List<CitationDetail> parsed = MAPPER.readValue(raw.substring(jsonFrom, end), TYPE);
            return new Extracted(clean, parsed == null ? List.of() : new ArrayList<>(parsed));
        } catch (Exception e) {
            return new Extracted(clean, List.of());       // JSON 损坏：剥掉，忽略
        }
    }

    public record Extracted(String cleanText, List<CitationDetail> citations) {
    }
}
