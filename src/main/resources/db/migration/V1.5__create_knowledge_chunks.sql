-- MySQL knowledge_chunks 表
-- Task 1: 数据库Schema与基础配置
-- 存储知识库文档切分后的块，作为原文权威源

CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键，chunk_id',
    doc_type           VARCHAR(32)     NOT NULL COMMENT '文档类型：spark调优|数据研发平台用户手册',
    doc_id             VARCHAR(128)    NOT NULL COMMENT '文件名（不含扩展名）',
    category           VARCHAR(255)    NOT NULL COMMENT '一级标题',
    scene_type         VARCHAR(32)     NOT NULL COMMENT '二级标题类型枚举：error|slow|pending|boundary|guide|concept|faq',
    scene_title        VARCHAR(64)     NOT NULL COMMENT '二级标题中文名',
    symptom_text       TEXT            NULL COMMENT '现象描述（可空）',
    questions          TEXT            NOT NULL COMMENT '三级标题或问法',
    answer             TEXT            NOT NULL COMMENT '回答正文',
    section_path       VARCHAR(512)    NULL COMMENT '章节路径',
    content_type       VARCHAR(32)     NULL COMMENT 'faq | policy | manual',
    is_tunable         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否可调优：0-否，1-是',
    route_action       VARCHAR(32)     NULL COMMENT 'tunable | not_tunable | escalate | guide',
    prev_chunk_id      BIGINT UNSIGNED NULL COMMENT '前一个chunk的ID',
    next_chunk_id      BIGINT UNSIGNED NULL COMMENT '后一个chunk的ID',
    vectorize_status   ENUM('pending_vectorization','vectorized') NOT NULL DEFAULT 'pending_vectorization' COMMENT '向量化状态',
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    KEY idx_doc_type (doc_type),
    KEY idx_doc_id (doc_id),
    KEY idx_scene_type (doc_type, scene_type),
    KEY idx_vectorize_status (vectorize_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库切块原文表';
