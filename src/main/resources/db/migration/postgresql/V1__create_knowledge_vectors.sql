-- PostgreSQL knowledge_vectors 表
-- Task 1: 数据库Schema与基础配置
-- 存储向量检索必需字段，支持高性能向量检索

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_vectors (
    chunk_id     BIGINT        PRIMARY KEY,
    embedding    vector(1024)  NOT NULL,
    doc_type     VARCHAR(32)   NOT NULL,
    route_action VARCHAR(32)   NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_doc_type ON knowledge_vectors (doc_type);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_embedding_hnsw ON knowledge_vectors USING hnsw (embedding vector_cosine_ops);

COMMENT ON TABLE knowledge_vectors IS '知识库向量检索表';
COMMENT ON COLUMN knowledge_vectors.chunk_id IS 'chunk ID，对应 MySQL knowledge_chunks.id';
COMMENT ON COLUMN knowledge_vectors.embedding IS '1024维向量';
COMMENT ON COLUMN knowledge_vectors.doc_type IS '文档类型：spark调优|数据研发平台用户手册';
COMMENT ON COLUMN knowledge_vectors.route_action IS '路由动作：tunable | not_tunable | escalate | guide';
