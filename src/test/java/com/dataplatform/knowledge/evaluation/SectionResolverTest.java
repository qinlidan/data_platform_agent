package com.dataplatform.knowledge.evaluation;

import com.dataplatform.knowledge.dto.ChunkCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 把手工标注的章节文字（expect_section）解析成真实 chunk_id。
 * 标注格式：「标题片段,标题片段,...,正文片段」，片段之间用逗号连接，带不带编号、markdown # 都可能。
 * section_path 格式：「文档标题 / 一级 / 二级 / ...」。
 */
class SectionResolverTest {

    private static ChunkCandidate chunk(long id, String sectionPath) {
        ChunkCandidate c = new ChunkCandidate();
        c.setChunkId(id);
        c.setSectionPath(sectionPath);
        return c;
    }

    private static final String DOC = "文档一：Spark/Hive 参数调优知识库 - 作业报错篇";

    private final List<ChunkCandidate> chunks = List.of(
        chunk(1, DOC + " / 1. 报错：Java heap space 或 OutOfMemoryError (OOM) / 1.1 map 端 OOM，作业在读取阶段失败，日志报 `Java heap space`。"),
        chunk(2, DOC + " / 1. 报错：Java heap space 或 OutOfMemoryError (OOM) / 1.2 shuffle 阶段 OOM，作业在执行 join、group by 等宽依赖操作时失败，日志报 `OutOfMemoryError`。"),
        chunk(3, DOC + " / 1. 报错：Java heap space 或 OutOfMemoryError (OOM) / 1.3 ：driver 端 OOM，driver 节点崩溃报错。"),
        chunk(4, DOC + " / 4. 报错：动态分区超限 / 写入 Hive 表时，由于动态分区产生过多，作业失败，报错包含 `dynamic partition exceed max number`。"),
        chunk(5, "手册 / 数据源管理模块 / 如何新增数据源？"),
        chunk(6, "手册 / 数据源管理模块 / 平台支持哪些数据源？"));

    private final SectionResolver resolver = new SectionResolver();

    @Test
    void exactSectionMatchesOnlyThatChunk() {
        Set<Long> ids = resolver.resolve(
            "1. 报错：Java heap space 或 OutOfMemoryError (OOM),1.1 map 端 OOM，作业在读取阶段失败，日志报 `Java heap space`", chunks);
        assertEquals(Set.of(1L), ids);
    }

    @Test
    void parentLevelOnlyMatchesAllChildren() {
        // 标注只写到一级标题：子块 1.1 / 1.2 / 1.3 都算正确答案
        Set<Long> ids = resolver.resolve("1. 报错：Java heap space 或 OutOfMemoryError (OOM),", chunks);
        assertEquals(Set.of(1L, 2L, 3L), ids);
    }

    @Test
    void parentLevelWithoutTrailingCommaAlsoMatchesAllChildren() {
        assertEquals(Set.of(1L, 2L, 3L),
            resolver.resolve("报错：Java heap space 或 OutOfMemoryError (OOM)", chunks));
    }

    @Test
    void leadingSpaceNumberingAndMarkdownHashAreIgnored() {
        assertEquals(Set.of(4L),
            resolver.resolve(" 4. 报错：动态分区超限,写入 Hive 表时，由于动态分区产生过多，作业失败，报错包含 `dynamic partition exceed max number`", chunks));
        assertEquals(Set.of(6L),
            resolver.resolve("## 数据源管理模块,平台支持哪些数据源？", chunks));
    }

    @Test
    void trailingChinesePunctuationDifferenceIsIgnored() {
        // 标注里少了结尾的句号和反引号，chunk 里有
        assertEquals(Set.of(2L), resolver.resolve(
            "1. 报错：Java heap space 或 OutOfMemoryError (OOM),1.2 shuffle 阶段 OOM，作业在执行 join、group by 等宽依赖操作时失败，日志报 `OutOfMemoryError", chunks));
    }

    @Test
    void sectionThatMatchesNothingReturnsEmptyInsteadOfThrowing() {
        assertTrue(resolver.resolve("这一节文档里根本不存在", chunks).isEmpty());
    }

    @Test
    void siblingWithSimilarTitleIsNotMatched() {
        // “如何新增数据源”不能误命中“平台支持哪些数据源”
        assertEquals(Set.of(5L), resolver.resolve("数据源管理模块,如何新增数据源？", chunks));
    }

    @Test
    void numericSuffixIsNotTreatedAsPrefixOfALongerNumber() {
        // 章节名“模块1”不能命中“模块10”“模块11”：纯“字符串包含”会把 1 当成 10 的前缀
        List<ChunkCandidate> many = List.of(
            chunk(1, "手册 / 模块1 / 小节1"),
            chunk(10, "手册 / 模块10 / 小节10"),
            chunk(11, "手册 / 模块11 / 小节11"));
        assertEquals(Set.of(1L), resolver.resolve("模块1,小节1", many));
        assertEquals(Set.of(10L), resolver.resolve("模块10,小节10", many));
    }

    @Test
    void blankOrNullExpectSectionMatchesNothing() {
        assertTrue(resolver.resolve(null, chunks).isEmpty());
        assertTrue(resolver.resolve("   ", chunks).isEmpty());
        assertTrue(resolver.resolve(",,", chunks).isEmpty());
    }

    @Test
    void emptyChunkListMatchesNothing() {
        assertTrue(resolver.resolve("数据源管理模块,如何新增数据源？", List.of()).isEmpty());
    }

    @Test
    void chunkWithNullSectionPathIsSkipped() {
        List<ChunkCandidate> withNull = List.of(chunk(9, null), chunk(5, "手册 / 数据源管理模块 / 如何新增数据源？"));
        assertEquals(Set.of(5L), resolver.resolve("数据源管理模块,如何新增数据源？", withNull));
    }
}
