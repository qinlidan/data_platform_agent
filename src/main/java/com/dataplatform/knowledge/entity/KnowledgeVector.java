package com.dataplatform.knowledge.entity;

import com.dataplatform.knowledge.converter.VectorConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * pgvector 向量表实体
 * 存储 chunk_id 和对应的 1024 维向量
 *
 * Task 4 - Step 4
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

    @Column(name = "embedding", columnDefinition = "vector(1024)", nullable = false)
    @Convert(converter = VectorConverter.class)
    private float[] embedding;

    @Column(name = "doc_type", length = 32, nullable = false)
    private String docType;

    @Column(name = "route_action", length = 32)
    private String routeAction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
