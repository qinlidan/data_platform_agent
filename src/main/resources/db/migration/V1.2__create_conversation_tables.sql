-- V1.2__create_conversation_tables.sql

-- 会话表
CREATE TABLE IF NOT EXISTS conversations (
    id BIGINT PRIMARY KEY COMMENT '会话 ID，雪花算法生成',
    user_id VARCHAR(64) NOT NULL COMMENT '用户标识',
    title VARCHAR(128) NOT NULL DEFAULT '新会话' COMMENT '会话标题',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态：ACTIVE/CLOSED/DELETED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    deleted_at DATETIME NULL COMMENT '软删除时间',

    INDEX idx_user_status (user_id, status, deleted_at),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 会话表';

-- 消息流水表
CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息 ID',
    conversation_id BIGINT NOT NULL COMMENT '所属会话 ID',
    role VARCHAR(20) NOT NULL COMMENT '角色：user/assistant/tool',
    content TEXT NULL COMMENT '消息正文',
    tool_calls JSON NULL COMMENT '工具调用申请（JSON 格式）',
    tool_call_id VARCHAR(64) NULL COMMENT '工具消息对应的申请单 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_conversation_created (conversation_id, created_at),
    CONSTRAINT fk_messages_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES conversations(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 消息流水表';

-- 知识库表
CREATE TABLE IF NOT EXISTS faq (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'FAQ ID',
    question VARCHAR(256) NOT NULL COMMENT '问题',
    answer TEXT NOT NULL COMMENT '答案',
    category VARCHAR(64) NULL COMMENT '分类',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    FULLTEXT INDEX ft_question_answer (question, answer) WITH PARSER ngram,
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='FAQ 知识库';

-- 已用确认令牌表（防止重放攻击）
CREATE TABLE IF NOT EXISTS used_confirmation_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增 ID',
    jti VARCHAR(64) UNIQUE NOT NULL COMMENT 'Token 唯一 ID',
    tool_name VARCHAR(64) NOT NULL COMMENT '绑定的工具名',
    tool_params JSON NOT NULL COMMENT '绑定的参数',
    used_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '使用时间',
    expires_at DATETIME NOT NULL COMMENT '过期时间',

    INDEX idx_jti (jti),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='已用确认令牌表';

-- 插入测试数据
INSERT INTO faq (question, answer, category) VALUES
('作业OOM内存溢出怎么办', '1. 增加作业 JVM 堆内存大小（-Xmx 参数）\n2. 检查是否有内存泄漏（使用 MAT 分析 dump 文件）\n3. 优化 SQL 查询，减少结果集大小\n4. 启用增量同步，避免全量拉取', '作业问题'),
('作业执行很慢怎么排查', '1. 检查源库和目标库网络延迟\n2. 查看数据源是否有慢查询（开启 slow query log）\n3. 检查同步任务并行度配置\n4. 观察 CPU 和磁盘 IO 指标', '作业问题'),
('作业一直不执行是什么原因', '1. 检查 Flink 集群资源是否充足（TaskManager 数量）\n2. 查看作业状态是否为"已启动"（status=RUNNING）\n3. 检查 Flink Web UI 是否有异常日志\n4. 确认调度时间配置是否正确', '作业问题'),
('如何修改数据源连接信息', '在"数据源管理"页面点击"编辑"按钮，修改 URL、用户名、密码等配置，保存后需重启相关作业。', '数据源管理'),
('支持哪些数据库类型', '当前支持 MySQL、PostgreSQL、Oracle、SQL Server、MongoDB、ClickHouse、Kafka 等主流数据源。', '数据源管理');
