CREATE TABLE low_confidence_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT COMMENT '对话 ID',
    raw_question TEXT NOT NULL COMMENT '原始问题',
    source ENUM('retrieval_low_conf', 'self_check', 'user_feedback') NOT NULL COMMENT '来源',
    reason VARCHAR(500) COMMENT '低置信原因',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_conversation (conversation_id),
    INDEX idx_source (source),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低置信度问题池';
