package com.dataplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 初始化器只做检查，绝不能执行 DDL。
 * 历史教训：pgvector 0.8 自带 C 实现的 cosine_distance(vector, vector)，且 <=> 运算符就绑定在它上面；
 * 用 CREATE OR REPLACE 把它覆盖成 "SELECT $1 <=> $2" 会让 <=> 无限递归（stack depth limit exceeded）。
 */
class PostgreSQLInitializerTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PostgreSQLInitializer initializer = new PostgreSQLInitializer(jdbc);

    @Test
    void neverExecutesAnyDdl() {
        when(jdbc.queryForObject(anyString(), eq(Double.class))).thenReturn(1.0);

        initializer.onApplicationEvent(null);

        verify(jdbc, never()).execute(anyString());
        verify(jdbc, never()).update(anyString());
    }

    @Test
    void doesNotThrowWhenTheFunctionIsBroken() {
        when(jdbc.queryForObject(anyString(), eq(Double.class)))
            .thenThrow(new RuntimeException("stack depth limit exceeded"));

        assertDoesNotThrow(() -> initializer.onApplicationEvent(null));
        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void doesNotThrowWhenTheFunctionReturnsAWrongValue() {
        when(jdbc.queryForObject(anyString(), eq(Double.class))).thenReturn(null);

        assertDoesNotThrow(() -> initializer.onApplicationEvent(null));
        verify(jdbc, never()).execute(anyString());
    }
}
