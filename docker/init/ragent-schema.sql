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
    type VARCHAR(20) NOT NULL DEFAULT 'KB_RETRIEVAL',  -- KB_META / KB_RETRIEVAL / CHAT
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

-- 种子数据：三类模式意图（统计/管理、内容检索、闲聊）
-- ON CONFLICT DO UPDATE：已有卷也能从旧的「领域混模式」种子切过来
INSERT INTO t_rag_intent (id, name, type, kb_id, keywords, description, priority, enabled, custom_prompt_fragment, custom_top_k, create_time)
VALUES
(1, '知识库统计与概览', 'KB_META', NULL,
 '["多少篇","几篇文章","文章数量","文档数量","谁上传","谁最后上传","最后上传","最近上传","知识库情况","知识库有哪些","库里有哪些","上传人"]',
 '用户询问知识库本身的情况：有多少篇文章、谁写的/谁上传的、谁最后上传、最近入库了什么、知识库里有哪些文章。这类问题必须查目录，禁止用向量检索文章正文。', 10, true,
 '你在回答知识库目录问题：只陈述清单中的篇数、作者和最近文章，不要检索或引用文章正文。', NULL, NOW()),
(2, '读书笔记检索', 'KB_RETRIEVAL', NULL,
 '["读书","书籍","推荐","读后感","学习路线","入门","书单","阅读","好书"]',
 '（已停用）原领域检索意图，避免与模式分类抢票', 5, false,
 '你是博客读书笔记助手的补充：推荐书籍时说明理由。', 5, NOW()),
(3, '学习笔记检索', 'KB_RETRIEVAL', NULL,
 '["笔记","学习","总结","复习","知识点","面试","教程","整理","备忘","踩坑","实践"]',
 '（已停用）原领域检索意图，避免与模式分类抢票', 8, false,
 '你是博客学习笔记助手的补充：回答要结构化。', 3, NOW()),
(4, '闲聊', 'CHAT', NULL,
 '["你好","谢谢","你是谁","再见","早上好","晚上好"]',
 '问候、感谢、自我介绍、与博客内容无关的闲聊。不包括询问知识库篇数/作者，也不包括具体技术问题。', 0, true, NULL, NULL, NOW()),
(5, '内容检索', 'KB_RETRIEVAL', NULL,
 '[]',
 '用户询问博客文章里的技术内容、实现方法、概念解释、代码或配置。不是在问知识库有多少篇、谁上传。', 9, true,
 '你是博客内容助手：根据检索到的文章片段回答，引用文章标题。', NULL, NOW())
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    kb_id = EXCLUDED.kb_id,
    keywords = EXCLUDED.keywords,
    description = EXCLUDED.description,
    priority = EXCLUDED.priority,
    enabled = EXCLUDED.enabled,
    custom_prompt_fragment = EXCLUDED.custom_prompt_fragment,
    custom_top_k = EXCLUDED.custom_top_k;

-- ============================================================
-- AI 用量记录（调用记录 + 消费看板）
-- ============================================================

CREATE TABLE IF NOT EXISTS t_llm_usage (
    id BIGINT PRIMARY KEY,
    trace_id VARCHAR(36) NOT NULL,
    call_type VARCHAR(32) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(64) NOT NULL,
    user_id BIGINT,
    username VARCHAR(64),
    visitor_id VARCHAR(64),
    user_role VARCHAR(32),
    conversation_id BIGINT,
    document_id BIGINT,
    prompt_tokens INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    token_source VARCHAR(16) NOT NULL DEFAULT 'ESTIMATED',
    cost NUMERIC(12,6) DEFAULT 0,
    currency VARCHAR(8) DEFAULT 'CNY',
    latency_ms INT,
    success BOOLEAN NOT NULL DEFAULT true,
    error_message TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_llm_usage_time ON t_llm_usage(create_time DESC);
CREATE INDEX IF NOT EXISTS idx_llm_usage_user ON t_llm_usage(user_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_llm_usage_trace ON t_llm_usage(trace_id);
CREATE INDEX IF NOT EXISTS idx_llm_usage_type ON t_llm_usage(call_type, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_llm_usage_model ON t_llm_usage(model, create_time DESC);

-- ============================================================
-- AI 供应商运行时覆盖（后台「模型与 API」；启动时覆盖 yaml+env）
-- embedding / rerank 列保留但不允许后台改（向量维度绑定）
-- ============================================================

CREATE TABLE IF NOT EXISTS t_llm_provider_setting (
    name VARCHAR(32) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT true,
    priority INT NOT NULL DEFAULT 99,
    base_url VARCHAR(256),
    chat_model VARCHAR(64),
    embedding_model VARCHAR(64),
    rerank_model VARCHAR(64),
    embedding_dimension INT,
    api_key TEXT,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
