package com.dataplatform.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 1 - 测试 knowledge_chunks 表结构
 */
@SpringBootTest
class KnowledgeChunkSchemaTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void testKnowledgeChunksTableExists() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "knowledge_chunks", null)) {
            assertTrue(rs.next(), "knowledge_chunks table should exist");
        }
    }

    @Test
    void testKnowledgeChunksColumns() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.getMetaData().getColumns(null, null, "knowledge_chunks", null);
            Set<String> columns = new HashSet<>();
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
            assertTrue(columns.contains("id"), "should have id column");
            assertTrue(columns.contains("doc_type"), "should have doc_type column");
            assertTrue(columns.contains("doc_id"), "should have doc_id column");
            assertTrue(columns.contains("category"), "should have category column");
            assertTrue(columns.contains("scene_type"), "should have scene_type column");
            assertTrue(columns.contains("scene_title"), "should have scene_title column");
            assertTrue(columns.contains("symptom_text"), "should have symptom_text column");
            assertTrue(columns.contains("questions"), "should have questions column");
            assertTrue(columns.contains("answer"), "should have answer column");
            assertTrue(columns.contains("section_path"), "should have section_path column");
            assertTrue(columns.contains("content_type"), "should have content_type column");
            assertTrue(columns.contains("is_tunable"), "should have is_tunable column");
            assertTrue(columns.contains("route_action"), "should have route_action column");
            assertTrue(columns.contains("prev_chunk_id"), "should have prev_chunk_id column");
            assertTrue(columns.contains("next_chunk_id"), "should have next_chunk_id column");
            assertTrue(columns.contains("vectorize_status"), "should have vectorize_status column");
            assertTrue(columns.contains("created_at"), "should have created_at column");
            assertTrue(columns.contains("updated_at"), "should have updated_at column");
        }
    }

    @Test
    void testVectorizeStatusDefaultValue() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            // 插入测试数据，不指定 vectorize_status
            stmt.execute("INSERT INTO knowledge_chunks (doc_type, doc_id, category, scene_type, " +
                    "scene_title, questions, answer) VALUES " +
                    "('test', 'test_doc', 'test_category', 'test_scene_type', " +
                    "'test_scene_title', 'test_question', 'test_answer')");

            // 查询验证默认值
            ResultSet rs = stmt.executeQuery("SELECT vectorize_status FROM knowledge_chunks WHERE doc_id = 'test_doc'");
            assertTrue(rs.next());
            String status = rs.getString("vectorize_status");
            assertTrue("pending_vectorization".equals(status),
                    "vectorize_status should default to 'pending_vectorization', but was: " + status);

            // 清理测试数据
            stmt.execute("DELETE FROM knowledge_chunks WHERE doc_id = 'test_doc'");
        }
    }
}
