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
