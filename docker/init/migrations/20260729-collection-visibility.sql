-- ==============================================
-- 迁移脚本：t_collection 增加可见性字段
-- 日期：2026-07-29
-- 背景：合集支持手动可见性管理（0:公开 1:私有），与文章 visibility 语义一致
-- 用法：对已有数据库手动执行（新库由 schema.sql 自动包含该字段）
--   mysql -uroot -p mysite < docker/init/migrations/20260729-collection-visibility.sql
-- ==============================================

-- 注意：MySQL 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报 Duplicate column 错误，可忽略
ALTER TABLE `t_collection`
    ADD COLUMN `visibility` TINYINT NOT NULL DEFAULT 0
    COMMENT '可见性 0:公开 1:私有(仅作者和管理员可见)' AFTER `article_count`;
