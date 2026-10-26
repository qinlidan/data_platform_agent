-- ========================================
-- V1.3 修正会话表结构
-- ========================================
-- 目标：
-- 1. 为 conversations 表添加 title 和 deleted_at 字段
-- 2. 优化索引结构（复合索引）
-- 3. 修改 status 字段注释，明确枚举值

-- ========================================
-- 1. 修改 conversations 表
-- ========================================

-- 添加 title 字段（会话标题）
ALTER TABLE conversations
ADD COLUMN title VARCHAR(128) NOT NULL DEFAULT '新会话' COMMENT '会话标题' AFTER user_id;

-- 添加 deleted_at 字段（软删除时间）
ALTER TABLE conversations
ADD COLUMN deleted_at DATETIME NULL COMMENT '软删除时间（软删除标记）' AFTER updated_at;

-- 修改 status 字段注释，明确枚举值
ALTER TABLE conversations
MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态：ACTIVE-进行中, CLOSED-已结束, DELETED-已删除';

-- ========================================
-- 2. 优化索引结构
-- ========================================

-- 删除单列索引
DROP INDEX idx_user_id ON conversations;
DROP INDEX idx_status ON conversations;

-- 创建复合索引（user_id + status + deleted_at）
-- 用于快速查询用户的活跃会话列表（deleted_at IS NULL AND status = 'ACTIVE'）
CREATE INDEX idx_user_status ON conversations(user_id, status, deleted_at);

-- 保留 created_at 索引（用于按创建时间排序）
-- idx_created_at 已存在，无需修改

-- ========================================
-- 3. 优化 messages 表索引
-- ========================================

-- 删除低效的单列索引
DROP INDEX idx_role ON messages;
DROP INDEX idx_tool_call_id ON messages;

-- 创建复合索引（conversation_id + created_at）
-- 用于快速查询会话的历史消息（按时间正序）
DROP INDEX idx_conversation_id ON messages;
CREATE INDEX idx_conversation_created ON messages(conversation_id, created_at);

-- ========================================
-- 4. 数据迁移（将现有数据的 status 统一为枚举值）
-- ========================================

-- 将旧的 'active' 改为 'ACTIVE'
UPDATE conversations SET status = 'ACTIVE' WHERE status = 'active';

-- 将旧的 'ended' 改为 'CLOSED'
UPDATE conversations SET status = 'CLOSED' WHERE status = 'ended';

-- 将旧的 'transferred' 保留（可选，如果后续需要人工转接功能）
-- 如果不需要，可改为 CLOSED
UPDATE conversations SET status = 'CLOSED' WHERE status = 'transferred';

-- ========================================
-- 说明：
-- 1. 主键 id 保持 AUTO_INCREMENT（后续通过应用层使用雪花 ID）
-- 2. deleted_at 为 NULL 表示未删除，有值表示已删除
-- 3. 复合索引 idx_user_status 覆盖最常见的查询场景：
--    WHERE user_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
-- 4. 索引 idx_conversation_created 优化消息历史查询：
--    WHERE conversation_id = ? ORDER BY created_at ASC
-- ========================================
