-- 用户角色和状态字段迁移脚本
-- 为 t_user 表添加 role 和 status 字段

USE mysite;

-- 添加角色字段（如果不存在）
SET @col_exists = 0;
SELECT 1 INTO @col_exists FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'mysite' AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'role';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `t_user` ADD COLUMN `role` VARCHAR(20) NOT NULL DEFAULT ''USER'' COMMENT ''用户角色 DEVELOPER:开发者 USER:普通用户'' AFTER `phone_number`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加状态字段（如果不存在）
SET @col_exists = 0;
SELECT 1 INTO @col_exists FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'mysite' AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'status';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `t_user` ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT ''用户状态 0:禁用 1:启用'' AFTER `role`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 将现有 admin 用户升级为开发者角色
UPDATE `t_user` SET `role` = 'DEVELOPER' WHERE `username` = 'admin';

-- 创建操作日志表（如果不存在）
CREATE TABLE IF NOT EXISTS `t_user_operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `operator_id` BIGINT DEFAULT NULL COMMENT '操作者ID',
    `operator_name` VARCHAR(50) DEFAULT NULL COMMENT '操作者用户名',
    `target_user_id` BIGINT DEFAULT NULL COMMENT '目标用户ID',
    `target_user_name` VARCHAR(50) DEFAULT NULL COMMENT '目标用户名',
    `operation_type` VARCHAR(30) NOT NULL COMMENT '操作类型 ROLE_CHANGE/STATUS_CHANGE/DELETE',
    `detail` VARCHAR(500) DEFAULT NULL COMMENT '操作详情',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_operator_id` (`operator_id`),
    KEY `idx_target_user_id` (`target_user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户操作日志表';
