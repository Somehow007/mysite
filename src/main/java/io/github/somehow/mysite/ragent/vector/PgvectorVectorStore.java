package io.github.somehow.mysite.ragent.vector;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.pgvector.PGvector;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * pgvector 实现 —— 用 JDBC 原生 SQL 操作向量
 *
 * 为什么不用 Mybatis-Plus 直接操作
 *  pgvector 的 vector 类型是一个自定义 JDBC 操作类型（PGVector）。
 *  Mybatis-Plus 不原生支持，需要用 PGVector 库提供的 getValue/setValue
 *
 * 核心 SQL：
 *  -- 插入向量
 *  INSERT INTO t_knowledge_vector (id, chunk_id, kb_id, embedding, model)
 *  VALUES (?, ?, ?, ?::vector, ?)
 *
 *  -- 余弦相似度检索（<=> 是 pgvector 提供的余弦距离运算符）
 *  SELECT v.id, v.chunk_id, c.content, c.doc_id, d.title,
 *         1 - (v.embedding <=> ?::vector) AS similarity
 *  FROM t_knowledge_vector v
 *  JOIN t_knowledge_chunk c ON v.chunk_id = c.id
 *  JOIN t_knowledge_document d ON c.doc_id = d.id
 *  ORDER BY v.embedding <==> ?::vector
 *  LIMIT ?
 *
 *  HNSW 索引原理：
 *      HNSW = Hierarchical Navigable Small World
 *      多图层结构，上层稀疏下层密集，搜索时从上层快速定位区域，
 *      再在下层精确查找，时间复杂度 O(log N)，ANN（近似最近邻）领域
 *      最主流的算法之一，pgvector、Milvus、Weaviate 都在用。
 */
@Component
public class PgvectorVectorStore implements VectorStore{

    private final DataSource dataSource;

    public PgvectorVectorStore(@Qualifier("ragentDataSource") DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(List<VectorEntry> vectors) {
        String sql = """
                INSERT INTO t_knowledge_vector (id, chunk_id, kb_id, embedding, model)
                VALUES (?, ?, ?, ?::vector, ?)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (VectorEntry entry : vectors) {
                // 主键用 MyBatis-Plus 雪花算法生成（建表无自增序列，与全库 ASSIGN_ID 策略一致）
                ps.setLong(1, IdWorker.getId());
                ps.setLong(2, entry.chunkId());
                ps.setLong(3, entry.kbId());
                ps.setObject(4, new PGvector(entry.embedding()));
                ps.setString(5, entry.model());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert vectors", e);
        }
    }

    @Override
    public List<SearchResult> search(float[] queryEmbedding, int topK, Long kbId) {
        String sql = """
                SELECT
                    v.chunk_id,
                    c.doc_id,
                    d.title AS doc_title,
                    c.content,
                    1 - (v.embedding <=> ?::vector) AS similarity,
                    v.kb_id
                FROM t_knowledge_vector v
                JOIN t_knowledge_chunk c ON v.chunk_id = c.id
                JOIN t_knowledge_document d ON c.doc_id = d.id
                WHERE d.status = 'READY'
                    AND (?::bigint IS NULL OR v.kb_id = ?::bigint)
                ORDER BY v.embedding <=> ?::vector
                LIMIT ?
                """;
        List<SearchResult> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
            PGvector queryVec = new PGvector(queryEmbedding);
            ps.setObject(1, queryVec);
            // kbId 为 null 时不过滤（全库检索），否则限定知识库
            if (kbId == null) {
                ps.setNull(2, Types.BIGINT);
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(2, kbId);
                ps.setLong(3, kbId);
            }
            ps.setObject(4, queryVec);  // ORDER BY 也用到
            ps.setInt(5, topK);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new SearchResult(
                            rs.getLong("chunk_id"),
                            rs.getLong("doc_id"),
                            rs.getString("doc_title"),
                            rs.getString("content"),
                            rs.getFloat("similarity"),
                            rs.getLong("kb_id")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Vector search failed", e);
        }
        return results;
    }

    @Override
    public void deleteByKbId(Long kbId) {
        String sql = "DELETE FROM t_knowledge_vector WHERE kb_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, kbId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete vectors by kbId: " + kbId, e);
        }
    }

    @Override
    public void deleteByDocId(Long docId) {
        // t_knowledge_vector 没有 doc_id，需要 JOIN t_knowledge_chunk
        String sql = """
                DELETE FROM t_knowledge_vector
                WHERE chunk_id IN (SELECT id FROM t_knowledge_chunk WHERE doc_id = ?)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, docId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete vectors by docId: " + docId, e);
        }
    }
}
