-- 学习手帐（花期 Blossom）第二期内置迁移脚本
-- 用途：在【已存在的】mysite 库上增量创建手帐子系统的 3 张 sj_ 表。
--      （全新环境由 schema.sql 一次性建好，无需执行本脚本。）
-- 依据：study-journey《网站集成与 MySQL 存储方案 v2.0》§3
-- 执行：mysql -u<user> -p mysite < journal-schema.sql
--      或服务器上：docker exec -i <mysql容器> mysql -uroot -p<pwd> mysite < journal-schema.sql
-- 幂等：全部 CREATE TABLE IF NOT EXISTS，可重复执行。

USE mysite;

CREATE TABLE IF NOT EXISTS `sj_day_record` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID（服务端从登录态解析，前端不传）',
    `date` CHAR(10) NOT NULL COMMENT 'YYYY-MM-DD，用户本地日历日',
    `mood` VARCHAR(32) NULL COMMENT '预设 6 种心情枚举或自定义心情 nanoid，NULL=未记录',
    `diary` TEXT NULL COMMENT 'Markdown 原文',
    `created_at` BIGINT NOT NULL COMMENT '创建时间，Unix 毫秒',
    `updated_at` BIGINT NOT NULL COMMENT '更新时间，Unix 毫秒',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_id`, `date`),
    KEY `idx_user_updated` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='手帐单日记录表';

CREATE TABLE IF NOT EXISTS `sj_learning_item` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `record_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 sj_day_record.id',
    `client_id` VARCHAR(21) NOT NULL COMMENT '前端 nanoid，用于编辑时对齐条目',
    `subject` VARCHAR(64) NOT NULL COMMENT '学科/事项名称',
    `duration_min` INT NOT NULL COMMENT '时长（分钟）',
    `note` VARCHAR(512) NOT NULL DEFAULT '' COMMENT '备注',
    `color` VARCHAR(7) NOT NULL COMMENT '条目颜色 #RRGGBB',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '当日清单内排序',
    PRIMARY KEY (`id`),
    KEY `idx_record` (`record_id`),
    CONSTRAINT `fk_item_record` FOREIGN KEY (`record_id`)
        REFERENCES `sj_day_record` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='手帐学习条目表';

CREATE TABLE IF NOT EXISTS `sj_custom_mood` (
    `id` VARCHAR(21) NOT NULL COMMENT '前端 nanoid，直接作主键',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    `label` VARCHAR(16) NOT NULL COMMENT '心情名称',
    `emoji` VARCHAR(8) NOT NULL DEFAULT '' COMMENT '表情（辅助表达/空状态）',
    `solid` VARCHAR(7) NOT NULL COMMENT '花瓣实色 #RRGGBB',
    `ink` VARCHAR(7) NOT NULL COMMENT '深调色（tint 底上的文字）#RRGGBB',
    `tint` VARCHAR(7) NOT NULL COMMENT '浅底色（大面积铺垫）#RRGGBB',
    `dark_colors` JSON NOT NULL COMMENT '深色模式三档色 {"solid":"#…","ink":"#…","tint":"#…"}',
    `created_at` BIGINT NOT NULL COMMENT '创建时间，Unix 毫秒',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='手帐自定义心情表';

-- ==============================================
-- 学习任务清单（月计划）：大任务 + 清单子任务 + 数量型打卡
-- 进度读时计算，不落 percent。主键 nanoid，硬删除 + 外键级联。
-- ==============================================

CREATE TABLE IF NOT EXISTS `sj_goal` (
    `id` VARCHAR(21) NOT NULL COMMENT '前端 nanoid，直接作主键',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID（服务端从登录态解析）',
    `period` CHAR(7) NOT NULL COMMENT 'YYYY-MM，月计划所属月份',
    `title` VARCHAR(128) NOT NULL COMMENT '目标标题',
    `type` VARCHAR(16) NOT NULL COMMENT 'COUNT 数量型 / CHECKLIST 清单型',
    `target_value` INT NULL COMMENT 'COUNT 目标值，如 300',
    `unit` VARCHAR(16) NULL COMMENT 'COUNT 单位，如 词',
    `color` VARCHAR(7) NOT NULL COMMENT '标记色 #RRGGBB',
    `note` VARCHAR(512) NOT NULL DEFAULT '' COMMENT '月度说明',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '月内排序',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / ARCHIVED',
    `created_at` BIGINT NOT NULL COMMENT '创建时间，Unix 毫秒',
    `updated_at` BIGINT NOT NULL COMMENT '更新时间，Unix 毫秒',
    PRIMARY KEY (`id`),
    KEY `idx_user_period` (`user_id`, `period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='手帐月计划目标表';

CREATE TABLE IF NOT EXISTS `sj_goal_task` (
    `id` VARCHAR(21) NOT NULL COMMENT '前端 nanoid，直接作主键',
    `goal_id` VARCHAR(21) NOT NULL COMMENT '关联 sj_goal.id',
    `title` VARCHAR(128) NOT NULL COMMENT '子任务标题',
    `due_date` CHAR(10) NULL COMMENT '绑定某日 YYYY-MM-DD',
    `week_start` CHAR(10) NULL COMMENT '绑定某周周一 YYYY-MM-DD',
    `done` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否完成',
    `done_at` BIGINT NULL COMMENT '完成时间，Unix 毫秒',
    `duration_min` INT NULL COMMENT '完成时记录的时长（分钟）',
    `reflection` TEXT NULL COMMENT '完成感想',
    `learning_client_id` VARCHAR(21) NULL COMMENT '同步到 sj_learning_item.client_id',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '清单内排序',
    `created_at` BIGINT NOT NULL COMMENT '创建时间，Unix 毫秒',
    `updated_at` BIGINT NOT NULL COMMENT '更新时间，Unix 毫秒',
    PRIMARY KEY (`id`),
    KEY `idx_goal` (`goal_id`),
    KEY `idx_due_date` (`due_date`),
    CONSTRAINT `fk_goal_task_goal` FOREIGN KEY (`goal_id`)
        REFERENCES `sj_goal` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='手帐清单型子任务表';

CREATE TABLE IF NOT EXISTS `sj_goal_checkin` (
    `id` VARCHAR(21) NOT NULL COMMENT '前端 nanoid，直接作主键',
    `goal_id` VARCHAR(21) NOT NULL COMMENT '关联 sj_goal.id',
    `date` CHAR(10) NOT NULL COMMENT '打卡日 YYYY-MM-DD',
    `quantity` INT NOT NULL COMMENT '当日完成数量',
    `duration_min` INT NULL COMMENT '当日学习时长（分钟）',
    `note` VARCHAR(512) NOT NULL DEFAULT '' COMMENT '当日感想',
    `learning_client_id` VARCHAR(21) NULL COMMENT '同步到 sj_learning_item.client_id',
    `created_at` BIGINT NOT NULL COMMENT '创建时间，Unix 毫秒',
    `updated_at` BIGINT NOT NULL COMMENT '更新时间，Unix 毫秒',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_goal_date` (`goal_id`, `date`),
    CONSTRAINT `fk_goal_checkin_goal` FOREIGN KEY (`goal_id`)
        REFERENCES `sj_goal` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='手帐数量型打卡表';
