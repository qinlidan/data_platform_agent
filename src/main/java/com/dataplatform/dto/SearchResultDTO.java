package com.dataplatform.dto;

import lombok.Data;

/**
 * 向量检索结果DTO
 * Task 7 - 向量检索服务
 *
 * 包含chunk文本和元数据的完整信息
 */
@Data
public class SearchResultDTO {

    /**
     * Chunk ID
     */
    private Long chunkId;

    /**
     * Chunk文本内容（questions字段）
     */
    private String chunkText;

    /**
     * Chunk类型（scene_type: slow/error/pending/boundary等）
     */
    private String chunkType;

    /**
     * 文档类型（doc_type）
     */
    private String docType;

    /**
     * 文档ID
     */
    private String docId;

    /**
     * 相似度得分（0-1之间，1表示完全相同）
     */
    private Float similarity;

    /**
     * 回答内容
     */
    private String answer;

    /**
     * 章节路径
     */
    private String sectionPath;

    /**
     * 路由动作（tunable/not_tunable/escalate/guide）
     */
    private String routeAction;
}
