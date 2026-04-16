-- MySite 数据库初始化脚本
-- 创建数据库
CREATE DATABASE IF NOT EXISTS mysite DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE mysite;

-- 用户表
CREATE TABLE IF NOT EXISTS `t_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username` VARCHAR(50) NOT NULL COMMENT '昵称',
    `password` VARCHAR(255) NOT NULL COMMENT '密码',
    `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
    `sex` TINYINT DEFAULT 2 COMMENT '性别 0:男 1:女 2:保密',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone_number` VARCHAR(20) NOT NULL COMMENT '手机号',
    `following_count` INT DEFAULT 0 COMMENT '关注人数',
    `follower_count` INT DEFAULT 0 COMMENT '粉丝人数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_phone_number` (`phone_number`),
    KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 文章表
CREATE TABLE IF NOT EXISTS `t_article` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title` VARCHAR(200) NOT NULL COMMENT '文章标题',
    `content` LONGTEXT NOT NULL COMMENT '文章内容',
    `summary` VARCHAR(500) DEFAULT NULL COMMENT '摘要',
    `author_id` BIGINT NOT NULL COMMENT '作者ID',
    `published` TINYINT NOT NULL DEFAULT 0 COMMENT '是否发布 0:草稿 1:已发布',
    `view_count` INT DEFAULT 0 COMMENT '阅读量',
    `favorite_count` INT DEFAULT 0 COMMENT '收藏量',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识 0:未删除 1:已删除',
    PRIMARY KEY (`id`),
    KEY `idx_author_id` (`author_id`),
    KEY `idx_published` (`published`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章表';

-- 用户关注表
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

-- 用户收藏文章表
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

-- 插入默认管理员用户 (密码: admin123，实际使用时请修改)
-- 密码使用 BCrypt 加密，这里是一个示例加密后的密码
INSERT INTO `t_user` (`username`, `password`, `real_name`, `sex`, `email`, `phone_number`, `following_count`, `follower_count`)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '管理员', 0, 'admin@mysite.com', '13800138000', 0, 0)
ON DUPLICATE KEY UPDATE `username` = `username`;
