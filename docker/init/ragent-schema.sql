-- ============================================================
-- RAG 模块数据库初始化（PostgreSQL + pgvector）
-- 通过 docker-compose 的 docker-entrypoint-initdb.d 自动执行
-- ============================================================

-- pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 知识库
CREATE TABLE IF NOT EXISTS t_knowledge_base (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    collection_name VARCHAR(100) NOT NULL UNIQUE,
    embedding_model VARCHAR(100) DEFAULT 'text-embedding-v4',
    embedding_dimension INT DEFAULT 1024,
    chunk_size INT DEFAULT 800,
    chunk_overlap INT DEFAULT 100,
    chunking_mode VARCHAR(30) DEFAULT 'MARKDOWN_HEADING',
    enabled BOOLEAN DEFAULT true,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 文档
CREATE TABLE IF NOT EXISTS t_knowledge_document (
    id BIGINT PRIMARY KEY,
    kb_id BIGINT NOT NULL REFERENCES t_knowledge_base(id),
    title VARCHAR(500) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    source_ref VARCHAR(500),
    file_type VARCHAR(20),
    status VARCHAR(20) DEFAULT 'PENDING',   -- PENDING/CHUNKING/READY/FAILED
    fail_reason TEXT,                        -- 摄取失败原因（embedding API 挂了就记录在这里）
    chunk_count INT DEFAULT 0,
    char_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 同一来源只对应一份文档，防止并发的创建/更新事件插入重复记录
    CONSTRAINT uk_doc_source UNIQUE (kb_id, source_type, source_ref)
);

-- 分块
CREATE TABLE IF NOT EXISTS t_knowledge_chunk (
    id BIGINT PRIMARY KEY,
    doc_id BIGINT NOT NULL REFERENCES t_knowledge_document(id),
    kb_id BIGINT NOT NULL REFERENCES t_knowledge_base(id),
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding_text TEXT,    -- 向量化专用文本，NULL 时回退到 content（Ragent 模式）
    char_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 向量（pgvector）
CREATE TABLE IF NOT EXISTS t_knowledge_vector (
    id BIGINT PRIMARY KEY,
    chunk_id BIGINT NOT NULL REFERENCES t_knowledge_chunk(id),
    kb_id BIGINT NOT NULL REFERENCES t_knowledge_base(id),
    embedding vector(1024),
    model VARCHAR(100),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- HNSW 索引（cosine 距离）
CREATE INDEX IF NOT EXISTS idx_vector_embedding ON t_knowledge_vector
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- 对话会话
CREATE TABLE IF NOT EXISTS t_conversation (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,                          -- 可空：匿名聊天不强制登录（见 3.5 节决策 3）
    visitor_id VARCHAR(64),                  -- 匿名访客标识（前端 localStorage UUID），防 IDOR
    title VARCHAR(200),
    message_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    -- 注意：PG 没有 MySQL 的 ON UPDATE CURRENT_TIMESTAMP，
    -- update_time 由应用层（MyBatis-Plus 填充或 service 显式 set）维护
);

-- 对话消息
CREATE TABLE IF NOT EXISTS t_conversation_message (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES t_conversation(id),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    sources JSONB,
    token_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 辅助索引
CREATE INDEX IF NOT EXISTS idx_chunk_doc_id ON t_knowledge_chunk(doc_id);
CREATE INDEX IF NOT EXISTS idx_chunk_kb_id ON t_knowledge_chunk(kb_id);
CREATE INDEX IF NOT EXISTS idx_vector_chunk_id ON t_knowledge_vector(chunk_id);
CREATE INDEX IF NOT EXISTS idx_vector_kb_id ON t_knowledge_vector(kb_id);
CREATE INDEX IF NOT EXISTS idx_conv_user_id ON t_conversation(user_id);
CREATE INDEX IF NOT EXISTS idx_conv_visitor_id ON t_conversation(visitor_id);
CREATE INDEX IF NOT EXISTS idx_conv_msg_conv_id ON t_conversation_message(conversation_id);

-- ============================================================
-- Phase 6：意图识别与智能路由
-- ============================================================

-- 意图定义表（博客规模，扁平列表，不需要意图树）
CREATE TABLE IF NOT EXISTS t_rag_intent (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,           -- 意图名称，如 "技术博客检索"、"读书笔记检索"
    type VARCHAR(20) NOT NULL DEFAULT 'KB_RETRIEVAL',  -- KB_RETRIEVAL / CHAT
    kb_id BIGINT,                          -- 绑定的知识库（CHAT 类型为 NULL）
    keywords TEXT,                         -- 触发关键词，JSON 数组：["Java","Spring","JWT"]
    description TEXT,                      -- 意图描述，给 LLM 分类用的提示
    priority INT DEFAULT 0,               -- 优先级，数值越大优先级越高
    enabled BOOLEAN DEFAULT true,
    custom_prompt_fragment TEXT,           -- 自定义 Prompt 片段（追加到 system prompt）
    custom_top_k INT,                      -- 该意图专用 topK（覆盖全局配置，NULL=使用默认值）
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引：按启用状态 + 类型查询（IntentRepository.listEnabled）
CREATE INDEX IF NOT EXISTS idx_intent_enabled_type ON t_rag_intent(enabled, type);
-- 索引：按知识库查询
CREATE INDEX IF NOT EXISTS idx_intent_kb_id ON t_rag_intent(kb_id);

-- 种子数据：四类初始意图（覆盖 KB 检索 + 闲聊）
-- 使用 ON CONFLICT DO NOTHING 保证重复执行幂等
INSERT INTO t_rag_intent (id, name, type, kb_id, keywords, description, priority, enabled, custom_prompt_fragment, custom_top_k, create_time)
VALUES
(1, '技术博客检索', 'KB_RETRIEVAL', 1,
 '["Java","Spring","JWT","Redis","Docker","MySQL","Vue","TypeScript","后端","前端","数据库","安全","部署","Nginx","Linux","Git","API","微服务"]',
 '用户询问后端开发、Spring Boot、数据库、前端框架、系统部署等技术问题', 10, true,
 '你是博客技术文章助手的补充：回答要准确，代码示例注明版本和来源文章。', NULL, NOW()),
(2, '读书笔记检索', 'KB_RETRIEVAL', 2,
 '["读书","书籍","推荐","读后感","学习路线","入门","书单","阅读","好书"]',
 '用户询问书籍推荐、读书心得、学习路径等', 5, true,
 '你是博客读书笔记助手的补充：推荐书籍时说明理由，可以结合技术博客内容给出学习路径建议。', 5, NOW()),
(3, '学习笔记检索', 'KB_RETRIEVAL', 3,
 '["笔记","学习","总结","复习","知识点","面试","教程","整理","备忘","踩坑","实践","笔记整理","知识点总结"]',
 '用户询问学习笔记、知识点总结、面试准备、技术教程、实践踩坑等', 8, true,
 '你是博客学习笔记助手的补充：回答要结构化，给出清晰的知识点梳理和学习路径建议，区分"已掌握"和"待深入"的内容。', 3, NOW()),
(4, '闲聊', 'CHAT', NULL,
 '["你好","谢谢","你是谁","帮助","介绍","再见","早上好","晚上好"]',
 '问候、自我介绍、能力询问、感谢等社交对话', 0, true, NULL, NULL, NOW()),
(5, '全局检索', 'KB_RETRIEVAL', NULL,
 '["全部","所有","总共","一共","汇总","概览","范围","涵盖","包含哪些","多少","统计"]',
 '用户询问博客整体情况、文章总数、主题范围、全站概览等元问题，需要在所有知识库中检索', 9, true,
 '你是博客全局助手的补充：当用户询问博客整体情况时，你需要综合所有知识库的信息来回答，包括文章数量、主题分布、时间跨度等。', NULL, NOW())
ON CONFLICT (id) DO NOTHING;
