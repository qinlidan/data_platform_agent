package com.dataplatform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL 启动自检：只检查，不做任何 DDL。
 *
 * 历史教训：这个类曾经用 CREATE OR REPLACE 创建 cosine_distance(vector, vector)，函数体是 "SELECT $1 <=> $2"。
 * 但 pgvector 0.8 自带 C 实现的同名函数，而且 <=> 运算符就绑定在它上面；
 * 一旦被覆盖成 SQL 版本，<=> 会调用函数、函数又调用 <=>，无限递归（stack depth limit exceeded），
 * 所有向量检索全部报错。当时它因为错误地使用了 MySQL 的数据源，每次都语法错误，反而"保护"了库。
 * 所以这里不再创建任何对象，只在启动时做一次探测，坏了就明确报错。
 *
 * 必须使用 PostgreSQL 的 JdbcTemplate：项目默认（@Primary）的数据源是 MySQL。
 */
@Component
@Slf4j
public class PostgreSQLInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final JdbcTemplate postgresqlJdbcTemplate;

    public PostgreSQLInitializer(@Qualifier("postgresqlJdbcTemplate") JdbcTemplate postgresqlJdbcTemplate) {
        this.postgresqlJdbcTemplate = postgresqlJdbcTemplate;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            checkCosineDistance();
        } catch (Exception e) {
            // 自检失败不阻止应用启动，但必须是醒目的 ERROR，让人立刻知道向量检索不可用
            log.error("PostgreSQL 向量函数自检失败：cosine_distance 不可用，向量检索会报错。"
                + "常见原因：该函数被 CREATE OR REPLACE 覆盖成了 SQL 版本导致无限递归。"
                + "修复：执行 db/fix/restore_cosine_distance.sql", e);
        }
    }

    /** 用一次真实调用探测：相同向量的余弦距离应为 0。 */
    private void checkCosineDistance() {
        Double d = postgresqlJdbcTemplate.queryForObject(
            "select cosine_distance('[1,0,0]'::vector, '[1,0,0]'::vector)", Double.class);
        if (d == null || Math.abs(d) > 1e-9) {
            throw new IllegalStateException("cosine_distance 返回了异常值: " + d);
        }
        log.info("PostgreSQL 向量函数自检通过");
    }
}
