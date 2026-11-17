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
        execute("ALTER TABLE t_llm_usage ADD COLUMN IF NOT EXISTS cache_read_tokens INT DEFAULT 0",
            "t_llm_usage.cache_read_tokens");
        execute("ALTER TABLE t_llm_usage ADD COLUMN IF NOT EXISTS cache_write_tokens INT DEFAULT 0",
            "t_llm_usage.cache_write_tokens");
        execute("ALTER TABLE t_llm_usage ADD COLUMN IF NOT EXISTS price_id BIGINT",
            "t_llm_usage.price_id");
        execute("ALTER TABLE t_llm_usage ADD COLUMN IF NOT EXISTS peak BOOLEAN",
            "t_llm_usage.peak");

        execute("""
            CREATE TABLE IF NOT EXISTS t_llm_model_price (
                id BIGINT PRIMARY KEY,
                provider VARCHAR(32) NOT NULL,
                model VARCHAR(128) NOT NULL,
                display_name VARCHAR(128),
                currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
                unit VARCHAR(32) NOT NULL DEFAULT 'PER_MILLION_TOKENS',
                input_price NUMERIC(12,6),
                cache_read_price NUMERIC(12,6),
                cache_read_factor NUMERIC(8,4),
                cache_write_price NUMERIC(12,6),
                cache_write_factor NUMERIC(8,4),
                output_price NUMERIC(12,6),
                peak_windows TEXT,
                off_peak_factor NUMERIC(6,4),
                exclude_holidays BOOLEAN DEFAULT false,
                tiers TEXT,
                effective_from TIMESTAMP,
                effective_to TIMESTAMP,
                source VARCHAR(32),
                note TEXT,
                create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """, "t_llm_model_price");
        execute("CREATE INDEX IF NOT EXISTS idx_llm_model_price_key ON t_llm_model_price(provider, model, effective_from DESC)",
            "idx_llm_model_price_key");
        execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_llm_model_price_ver ON t_llm_model_price(provider, model, effective_from)",
            "uq_llm_model_price_ver");

        execute("""
            CREATE TABLE IF NOT EXISTS t_llm_holiday (
                holiday_date DATE PRIMARY KEY,
                name VARCHAR(64)
            )
            """, "t_llm_holiday");

        // 种子灌入标记：保证每类内置种子只在首次启动时写入一次，
        // 之后管理员在「计价」页的增删改不会被重启覆盖复活。
        execute("""
            CREATE TABLE IF NOT EXISTS t_llm_seed_marker (
                seed_key VARCHAR(64) PRIMARY KEY,
                seeded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """, "t_llm_seed_marker");

        execute("""
            CREATE TABLE IF NOT EXISTS t_llm_balance_snapshot (
                id BIGINT PRIMARY KEY,
                provider VARCHAR(32) NOT NULL,
                ok BOOLEAN,
                payload TEXT,
                latency_ms INT,
                create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """, "t_llm_balance_snapshot");
        execute("CREATE INDEX IF NOT EXISTS idx_llm_balance_snapshot_provider ON t_llm_balance_snapshot(provider, create_time DESC)",
            "idx_llm_balance_snapshot_provider");

        // 2026 年法定节假日放假安排（国办发明电〔2025〕7号，2025-11-04 发布）。
        // 只含放假调休日；调休上班日（2/14、2/28、5/9、9/20、10/10）不算节假日。
        // 后续年份由管理端「计价」页维护，本表不自动滚动。
        execute("""
            WITH marker AS (
                INSERT INTO t_llm_seed_marker (seed_key)
                SELECT 'holidays_2026'
                WHERE NOT EXISTS (SELECT 1 FROM t_llm_holiday)
                ON CONFLICT (seed_key) DO NOTHING
                RETURNING seed_key
            )
            INSERT INTO t_llm_holiday (holiday_date, name)
            SELECT v.holiday_date, v.name
            FROM (VALUES
                ('2026-01-01'::date,'元旦'),('2026-01-02','元旦'),('2026-01-03','元旦'),
                ('2026-02-15','春节'),('2026-02-16','春节'),('2026-02-17','春节'),('2026-02-18','春节'),
                ('2026-02-19','春节'),('2026-02-20','春节'),('2026-02-21','春节'),('2026-02-22','春节'),('2026-02-23','春节'),
                ('2026-04-04','清明节'),('2026-04-05','清明节'),('2026-04-06','清明节'),
                ('2026-05-01','劳动节'),('2026-05-02','劳动节'),('2026-05-03','劳动节'),('2026-05-04','劳动节'),('2026-05-05','劳动节'),
                ('2026-06-19','端午节'),('2026-06-20','端午节'),('2026-06-21','端午节'),
                ('2026-09-25','中秋节'),('2026-09-26','中秋节'),('2026-09-27','中秋节'),
                ('2026-10-01','国庆节'),('2026-10-02','国庆节'),('2026-10-03','国庆节'),('2026-10-04','国庆节'),
                ('2026-10-05','国庆节'),('2026-10-06','国庆节'),('2026-10-07','国庆节')
            ) AS v(holiday_date, name)
            WHERE EXISTS (SELECT 1 FROM marker)
            ON CONFLICT (holiday_date) DO NOTHING
            """, "t_llm_holiday.seed_2026");

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

        // 会话归属切换回填：t_llm_usage 记录了每次调用的 user_id+conversation_id，
        // 对「该会话在 usage 中只出现过一个 user_id」的存量会话无损回填 user_id。
        // 多用户歧义的会话不回填，留给续聊时 lazy claim（ConversationManager.claimUserId）。
        // 幂等：只补 user_id IS NULL 的行，重复执行无副作用。
        execute("""
            UPDATE t_conversation c
               SET user_id = sub.user_id
              FROM (
                    SELECT conversation_id, MIN(user_id) AS user_id
                      FROM t_llm_usage
                     WHERE conversation_id IS NOT NULL AND user_id IS NOT NULL
                     GROUP BY conversation_id
                    HAVING COUNT(DISTINCT user_id) = 1
                   ) sub
             WHERE c.id = sub.conversation_id
               AND c.user_id IS NULL
            """, "t_conversation.user_id_backfill");

        // 三类模式意图：统计/管理、内容检索、闲聊（覆盖旧的领域混模式种子）
        execute("""
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
                custom_top_k = EXCLUDED.custom_top_k
            """, "t_rag_intent.narrow_keywords");
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
