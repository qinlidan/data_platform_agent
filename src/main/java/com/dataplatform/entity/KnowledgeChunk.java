package com.dataplatform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库切块实体
 * Task 2 - 实体类与MyBatis Mapper
 *
 * 对应表: knowledge_chunks
 * 功能: 存储知识库文档切分后的块，作为原文权威源
 */
@Data
@TableName("knowledge_chunks")
public class KnowledgeChunk {

    /**
     * 自增主键，chunk_id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文档类型：spark调优|数据研发平台用户手册
     */
    @TableField("doc_type")
    private String docType;

    /**
     * 文件名（不含扩展名）
     */
    @TableField("doc_id")
    private String docId;

    /**
     * 一级标题
     */
    @TableField("category")
    private String category;

    /**
     * 二级标题类型枚举：error|slow|pending|boundary|guide|concept|faq
     */
    @TableField("scene_type")
    private String sceneType;

    /**
     * 二级标题中文名
     */
    @TableField("scene_title")
    private String sceneTitle;

    /**
     * 现象描述（可空）
     */
    @TableField("symptom_text")
    private String symptomText;

    /**
     * 三级标题或问法
     */
    @TableField("questions")
    private String questions;

    /**
     * 回答正文
     */
    @TableField("answer")
    private String answer;

    /**
     * 章节路径
     */
    @TableField("section_path")
    private String sectionPath;

    /**
     * 内容类型：faq | policy | manual
     */
    @TableField("content_type")
    private String contentType;

    /**
     * 是否可调优：0-否，1-是
     */
    @TableField("is_tunable")
    private Boolean isTunable;

    /**
     * 路由动作：tunable | not_tunable | escalate | guide
     */
    @TableField("route_action")
    private String routeAction;

    /**
     * 前一个chunk的ID
     */
    @TableField("prev_chunk_id")
    private Long prevChunkId;

    /**
     * 后一个chunk的ID
     */
    @TableField("next_chunk_id")
    private Long nextChunkId;

    /**
     * 向量化状态：pending_vectorization | vectorized
     */
    @TableField("vectorize_status")
    private String vectorizeStatus;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
      @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
      @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
