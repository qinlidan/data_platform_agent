-- conversations 表
CREATE TABLE conversations (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id VARCHAR(64) NOT NULL COMMENT '用户标识',
  status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '会话状态：active-进行中, transferred-已转人工, ended-已结束',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_status (status),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话会话表';

-- messages 表
CREATE TABLE messages (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  conversation_id BIGINT UNSIGNED NOT NULL COMMENT '会话ID',
  role VARCHAR(20) NOT NULL COMMENT '角色：user, assistant, tool',
  content TEXT COMMENT '消息内容（assistant纯工具调用时可为空）',
  tool_calls JSON COMMENT 'assistant消息带的工具调用申请（OpenAI格式）',
  tool_call_id VARCHAR(64) COMMENT 'tool消息对应的申请单ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_conversation_id (conversation_id),
  KEY idx_role (role),
  KEY idx_tool_call_id (tool_call_id),
  KEY idx_created_at (created_at),
  CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话消息表';

-- faq 表
CREATE TABLE faq (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  question VARCHAR(255) NOT NULL COMMENT '问题',
  answer TEXT NOT NULL COMMENT '答案',
  category VARCHAR(64) NOT NULL COMMENT '分类：作业报错、作业执行变慢、作业未执行',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_category (category),
  FULLTEXT KEY idx_question_answer (question, answer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常见问题知识库';

-- used_confirmation_tokens 表
CREATE TABLE used_confirmation_tokens (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  jti VARCHAR(64) NOT NULL UNIQUE COMMENT 'Token 唯一标识',
  tool_name VARCHAR(64) NOT NULL COMMENT '绑定的工具名',
  tool_params JSON NOT NULL COMMENT '绑定的参数',
  used_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '使用时间',
  expires_at DATETIME NOT NULL COMMENT '过期时间',
  PRIMARY KEY (id),
  KEY idx_jti (jti),
  KEY idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已用确认令牌';
INSERT INTO faq (question, answer, category) VALUES
('Spark 任务 OOM 内存溢出---map端OOM怎么办？', 'map 端 OOM：增加 spark.executor.memory 和 spark.executor.memoryOverhead；reduce 端 OOM：增加 spark.sql.shuffle.partitions 减少单分区数据量', '作业报错'),
('DB2 源库读取很慢', '检查：1. 是否有索引；2. 是否全表扫描；3. 网络带宽是否足够；4. 调整 fetchSize 参数；5. 使用增量读取而非全量', '作业执行变慢'),
('作业一直不执行', '检查：1. 调度状态是否启用；2. Cron 表达式是否正确；3. 依赖的上游作业是否完成；4. 资源队列是否有空闲资源', '作业未执行'),
('Hive 表分区过多导致元数据查询慢', '方案：1. 合并小分区；2. 使用动态分区裁剪；3. 升级 Hive MetaStore 版本；4. 调整 hive.exec.max.dynamic.partitions', '作业执行变慢'),
('作业日志提示权限不足', '检查：1. 数据源用户权限（GRANT SELECT）；2. HDFS 目录权限（chmod/chown）；3. Kerberos 认证是否过期', '作业报错');
