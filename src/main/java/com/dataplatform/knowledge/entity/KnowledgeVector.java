package com.dataplatform.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 知识向量表实体
 *
 * 对应 PostgreSQL knowledge_vectors 表
 */
@Entity
@Table(name = "knowledge_vectors")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class KnowledgeVector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chunk_id")
    private Long chunkId;

    @Column(name = "doc_type", nullable = false)
    private String docType;

    @Column(name = "route_action")
    private String routeAction;

    @Column(name = "text", columnDefinition = "TEXT", nullable = false)
    private String text;

    @Column(name = "rerank_text", columnDefinition = "TEXT")
    private String rerankText;

    @Column(name = "section_path")
    private String sectionPath;

    // 向量字段不映射到 JPA（由 MyBatis 处理）
    @Transient
    private String embedding;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
