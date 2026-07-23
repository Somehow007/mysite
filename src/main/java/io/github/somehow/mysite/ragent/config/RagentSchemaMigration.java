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
