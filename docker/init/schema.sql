-- MySite 数据库表结构脚本
-- 版本：2026-04-27
-- 说明：执行此脚本创建所有数据库表结构

CREATE DATABASE IF NOT EXISTS mysite DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE mysite;

-- ==============================================
-- 1. 用户表 (t_user)
-- ==============================================
CREATE TABLE IF NOT EXISTS `t_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username` VARCHAR(50) NOT NULL COMMENT '昵称',
    `password` VARCHAR(255) NOT NULL COMMENT '密码',
    `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
    `sex` TINYINT DEFAULT 2 COMMENT '性别 0:男 1:女 2:保密',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone_number` VARCHAR(20) NOT NULL COMMENT '手机号',
    `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '用户角色 DEVELOPER:开发者 USER:普通用户',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '用户状态 0:禁用 1:启用',
    `following_count` INT DEFAULT 0 COMMENT '关注人数',
    `follower_count` INT DEFAULT 0 COMMENT '粉丝人数',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_phone_number` (`phone_number`),
    KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ==============================================
-- 2. 分类表 (t_category)
-- ==============================================
CREATE TABLE IF NOT EXISTS `t_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `slug` VARCHAR(50) NOT NULL COMMENT '分类别名（URL友好）',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '分类描述',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父分类ID',
    `level` INT NOT NULL DEFAULT 1 COMMENT '分类层级 1:一级 2:二级 3:三级',
    `path` VARCHAR(500) DEFAULT NULL COMMENT '分类路径（如：1,2,3）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0:禁用 1:启用',
    `icon` VARCHAR(100) DEFAULT NULL COMMENT '分类图标',
    `color` VARCHAR(20) DEFAULT NULL COMMENT '分类颜色',
    `seo_title` VARCHAR(200) DEFAULT NULL COMMENT 'SEO标题',
    `seo_description` VARCHAR(500) DEFAULT NULL COMMENT 'SEO描述',
    `seo_keywords` VARCHAR(200) DEFAULT NULL COMMENT 'SEO关键词',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_slug` (`slug`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_level` (`level`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分类表';

-- ==============================================
-- 3. 标签表 (t_tag)
-- ==============================================
CREATE TABLE IF NOT EXISTS `t_tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
    `slug` VARCHAR(50) NOT NULL COMMENT '标签别名',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_slug` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

-- ==============================================
-- 4. 文章表 (t_article)
-- ==============================================
CREATE TABLE IF NOT EXISTS `t_article` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title` VARCHAR(200) NOT NULL COMMENT '文章标题',
    `content` LONGTEXT NOT NULL COMMENT '文章内容',
    `summary` VARCHAR(500) DEFAULT NULL COMMENT '摘要',
    `cover_image` VARCHAR(500) DEFAULT NULL COMMENT '封面图片URL',
    `category_id` BIGINT DEFAULT NULL COMMENT '分类ID',
    `author_id` BIGINT NOT NULL COMMENT '作者ID',
    `published` TINYINT NOT NULL DEFAULT 0 COMMENT '是否发布 0:草稿 1:已发布',
    `visibility` TINYINT NOT NULL DEFAULT 0 COMMENT '可见性 0:公开 1:仅自己可见',
    `view_count` INT DEFAULT 0 COMMENT '阅读量',
    `favorite_count` INT DEFAULT 0 COMMENT '收藏量',
    `reading_time` INT DEFAULT 0 COMMENT '阅读时间（分钟）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    KEY `idx_author_id` (`author_id`),
    KEY `idx_published` (`published`),
    KEY `idx_visibility` (`visibility`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章表';

-- ==============================================
-- 5. 文章-标签关联表 (t_article_tag)
-- ==============================================
CREATE TABLE IF NOT EXISTS `t_article_tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `article_id` BIGINT NOT NULL COMMENT '文章ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_article_tag` (`article_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章标签关联表';

-- ==============================================
-- 6. 用户关注表 (t_user_follow)
-- ==============================================
CREATE TABLE IF NOT EXISTS `t_user_follow` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `follower_id` BIGINT NOT NULL COMMENT '关注者ID',
    `followee_id` BIGINT NOT NULL COMMENT '被关注者ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_follower_followee` (`follower_id`, `followee_id`),
    KEY `idx_follower_id` (`follower_id`),
    KEY `idx_followee_id` (`followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注表';

-- ==============================================
-- 7. 用户收藏文章表 (t_user_article_favorites)
-- ==============================================
CREATE TABLE IF NOT EXISTS `t_user_article_favorites` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `article_id` BIGINT NOT NULL COMMENT '文章ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_article` (`user_id`, `article_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_article_id` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏文章表';

-- ==============================================
-- 8. 用户操作日志表 (t_user_operation_log)
-- ==============================================
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

-- ==============================================
-- 12. 文章合集表 (t_collection)
-- ==============================================
CREATE TABLE IF NOT EXISTS `t_collection` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title` VARCHAR(200) NOT NULL COMMENT '合集标题',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '合集描述',
    `cover_image` VARCHAR(500) DEFAULT NULL COMMENT '合集封面图片URL',
    `author_id` BIGINT NOT NULL COMMENT '创建者ID',
    `article_count` INT NOT NULL DEFAULT 0 COMMENT '合集内文章数量',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    KEY `idx_author_id` (`author_id`),
    KEY `idx_sort_order` (`sort_order`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章合集表';

-- ==============================================
-- 13. 合集-文章关联表 (t_collection_article)
-- ==============================================
CREATE TABLE IF NOT EXISTS `t_collection_article` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `collection_id` BIGINT NOT NULL COMMENT '合集ID',
    `article_id` BIGINT NOT NULL COMMENT '文章ID',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '在合集内的排序序号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_collection_article` (`collection_id`, `article_id`),
    KEY `idx_collection_id` (`collection_id`),
    KEY `idx_article_id` (`article_id`),
    KEY `idx_collection_sort` (`collection_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合集文章关联表';

SELECT '表结构创建完成' AS message;

-- ==============================================
-- 9. 图片表 (t_image)
-- ==============================================
CREATE TABLE IF NOT EXISTS `t_image` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `stored_name` VARCHAR(255) NOT NULL COMMENT '存储文件名',
    `file_path` VARCHAR(500) NOT NULL COMMENT '文件相对路径',
    `url` VARCHAR(500) NOT NULL COMMENT '访问URL',
    `file_size` BIGINT NOT NULL COMMENT '文件大小(字节)',
    `content_type` VARCHAR(100) NOT NULL COMMENT 'MIME类型',
    `width` INT DEFAULT NULL COMMENT '图片宽度',
    `height` INT DEFAULT NULL COMMENT '图片高度',
    `source_type` TINYINT NOT NULL DEFAULT 0 COMMENT '来源 0:本地上传 1:URL拉取',
    `source_url` VARCHAR(500) DEFAULT NULL COMMENT '原始URL(source_type=1时)',
    `uploader_id` BIGINT NOT NULL COMMENT '上传者ID',
    `article_count` INT NOT NULL DEFAULT 0 COMMENT '引用文章数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    KEY `idx_uploader_id` (`uploader_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_source_type` (`source_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图片表';

-- ==============================================
-- 10. 评论表 (t_comment)
-- ==============================================
CREATE TABLE IF NOT EXISTS `t_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `article_id` BIGINT NOT NULL COMMENT '文章ID',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父评论ID（NULL=顶级评论）',
    `root_id` BIGINT DEFAULT NULL COMMENT '根评论ID（便于查询整个评论树）',
    `user_id` BIGINT DEFAULT NULL COMMENT '登录用户ID（NULL=游客）',
    `nickname` VARCHAR(50) NOT NULL COMMENT '昵称',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱（用于Gravatar头像）',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `content` TEXT NOT NULL COMMENT '评论内容',
    `ip_address` VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
    `user_agent` VARCHAR(500) DEFAULT NULL COMMENT '浏览器UA',
    `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `reply_count` INT NOT NULL DEFAULT 0 COMMENT '回复数量',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0:待审核 1:已通过 2:已拒绝/垃圾',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    KEY `idx_article_id` (`article_id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_root_id` (`root_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- ==============================================
-- 11. 评论点赞表 (t_comment_like)
-- ==============================================
CREATE TABLE IF NOT EXISTS `t_comment_like` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `comment_id` BIGINT NOT NULL COMMENT '评论ID',
    `user_id` BIGINT DEFAULT NULL COMMENT '点赞用户ID',
    `ip_address` VARCHAR(50) DEFAULT NULL COMMENT '游客IP',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_comment_user` (`comment_id`, `user_id`),
    UNIQUE KEY `uk_comment_ip` (`comment_id`, `ip_address`),
    KEY `idx_comment_id` (`comment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论点赞表';
