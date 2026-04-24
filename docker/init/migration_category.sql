-- 分类表结构优化迁移脚本
-- 执行前请备份数据

USE mysite;

-- 添加多级分类支持字段
ALTER TABLE `t_category` 
ADD COLUMN `parent_id` BIGINT DEFAULT NULL COMMENT '父分类ID' AFTER `sort_order`,
ADD COLUMN `level` INT NOT NULL DEFAULT 1 COMMENT '分类层级 1:一级 2:二级 3:三级' AFTER `parent_id`,
ADD COLUMN `path` VARCHAR(500) DEFAULT NULL COMMENT '分类路径（如：1,2,3）' AFTER `level`,
ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0:禁用 1:启用' AFTER `path`,
ADD COLUMN `icon` VARCHAR(100) DEFAULT NULL COMMENT '分类图标' AFTER `status`,
ADD COLUMN `color` VARCHAR(20) DEFAULT NULL COMMENT '分类颜色' AFTER `icon`,
ADD COLUMN `seo_title` VARCHAR(200) DEFAULT NULL COMMENT 'SEO标题' AFTER `color`,
ADD COLUMN `seo_description` VARCHAR(500) DEFAULT NULL COMMENT 'SEO描述' AFTER `seo_title`,
ADD COLUMN `seo_keywords` VARCHAR(200) DEFAULT NULL COMMENT 'SEO关键词' AFTER `seo_description`;

-- 添加索引
ALTER TABLE `t_category`
ADD INDEX `idx_parent_id` (`parent_id`),
ADD INDEX `idx_level` (`level`),
ADD INDEX `idx_status` (`status`);

-- 更新现有数据
UPDATE `t_category` SET 
    `parent_id` = NULL,
    `level` = 1,
    `path` = CAST(`id` AS CHAR),
    `status` = 1
WHERE `parent_id` IS NULL;

-- 添加外键约束（可选，根据实际需求决定）
-- ALTER TABLE `t_category`
-- ADD CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `t_category` (`id`) ON DELETE SET NULL;
