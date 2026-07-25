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
    }

    private void execute(String sql, String label) {
        try (Connection conn = ragentDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("Schema migration applied: {}", label);
        } catch (SQLException e) {
            log.debug("Schema migration skipped (likely already applied): {} — {}", label, e.getMessage());
        }
    }
}
