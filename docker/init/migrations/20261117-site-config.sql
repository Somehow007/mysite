-- ==============================================
-- 迁移脚本：t_site_config 站点配置单行表（后台可改）
-- 日期：2026-11-17
-- 背景：站点信息原先只能走 env/yaml（SITE_*），现支持后台 PUT /v1/site/config
--       持久化到 DB；DB 字段非空即覆盖 env 默认，NULL=继承 env/yaml 默认值。
--       navigation / ask-examples / ask-demo 结构性配置仍走 yaml，不在此表。
-- 用法：对已有数据库手动执行（新库由 schema.sql 自动包含该表）
--   mysql -uroot -p mysite < docker/init/migrations/20261117-site-config.sql
-- ==============================================

CREATE TABLE IF NOT EXISTS `t_site_config` (
    `id`               BIGINT       NOT NULL COMMENT '固定为 1 的单行配置',
    `title`            VARCHAR(128) DEFAULT NULL COMMENT '站点标题，NULL=继承 env 默认',
    `subtitle`         VARCHAR(255) DEFAULT NULL COMMENT '站点副标题',
    `description`      VARCHAR(512) DEFAULT NULL COMMENT '站点简介（meta description / og:description）',
    `author`           VARCHAR(64)  DEFAULT NULL COMMENT '作者署名',
    `author_bio`       TEXT         DEFAULT NULL COMMENT '作者简介',
    `author_tagline`   VARCHAR(255) DEFAULT NULL COMMENT '作者一句话签名',
    `about_content`    MEDIUMTEXT   DEFAULT NULL COMMENT '关于页长文（markdown，支持多行）',
    `tech_stack`       VARCHAR(255) DEFAULT NULL COMMENT '技术栈一句话',
    `avatar`           VARCHAR(512) DEFAULT NULL COMMENT '站点/作者头像 URL',
    `url`              VARCHAR(255) DEFAULT NULL COMMENT '站点绝对地址（canonical / og:url / JSON-LD）',
    `github`           VARCHAR(255) DEFAULT NULL COMMENT 'GitHub 主页',
    `rss`              VARCHAR(255) DEFAULT NULL COMMENT 'RSS 地址',
    `comment_enabled`  TINYINT(1)   DEFAULT NULL COMMENT '评论开关，NULL=继承 env 默认',
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag`         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站点配置（单行，后台可改；NULL 字段继承 env/yaml 默认）';
