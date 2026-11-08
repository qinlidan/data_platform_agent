package com.dataplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 真实 PostgreSQL：cosine_distance(vector, vector) 必须是 pgvector 扩展自带的 C 实现，
 * 而且应用启动（含初始化器）之后它和 <=> 运算符都能正常工作。
 *
 * 这个测试是回归保护：曾经有人用 CREATE OR REPLACE 把它覆盖成 SQL 版 "SELECT $1 <=> $2"，
 * 而 <=> 在 pgvector 里就绑定在这个函数上，结果无限递归，所有向量检索全部报错。
 */
@SpringBootTest
class PostgreSQLInitializerIT {

    @Autowired private PostgreSQLInitializer initializer;

    @Autowired
    @Qualifier("postgresqlJdbcTemplate")
    private JdbcTemplate pg;

    private String languageOfVectorCosineDistance() {
        return pg.queryForObject(
            "select l.lanname from pg_proc p join pg_language l on l.oid = p.prolang "
                + "where p.proname = 'cosine_distance' "
                + "and pg_get_function_identity_arguments(p.oid) = 'vector, vector'", String.class);
    }

    @Test
    void functionIsTheExtensionNativeCImplementation() {
        initializer.onApplicationEvent(null);   // 应用启动时它也会跑一次

        assertEquals("c", languageOfVectorCosineDistance(),
            "cosine_distance(vector, vector) 必须是扩展自带的 C 实现，不能被覆盖成 SQL 版本");
    }

    @Test
    void functionAndOperatorBothWork() {
        initializer.onApplicationEvent(null);

        Double fnSame = pg.queryForObject("select cosine_distance('[1,0,0]'::vector, '[1,0,0]'::vector)", Double.class);
        Double fnOrtho = pg.queryForObject("select cosine_distance('[1,0,0]'::vector, '[0,1,0]'::vector)", Double.class);
        Double opSame = pg.queryForObject("select '[1,0,0]'::vector <=> '[1,0,0]'::vector", Double.class);
        Double opOrtho = pg.queryForObject("select '[1,0,0]'::vector <=> '[0,1,0]'::vector", Double.class);
        assertNotNull(fnSame);
        assertNotNull(opSame);
        assertEquals(0.0, fnSame, 1e-9);
        assertEquals(1.0, fnOrtho, 1e-9);
        assertEquals(0.0, opSame, 1e-9);
        assertEquals(1.0, opOrtho, 1e-9);
    }

    @Test
    void isIdempotent() {
        initializer.onApplicationEvent(null);
        initializer.onApplicationEvent(null);

        Integer n = pg.queryForObject(
            "select count(*) from pg_proc where proname = 'cosine_distance' "
                + "and pg_get_function_identity_arguments(oid) = 'vector, vector'", Integer.class);
        assertEquals(1, n);
        assertEquals("c", languageOfVectorCosineDistance());
    }
}
