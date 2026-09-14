package io.github.somehow.mysite.ragent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Ragent schema 自动迁移 —— 非 Flyway 环境下的轻量替代。
 *
 * Docker 新卷走 docker-entrypoint-initdb.d 的 init SQL（已包含新列），
 * 旧卷/已有数据通过此处 ALTER TABLE IF NOT EXISTS 自动补列，
 * 删容器不删 volume 时也能平滑升级。
 */
@Slf4j
@Component
public class RagentSchemaMigration implements InitializingBean {

    private final DataSource ragentDataSource;

    public RagentSchemaMigration(@Qualifier("ragentDataSource") DataSource ragentDataSource) {
        this.ragentDataSource = ragentDataSource;
    }

    @Override
    public void afterPropertiesSet() {
        execute("ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS embedding_text TEXT",
            "t_knowledge_chunk.embedding_text");

        // KB 启用/禁用开关
        execute("ALTER TABLE t_knowledge_base ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT true",
            "t_knowledge_base.enabled");

        // Phase 6: 意图识别表（已有卷不含此表时自动创建）
        execute("""
            CREATE TABLE IF NOT EXISTS t_rag_intent (
                id BIGINT PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                type VARCHAR(20) NOT NULL DEFAULT 'KB_RETRIEVAL',
                kb_id BIGINT,
                keywords TEXT,
                description TEXT,
                priority INT DEFAULT 0,
                enabled BOOLEAN DEFAULT true,
                custom_prompt_fragment TEXT,
                custom_top_k INT,
                create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """, "t_rag_intent");

        execute("""
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
            )
            """, "t_llm_usage");
        execute("CREATE INDEX IF NOT EXISTS idx_llm_usage_time ON t_llm_usage(create_time DESC)",
            "idx_llm_usage_time");
        execute("CREATE INDEX IF NOT EXISTS idx_llm_usage_user ON t_llm_usage(user_id, create_time DESC)",
            "idx_llm_usage_user");
        execute("CREATE INDEX IF NOT EXISTS idx_llm_usage_trace ON t_llm_usage(trace_id)",
            "idx_llm_usage_trace");
        execute("CREATE INDEX IF NOT EXISTS idx_llm_usage_type ON t_llm_usage(call_type, create_time DESC)",
            "idx_llm_usage_type");
        execute("CREATE INDEX IF NOT EXISTS idx_llm_usage_model ON t_llm_usage(model, create_time DESC)",
            "idx_llm_usage_model");

        execute("""
            CREATE TABLE IF NOT EXISTS t_llm_provider_setting (
                name VARCHAR(32) PRIMARY KEY,
                enabled BOOLEAN NOT NULL DEFAULT true,
                priority INT NOT NULL DEFAULT 99,
                base_url VARCHAR(256),
                chat_model VARCHAR(64),
                embedding_model VARCHAR(64),
                rerank_model VARCHAR(64),
                api_key TEXT,
                update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """, "t_llm_provider_setting");
        execute("ALTER TABLE t_llm_provider_setting ADD COLUMN IF NOT EXISTS embedding_dimension INT",
            "t_llm_provider_setting.embedding_dimension");

        // 种子意图曾绑定 yaml 里写死的 kb_id=1/2/3，真实知识库是雪花 ID，悬空引用会让来源名对不上
        execute("""
            UPDATE t_rag_intent i
               SET kb_id = NULL
             WHERE i.kb_id IS NOT NULL
               AND NOT EXISTS (SELECT 1 FROM t_knowledge_base k WHERE k.id = i.kb_id)
            """, "t_rag_intent.orphan_kb_id");

        // 三类模式意图：统计/管理、内容检索、闲聊（覆盖旧的领域混模式种子）
        execute("""
            INSERT INTO t_rag_intent (id, name, type, kb_id, keywords, description, priority, enabled, custom_prompt_fragment, custom_top_k, create_time)
            VALUES
            (1, '知识库统计与概览', 'KB_META', NULL,
             '["多少篇","几篇文章","文章数量","谁上传","谁写的","作者","最后上传","最近上传","最新文章","知识库情况","有哪些文章","文档数量","覆盖哪些"]',
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
                custom_top_k = EXCLUDED.custom_top_k
            """, "t_rag_intent.mode_taxonomy");
    }

    private void execute(String sql, String label) {
        try (Connection conn = ragentDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("Schema migration applied: {}", label);
        } catch (SQLException e) {
            log.warn("Schema migration skipped: {} — {}", label, e.getMessage());
        }
    }
}
