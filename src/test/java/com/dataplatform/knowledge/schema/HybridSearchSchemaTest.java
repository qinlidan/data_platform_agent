package com.dataplatform.knowledge.schema;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 1 - Schema 测试: 验证中文分词扩展安装
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
public class HybridSearchSchemaTest {

    @Autowired
    @Qualifier("postgresqlJdbcTemplate")
    private JdbcTemplate pgJdbcTemplate;

    @Test
    public void testChineseTokenizerInstalled() {
        // 尝试 pg_jieba
        String jiebaCfg = "SELECT to_tsvector('jiebacfg', '内存溢出')";
        try {
            String result = pgJdbcTemplate.queryForObject(jiebaCfg, String.class);
            assertNotNull(result, "jiebacfg should work");
            System.out.println("✓ pg_jieba (jiebacfg) is available");
            return;
        } catch (Exception e) {
            System.out.println("jiebacfg not available, trying zhparser...");
        }

        // 尝试 zhparser
        String zhparserCfg = "SELECT to_tsvector('zhcfg', '内存溢出')";
        try {
            String result = pgJdbcTemplate.queryForObject(zhparserCfg, String.class);
            assertNotNull(result, "zhcfg should work");
            System.out.println("✓ zhparser (zhcfg) is available");
            return;
        } catch (Exception e) {
            fail("Neither pg_jieba nor zhparser is installed. Cannot proceed with hybrid search.");
        }
    }

    @Test
    public void testToTsqueryWorks() {
        // 验证 to_tsquery 能解析中文查询（Review Focus #1）
        String query = "SELECT to_tsquery('jiebacfg', 'Spark & 内存溢出')";
        try {
            String result = pgJdbcTemplate.queryForObject(query, String.class);
            assertNotNull(result);
            System.out.println("✓ to_tsquery works with jiebacfg: " + result);
        } catch (Exception e) {
            // 如果 jiebacfg 不可用，尝试 zhcfg
            String queryZh = "SELECT to_tsquery('zhcfg', 'Spark & 内存溢出')";
            String result = pgJdbcTemplate.queryForObject(queryZh, String.class);
            assertNotNull(result);
            System.out.println("✓ to_tsquery works with zhcfg: " + result);
        }
    }
}
