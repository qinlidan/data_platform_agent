package com.dataplatform.knowledge.evaluation;

import com.dataplatform.knowledge.dto.ChunkCandidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 把人工标注的章节文字（expect_section）解析成真实的 chunk_id 集合。
 *
 * 为什么按章节文字而不是 chunk_id 标注：重新入库后 chunk_id 会整体重新编号，
 * 写死的 id 立刻失效；章节文字只依赖文档内容，换库、重入库都不用重新标注。
 *
 * 标注格式：「标题片段,标题片段,...,正文片段」，片段之间用逗号（中英文均可）连接。
 * chunk 的 section_path 格式：「文档标题 / 一级标题 / 二级标题 / ...」。
 *
 * 匹配规则：每个片段都先归一化（去掉段首编号和 markdown #、去掉所有标点和空白、转小写），
 * 一个 chunk 命中，当且仅当标注的每个片段都能在该 chunk 路径的某一段里找到——
 * 片段包含路径段，或路径段包含片段，任一方向即可。
 * 这样既容忍标注里漏写结尾标点、反引号，也支持只标到上级标题（命中其下全部子块）。
 */
@Component
public class SectionResolver {

    /** 段首的 markdown # 和编号，如 "## "、"1. "、"7.1 "、"7.1 ："，归一化前先去掉。 */
    private static final Pattern LEADING_NUMBERING =
        Pattern.compile("^[\\s#]*(\\d+(\\.\\d+)*[.\\s：:、]*)?");
    /** 归一化时丢弃的字符：除字母、数字（含汉字）以外的全部。 */
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]");
    private static final Pattern COMMA = Pattern.compile("[,，]");
    private static final String PATH_SEPARATOR = " / ";

    /**
     * @return 命中的 chunk_id（保持 chunks 的顺序，去重）；没有任何命中时返回空集合，不抛异常。
     */
    public Set<Long> resolve(String expectSection, List<ChunkCandidate> chunks) {
        Set<Long> result = new LinkedHashSet<>();
        if (expectSection == null || chunks == null || chunks.isEmpty()) {
            return result;
        }
        List<String> parts = new ArrayList<>();
        for (String raw : COMMA.split(expectSection)) {
            String n = normalize(raw);
            if (!n.isEmpty()) {
                parts.add(n);
            }
        }
        if (parts.isEmpty()) {
            return result;
        }
        for (ChunkCandidate chunk : chunks) {
            if (chunk == null || chunk.getChunkId() == null || chunk.getSectionPath() == null) {
                continue;
            }
            List<String> segments = segmentsOf(chunk.getSectionPath());
            if (parts.stream().allMatch(p -> matchesAnySegment(p, segments))) {
                result.add(chunk.getChunkId());
            }
        }
        return result;
    }

    private static boolean matchesAnySegment(String part, List<String> segments) {
        for (String s : segments) {
            if (containsWholeNumbers(s, part) || containsWholeNumbers(part, s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 {@code haystack} 是否包含 {@code needle}，且不会把一个数字当成更长数字的一部分：
     * “模块1” 在 “模块10” 里的位置后面紧跟着数字 0，不算命中；“模块1” 在 “模块1小节” 里算命中。
     * 只检查 needle 的两端：前面不能紧贴数字（当 needle 以数字开头），后面不能紧贴数字（当 needle 以数字结尾）。
     */
    private static boolean containsWholeNumbers(String haystack, String needle) {
        if (needle.isEmpty()) {
            return false;
        }
        boolean startsWithDigit = Character.isDigit(needle.charAt(0));
        boolean endsWithDigit = Character.isDigit(needle.charAt(needle.length() - 1));
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return false;
            }
            int end = at + needle.length();
            boolean leftOk = !startsWithDigit || at == 0 || !Character.isDigit(haystack.charAt(at - 1));
            boolean rightOk = !endsWithDigit || end == haystack.length() || !Character.isDigit(haystack.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            from = at + 1;
        }
    }

    private static List<String> segmentsOf(String sectionPath) {
        List<String> out = new ArrayList<>();
        for (String seg : sectionPath.split(Pattern.quote(PATH_SEPARATOR))) {
            String n = normalize(seg);
            if (!n.isEmpty()) {
                out.add(n);
            }
        }
        return out;
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String stripped = LEADING_NUMBERING.matcher(s.trim()).replaceFirst("");
        return NON_WORD.matcher(stripped).replaceAll("").toLowerCase();
    }
}
