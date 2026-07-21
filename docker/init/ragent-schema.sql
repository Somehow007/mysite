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
