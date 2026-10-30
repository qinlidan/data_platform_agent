-- V1.4__create_tool_audit_logs.sql

-- 工具调用审计日志表
CREATE TABLE IF NOT EXISTS tool_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '审计日志 ID',
    conversation_id BIGINT NULL COMMENT '会话 ID（如果来自 AI 对话）',
    tool_name VARCHAR(128) NOT NULL COMMENT '工具名称',
    tool_input TEXT NULL COMMENT '调用参数（JSON）',
    tool_result TEXT NULL COMMENT '返回结果（截断至 1000 字符）',
    status VARCHAR(32) NOT NULL COMMENT '状态：SUCCESS/FAILED/TIMEOUT/VALIDATION_FAILED/PERMISSION_DENIED/NEED_CONFIRMATION',
    error_message VARCHAR(512) NULL COMMENT '错误信息',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    duration BIGINT NULL COMMENT '耗时（毫秒）',
    user_id VARCHAR(64) NULL COMMENT '用户 ID',
    user_roles JSON NULL COMMENT '用户角色列表',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_conversation (conversation_id),
    INDEX idx_user_time (user_id, create_time),
    INDEX idx_tool_status (tool_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具调用审计日志';
