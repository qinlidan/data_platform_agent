-- 数据库迁移脚本 V1.1
-- 功能：
-- 1. 重命名字段：job_code -> job_name, job_name -> job_cn_name
-- 2. 添加创建人ID、最后发布人、线上版本字段
-- 3. 为 etl_job_version 添加回滚标记和状态字段

USE data_platform;

-- ========== 修改 etl_job 表 ==========

-- 1. 先添加新字段 job_name_new 和 job_cn_name_new (临时字段)
ALTER TABLE `etl_job`
ADD COLUMN `job_name_new` VARCHAR(100) NULL COMMENT '作业名称(新)' AFTER `id`,
ADD COLUMN `job_cn_name_new` VARCHAR(200) NULL COMMENT '作业中文名(新)' AFTER `job_name_new`;

-- 2. 数据迁移：将 job_code -> job_name_new, job_name -> job_cn_name_new
UPDATE `etl_job` SET `job_name_new` = `job_code`, `job_cn_name_new` = `job_name`;

-- 3. 对于现有数据，如果 target_table 为空，则设置为 job_name_new
UPDATE `etl_job` SET `target_table` = `job_name_new` WHERE `target_table` IS NULL OR `target_table` = '';

-- 4. 删除旧字段
ALTER TABLE `etl_job` DROP COLUMN `job_code`;
ALTER TABLE `etl_job` DROP COLUMN `job_name`;

-- 5. 重命名新字段为目标字段名
ALTER TABLE `etl_job` CHANGE COLUMN `job_name_new` `job_name` VARCHAR(100) NOT NULL COMMENT '作业名称(唯一标识)';
ALTER TABLE `etl_job` CHANGE COLUMN `job_cn_name_new` `job_cn_name` VARCHAR(200) NULL COMMENT '作业中文名';

-- 6. 重建唯一索引
ALTER TABLE `etl_job` ADD UNIQUE KEY `uk_job_name` (`job_name`);

-- 7. 添加新字段
ALTER TABLE `etl_job`
ADD COLUMN `create_by_id` BIGINT NULL COMMENT '创建人ID' AFTER `create_by`,
ADD COLUMN `last_publish_by` VARCHAR(50) NULL COMMENT '最后发布人' AFTER `update_by`,
ADD COLUMN `last_publish_by_id` BIGINT NULL COMMENT '最后发布人ID' AFTER `last_publish_by`,
ADD COLUMN `last_publish_time` DATETIME NULL COMMENT '最后发布时间' AFTER `last_publish_by_id`,
ADD COLUMN `online_version` INT NULL COMMENT '线上运行版本号' AFTER `published_version`;

-- 8. 为现有数据设置 online_version = published_version
UPDATE `etl_job` SET `online_version` = `published_version` WHERE `published_version` IS NOT NULL AND `published_version` > 0;

-- 9. 添加索引
ALTER TABLE `etl_job` ADD KEY `idx_create_by_id` (`create_by_id`);
ALTER TABLE `etl_job` ADD KEY `idx_last_publish_by_id` (`last_publish_by_id`);

-- ========== 修改 etl_job_version 表 ==========

-- 1. 添加状态字段
ALTER TABLE `etl_job_version`
ADD COLUMN `status` VARCHAR(20) DEFAULT 'DRAFT' COMMENT '版本状态:DRAFT-草稿,PUBLISHED-已发布,ARCHIVED-已归档' AFTER `is_published`,
ADD COLUMN `is_rollback` TINYINT DEFAULT 0 COMMENT '是否回滚版本:0-否,1-是' AFTER `status`,
ADD COLUMN `rollback_from_version` INT NULL COMMENT '回滚来源版本号' AFTER `is_rollback`;

-- 2. 为现有数据设置状态
UPDATE `etl_job_version` SET `status` = 'PUBLISHED' WHERE `is_published` = 1;
UPDATE `etl_job_version` SET `status` = 'DRAFT' WHERE `is_published` = 0;

-- 3. 添加索引
ALTER TABLE `etl_job_version` ADD KEY `idx_status` (`status`);
ALTER TABLE `etl_job_version` ADD KEY `idx_is_rollback` (`is_rollback`);

-- 完成
SELECT 'Migration V1.1 completed successfully' AS result;
