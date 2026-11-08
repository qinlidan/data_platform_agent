package com.dataplatform.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 引用详情：回答里的 [n] 对应的参考资料。
 * 只含 pgvector 里的工作副本（text / section_path），点"查看完整原文"才回查 MySQL。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationDetail {
    /** 引用序号，从 1 开始，与回答里的 [n] 一致 */
    private Integer index;
    private Long chunkId;
    /** 参考资料正文（pgvector.text 或 rerank_text 的工作副本） */
    private String text;
    /** 章节路径，用于前端展示来源 */
    private String sectionPath;
}
