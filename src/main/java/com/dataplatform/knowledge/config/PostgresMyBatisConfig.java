package com.dataplatform.knowledge.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * 知识库 PostgreSQL 侧的 MyBatis 配置（混合检索 RRF 等 PG 专用 SQL）。
 *
 * 为什么要单独一套：项目默认（@Primary）的 SqlSessionFactory 绑定的是 MySQL，
 * 凡是扫描进默认工厂的 Mapper，SQL 都会发到 MySQL，PG 的 {@code <=>}、{@code @@} 等运算符会直接语法错误。
 * 这里只让 {@code com.dataplatform.knowledge.pgmapper} 包绑定 PG 工厂，不影响 MySQL 侧任何 Mapper。
 *
 * 使用普通 MyBatis（SqlSessionFactoryBean），不用 MyBatis-Plus 的工厂：这些 Mapper 全是手写 XML，不需要 BaseMapper。
 */
@Configuration
@MapperScan(
    basePackages = "com.dataplatform.knowledge.pgmapper",
    sqlSessionFactoryRef = "postgresSqlSessionFactory"
)
public class PostgresMyBatisConfig {

    @Bean(name = "postgresSqlSessionFactory")
    public SqlSessionFactory postgresSqlSessionFactory(
            @Qualifier("postgresDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        // 独立目录，避免被 MySQL 侧 classpath*:/mapper/**/*.xml 的通配扫描重复加载
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
            .getResources("classpath*:/mapper-pg/**/*.xml"));

        org.apache.ibatis.session.Configuration cfg = new org.apache.ibatis.session.Configuration();
        cfg.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(cfg);
        return factory.getObject();
    }
}
