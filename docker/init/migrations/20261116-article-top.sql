-- ==============================================
-- 迁移脚本：t_article 增加置顶字段
-- 日期：2026-11-16
-- 背景：UI 重构 T14 文章置顶——首页头条优先取置顶（多篇按 top_time 倒序）
-- 用法：对已有数据库手动执行（新库由 schema.sql 自动包含该字段）
--   mysql -uroot -p mysite < docker/init/migrations/20261116-article-top.sql
-- ==============================================

-- 注意：MySQL 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报 Duplicate column 错误，可忽略
ALTER TABLE `t_article`
    ADD COLUMN `is_top` TINYINT NOT NULL DEFAULT 0
        COMMENT '置顶 0:否 1:是' AFTER `visibility`,
    ADD COLUMN `top_time` DATETIME NULL
        COMMENT '置顶时间，多篇置顶按此倒序' AFTER `is_top`;
