package com.dataplatform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识向量实体（PostgreSQL knowledge_vectors 表）
 *
 * 包含完整的混合检索字段：
 * - embedding: 1024维向量
 * - text: 全文检索字段（doc_type + scene_title + questions + answer）
 * - rerank_text: 精排字段（scene_title + questions + answer，不含doc_type）
 * - section_path: 章节路径（用于引用溯源）
 * - text_tsv: tsvector 生成列（由数据库自动维护，不映射到实体）
 */
@Entity
@Table(name = "knowledge_vectors")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeVector {

    @Id
    @Column(name = "chunk_id")
    private Long chunkId;

    @Column(name = "embedding", columnDefinition = "vector(1024)")
    private float[] embedding;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;  // 全文检索字段（doc_type + scene_title + questions + answer）

    @Column(name = "rerank_text", columnDefinition = "TEXT")
    private String rerankText;  // 精排字段（scene_title + questions + answer）

    @Column(name = "section_path", columnDefinition = "TEXT")
    private String sectionPath;

    @Column(name = "doc_type", length = 50)
    private String docType;

    @Column(name = "route_action", length = 100)
    private String routeAction;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // text_tsv 是生成列，不映射到实体（由数据库自动维护）

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
