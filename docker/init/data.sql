-- MySite 初始化数据脚本
-- 版本：2026-04-27
-- 说明：执行 schema.sql 后执行此脚本插入初始化数据

USE mysite;

-- ==============================================
-- 1. 管理员用户 (密码: 000000)
-- ==============================================
INSERT INTO `t_user` (`username`, `password`, `real_name`, `sex`, `email`, `phone_number`, `role`, `status`, `following_count`, `follower_count`)
VALUES ('admin', '$2a$10$axBR06YqTi43c/ORWaL7KeqfIPLKa6gZsZ2.LCFtTkbZg6vtj/dDi', '管理员', 0, 'admin@mysite.com', '13800138000', 'DEVELOPER', 1, 0, 0)
ON DUPLICATE KEY UPDATE `username` = `username`;

-- ==============================================
-- 2. 示例普通用户 (密码: test123)
-- ==============================================
INSERT INTO `t_user` (`username`, `password`, `real_name`, `sex`, `email`, `phone_number`, `role`, `status`, `following_count`, `follower_count`)
VALUES ('testuser', '$2a$10$X5wFutsrWdLZmL7LLR4.3OqaLqWk4N5KkN8h7YqW3E5X6Z7A8B9C0', '测试用户', 1, 'test@mysite.com', '13900139000', 'USER', 1, 5, 10)
ON DUPLICATE KEY UPDATE `username` = `username`;

-- ==============================================
-- 3. 分类数据
-- ==============================================
INSERT INTO `t_category` (`name`, `slug`, `description`, `sort_order`, `parent_id`, `level`, `path`, `status`, `icon`, `color`, `seo_title`, `seo_description`, `seo_keywords`)
VALUES
    ('技术', 'tech', '技术相关文章', 1, NULL, 1, '1', 1, 'code', '#3b82f6', '技术文章', '分享技术经验和教程', '技术,编程,开发'),
    ('生活', 'life', '生活感悟分享', 2, NULL, 1, '2', 1, 'heart', '#10b981', '生活文章', '记录生活点滴', '生活,感悟,日常'),
    ('前端', 'frontend', '前端技术文章', 1, 1, 2, '1,3', 1, 'monitor', '#8b5cf6', '前端开发', '前端开发技术文章', '前端,JavaScript,Vue'),
    ('后端', 'backend', '后端技术文章', 2, 1, 2, '1,4', 1, 'server', '#f59e0b', '后端开发', '后端开发技术文章', '后端,Java,Spring'),
    ('数据库', 'database', '数据库相关文章', 3, 1, 2, '1,5', 1, 'database', '#ef4444', '数据库', '数据库技术文章', '数据库,MySQL,Redis'),
    ('旅行', 'travel', '旅行见闻分享', 3, NULL, 1, '6', 1, 'plane', '#06b6d4', '旅行日记', '旅行见闻和攻略', '旅行,攻略,见闻'),
    ('美食', 'food', '美食探店分享', 4, NULL, 1, '7', 1, 'utensils', '#f97316', '美食分享', '美食探店和推荐', '美食,餐厅,探店'),
    ('读书', 'reading', '读书笔记和书评', 5, NULL, 1, '8', 1, 'book', '#84cc16', '读书笔记', '读书笔记和书评', '读书,书评,笔记'),
    ('未分类', 'uncategorized', '默认分类', 0, NULL, 1, '9', 1, NULL, NULL, NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE `name` = `name`;

-- ==============================================
-- 4. 标签数据
-- ==============================================
INSERT INTO `t_tag` (`name`, `slug`)
VALUES
    ('JavaScript', 'javascript'),
    ('TypeScript', 'typescript'),
    ('Vue', 'vue'),
    ('React', 'react'),
    ('Node.js', 'nodejs'),
    ('Java', 'java'),
    ('Spring Boot', 'spring-boot'),
    ('Python', 'python'),
    ('Docker', 'docker'),
    ('Kubernetes', 'kubernetes'),
    ('MySQL', 'mysql'),
    ('Redis', 'redis'),
    ('MongoDB', 'mongodb'),
    ('Git', 'git'),
    ('Linux', 'linux'),
    ('算法', 'algorithm'),
    ('架构设计', 'architecture'),
    ('性能优化', 'performance'),
    ('工具推荐', 'tools'),
    ('经验分享', 'experience')
ON DUPLICATE KEY UPDATE `name` = `name`;

-- ==============================================
-- 5. 示例文章数据
-- ==============================================
INSERT INTO `t_article` (`title`, `content`, `summary`, `cover_image`, `category_id`, `author_id`, `published`, `view_count`, `favorite_count`, `reading_time`, `del_flag`)
VALUES
    ('欢迎使用 MySite 博客系统', '## 欢迎\n\n这是一篇关于 MySite 博客系统的介绍文章...\n\n## 功能特点\n\n- 支持 Markdown 编写\n- 分类和标签管理\n- 评论系统\n- 响应式设计', '这是一篇关于 MySite 博客系统的介绍文章，涵盖了系统的基本功能和使用方法。', 'https://picsum.photos/800/400?random=1', 1, 1, 1, 100, 5, 1, 0),
    ('Vue 3 Composition API 实战指南', '## 前言\n\nVue 3 引入了全新的 Composition API，让代码组织更加灵活...\n\n## setup 函数\n\nsetup 是组合式 API 的入口点...\n\n## ref 和 reactive\n\n这两个函数用于创建响应式数据...', '本文详细介绍 Vue 3 Composition API 的使用方法，包括 setup、ref、reactive 等核心概念。', 'https://picsum.photos/800/400?random=2', 3, 1, 1, 250, 15, 1, 0),
    ('Spring Boot 最佳实践', '## 为什么要最佳实践\n\n良好的实践可以提高代码质量和可维护性...\n\n## 项目结构\n\n推荐采用领域驱动设计的分层结构...\n\n## 配置管理\n\n使用 application.yml 进行配置管理...', '分享 Spring Boot 开发中的最佳实践，包括项目结构、配置管理、异常处理等方面。', 'https://picsum.photos/800/400?random=3', 4, 1, 1, 180, 12, 1, 0),
    ('MySQL 性能优化实战', '## 索引优化\n\n索引是提高查询性能的关键...\n\n## 慢查询分析\n\n使用 EXPLAIN 分析查询执行计划...\n\n## 分库分表\n\n当单表数据量过大时，考虑分库分表...', '本文分享 MySQL 性能优化的实战经验，包括索引优化、慢查询分析、分库分表等策略。', 'https://picsum.photos/800/400?random=4', 5, 1, 1, 150, 8, 1, 0),
    ('北京三日游攻略', '## 行程概览\n\nDay 1: 天安门 - 故宫 - 王府井\n\nDay 2: 长城 - 鸟巢\n\nDay 3: 颐和园 - 圆明园\n\n## 注意事项\n\n提前预约门票，建议住在地铁附近...', '分享一次北京三日游的完整攻略，包括景点推荐、美食推荐和出行建议。', 'https://picsum.photos/800/400?random=5', 6, 1, 1, 300, 25, 1, 0),
    ('成都美食探店合集', '## 火锅推荐\n\n- 小龙坎火锅\n- 大龙燚火锅\n\n## 小吃推荐\n\n- 串串香\n- 担担面\n- 钟水饺\n\n## 注意事项\n\n成都美食偏辣，提前准备好肠胃...', '整理了一份成都美食探店合集，涵盖了火锅、小吃、甜品等多种类型。', 'https://picsum.photos/800/400?random=6', 7, 1, 1, 200, 20, 1, 0),
    ('《百年孤独》读书笔记', '## 作品简介\n\n《百年孤独》是哥伦比亚作家加西亚·马尔克斯的代表作...\n\n## 主要人物\n\n- 何塞·阿尔卡蒂奥·布恩迪亚\n- 乌尔苏拉·伊格纳西娅\n\n## 经典语录\n\n> 多年以后，面对行刑队，奥雷里亚诺·布恩迪亚上校将会回想起父亲带他去见识冰块的那个遥远的下午。', '分享《百年孤独》的读书笔记和感悟，解读这部魔幻现实主义代表作。', 'https://picsum.photos/800/400?random=7', 8, 1, 1, 120, 10, 1, 0),
    ('Docker 容器化部署指南', '## 为什么使用 Docker\n\nDocker 可以实现环境一致性、隔离性...\n\n## 常用命令\n\n```bash\ndocker build -t myapp .\ndocker run -d -p 8080:8080 myapp\n```\n\n## Docker Compose\n\n使用 Docker Compose 管理多容器应用...', '详细介绍 Docker 的使用方法，包括镜像构建、容器运行、Docker Compose 等内容。', 'https://picsum.photos/800/400?random=8', 1, 1, 1, 220, 18, 1, 0)
ON DUPLICATE KEY UPDATE `title` = `title`;

-- ==============================================
-- 6. 文章-标签关联数据
-- ==============================================
INSERT INTO `t_article_tag` (`article_id`, `tag_id`)
VALUES
    (1, 20),  -- 欢迎使用 MySite -> 经验分享
    (2, 1),   -- Vue 3 -> JavaScript
    (2, 3),   -- Vue 3 -> Vue
    (2, 2),   -- Vue 3 -> TypeScript
    (3, 6),   -- Spring Boot -> Java
    (3, 7),   -- Spring Boot -> Spring Boot
    (3, 18),  -- Spring Boot -> 性能优化
    (4, 11),  -- MySQL -> MySQL
    (4, 18),  -- MySQL -> 性能优化
    (5, 20),  -- 北京游 -> 经验分享
    (6, 20),  -- 成都美食 -> 经验分享
    (7, 20),  -- 百年孤独 -> 经验分享
    (8, 9),   -- Docker -> Docker
    (8, 14)   -- Docker -> Git
ON DUPLICATE KEY UPDATE `article_id` = `article_id`;

ALTER TABLE `t_user` ADD COLUMN IF NOT EXISTS `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL' AFTER `follower_count`;

SELECT '初始化数据插入完成' AS message;
