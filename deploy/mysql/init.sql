-- 数据开发平台数据库初始化脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS data_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE data_platform;

-- 1. 用户表
CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(128) NOT NULL COMMENT '密码(加密)',
  `real_name` VARCHAR(50) COMMENT '真实姓名',
  `email` VARCHAR(100) COMMENT '邮箱',
  `phone` VARCHAR(20) COMMENT '手机号',
  `status` TINYINT DEFAULT 1 COMMENT '状态:0-禁用,1-启用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` VARCHAR(50) COMMENT '创建人',
  `update_by` VARCHAR(50) COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 数据源表
CREATE TABLE `data_source` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `source_code` VARCHAR(50) NOT NULL COMMENT '数据源编码',
  `source_name` VARCHAR(100) NOT NULL COMMENT '数据源名称',
  `source_type` VARCHAR(20) NOT NULL COMMENT '数据源类型:MYSQL,HIVE,ORACLE等',
  `jdbc_url` VARCHAR(500) COMMENT 'JDBC连接URL',
  `username` VARCHAR(100) COMMENT '用户名',
  `password` VARCHAR(200) COMMENT '密码(加密)',
  `description` VARCHAR(500) COMMENT '描述',
  `status` TINYINT DEFAULT 1 COMMENT '状态:0-禁用,1-启用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` VARCHAR(50) COMMENT '创建人',
  `update_by` VARCHAR(50) COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_code` (`source_code`),
  KEY `idx_source_type` (`source_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源表';

-- 3. 作业表
CREATE TABLE `etl_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_code` VARCHAR(100) NOT NULL COMMENT '作业英文名(唯一标识)',
  `job_name` VARCHAR(200) NOT NULL COMMENT '作业中文名',
  `job_type` VARCHAR(20) NOT NULL COMMENT '作业类型:DB2HIVE,HIVE2HIVE',
  `source_datasource_id` BIGINT COMMENT '源数据源ID',
  `source_database` VARCHAR(100) COMMENT '源库名',
  `source_table` VARCHAR(100) COMMENT '源表名',
  `target_database` VARCHAR(100) NOT NULL COMMENT '目标库名',
  `target_table` VARCHAR(100) NOT NULL COMMENT '目标表名',
  `engine_type` VARCHAR(20) NOT NULL COMMENT '执行引擎:HIVE,SPARK',
  `current_version` INT DEFAULT 0 COMMENT '当前版本号',
  `published_version` INT DEFAULT 0 COMMENT '已发布版本号',
  `status` VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态:DRAFT-草稿,PUBLISHED-已发布,ARCHIVED-已归档',
  `description` VARCHAR(1000) COMMENT '描述',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` VARCHAR(50) COMMENT '创建人',
  `update_by` VARCHAR(50) COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_code` (`job_code`),
  KEY `idx_job_type` (`job_type`),
  KEY `idx_status` (`status`),
  KEY `idx_create_by` (`create_by`),
  KEY `fk_source_datasource` (`source_datasource_id`),
  CONSTRAINT `fk_job_datasource` FOREIGN KEY (`source_datasource_id`) REFERENCES `data_source` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ETL作业表';

-- 4. 作业版本表
CREATE TABLE `etl_job_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_id` BIGINT NOT NULL COMMENT '作业ID',
  `version` INT NOT NULL COMMENT '版本号',
  `sql_content` TEXT COMMENT 'SQL映射逻辑',
  `spark_config` TEXT COMMENT 'Spark参数配置(JSON格式)',
  `is_published` TINYINT DEFAULT 0 COMMENT '是否已发布:0-未发布,1-已发布',
  `publish_time` DATETIME COMMENT '发布时间',
  `publish_by` VARCHAR(50) COMMENT '发布人',
  `change_log` VARCHAR(1000) COMMENT '变更日志',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_by` VARCHAR(50) COMMENT '创建人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_version` (`job_id`, `version`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_is_published` (`is_published`),
  CONSTRAINT `fk_version_job` FOREIGN KEY (`job_id`) REFERENCES `etl_job` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ETL作业版本表';

-- 5. 字段元数据表
CREATE TABLE `etl_field_metadata` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `job_version_id` BIGINT NOT NULL COMMENT '作业版本ID',
  `field_name` VARCHAR(100) NOT NULL COMMENT '字段名',
  `field_type` VARCHAR(50) NOT NULL COMMENT '字段类型',
  `field_comment` VARCHAR(500) COMMENT '字段注释',
  `is_partition` TINYINT DEFAULT 0 COMMENT '是否分区字段:0-否,1-是',
  `is_primary` TINYINT DEFAULT 0 COMMENT '是否主键:0-否,1-是',
  `is_nullable` TINYINT DEFAULT 1 COMMENT '是否可为空:0-否,1-是',
  `default_value` VARCHAR(200) COMMENT '默认值',
  `field_order` INT COMMENT '字段顺序',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_version_field` (`job_version_id`, `field_name`),
  KEY `idx_job_version_id` (`job_version_id`),
  CONSTRAINT `fk_field_version` FOREIGN KEY (`job_version_id`) REFERENCES `etl_job_version` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段元数据表';

-- 初始化数据：创建默认管理员账户
-- 密码：admin123 (BCrypt加密后)
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `email`, `status`, `create_by`)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EHsM8lE9lBOsl7.W', '系统管理员', 'admin@example.com', 1, 'system');

-- 初始化数据：常用数据源类型示例
INSERT INTO `data_source` (`source_code`, `source_name`, `source_type`, `jdbc_url`, `username`, `description`, `create_by`)
VALUES
('hive_prod', 'Hive生产环境', 'HIVE', 'jdbc:hive2://localhost:10000/default', 'hive', 'Hive生产环境数据源', 'admin'),
('mysql_source', 'MySQL源数据库', 'MYSQL', 'jdbc:mysql://localhost:3306/source_db', 'root', 'MySQL源数据库', 'admin');
