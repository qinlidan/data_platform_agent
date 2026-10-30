-- 数据库迁移脚本 V1.4
-- 功能：将 create_by_id 字段从 BIGINT 改为 VARCHAR，并重命名为 creator_id

USE data_platform;

-- 修改 etl_job 表的 create_by_id 字段
ALTER TABLE `etl_job`
    CHANGE COLUMN `create_by_id` `creator_id` VARCHAR(50) NULL COMMENT '创建人ID（用户ID）';

-- 重建索引（如果旧索引存在则删除）
-- 使用 IF EXISTS 语法（MySQL 5.7.4+）
ALTER TABLE `etl_job` DROP INDEX IF EXISTS `idx_create_by_id`;
ALTER TABLE `etl_job` ADD INDEX `idx_creator_id` (`creator_id`);

-- 完成
SELECT 'Migration V1.4 completed successfully - creator_id field type changed to VARCHAR' AS result;
