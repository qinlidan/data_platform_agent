-- 扩展 conversations 表
ALTER TABLE conversations ADD COLUMN summary TEXT;
ALTER TABLE conversations ADD COLUMN summary_upto_msg_id BIGINT DEFAULT NULL;
ALTER TABLE conversations ADD COLUMN layer1_from_msg_id BIGINT DEFAULT NULL;

-- 创建分段摘要表
CREATE TABLE conversation_summaries (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  seq INT NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_conversation_seq ON conversation_summaries(conversation_id, seq);
