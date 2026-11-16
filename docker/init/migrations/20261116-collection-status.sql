-- ==============================================
-- 迁移脚本：t_collection 增加连载计划与状态字段
-- 日期：2026-11-16
-- 背景：合集支持「计划总篇数 + 连载中/已完结」展示（首页侧栏进度条等），
--       字段为纯手动维护，无 DB 约束（已完结可不设计划、可提前完结）
-- 用法：对已有数据库手动执行（新库由 schema.sql 自动包含该字段）
--   mysql -uroot -p mysite < docker/init/migrations/20261116-collection-status.sql
-- ==============================================

-- 注意：MySQL 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报 Duplicate column 错误，可忽略
ALTER TABLE `t_collection`
    ADD COLUMN `planned_count` INT DEFAULT NULL
    COMMENT '计划总篇数（连载预告，NULL=未设计划）' AFTER `article_count`,
    ADD COLUMN `status` TINYINT NOT NULL DEFAULT 0
    COMMENT '连载状态 0:连载中 1:已完结' AFTER `planned_count`;
