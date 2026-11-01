package com.dataplatform.ai.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 多数据源配置
 * - MySQL: MyBatis-Plus，主数据源，管理业务表 + knowledge_chunks
 * - PostgreSQL: Spring Data JPA，pgvector 向量检索
 */
@Configuration
@EnableJpaRepositories(
    basePackages = {
        "com.dataplatform.ai.repository.vector",
        "com.dataplatform.knowledge.repository"  // Task 4: 添加 knowledge repository 包
    },
    entityManagerFactoryRef = "postgresEntityManagerFactory",
    transactionManagerRef = "postgresTransactionManager"
)
public class DataSourceConfig {

    // ======================== MySQL 数据源（MyBatis-Plus，主数据源） ========================

    @Primary
    @Bean(name = "mysqlDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.mysql")
    public DataSource mysqlDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Primary
    @Bean(name = "mysqlSqlSessionFactory")
    public SqlSessionFactory mysqlSqlSessionFactory(@Qualifier("mysqlDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
            .getResources("classpath*:/mapper/**/*.xml"));

        // MyBatis-Plus 全局配置
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
            new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(org.apache.ibatis.logging.stdout.StdOutImpl.class);
        factoryBean.setConfiguration(configuration);

        return factoryBean.getObject();
    }

    // ======================== PostgreSQL 数据源（Spring Data JPA） ========================

    @Bean(name = "postgresDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.postgres")
    public DataSource postgresDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "postgresEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean postgresEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("postgresDataSource") DataSource dataSource) {

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.hbm2ddl.auto", "none");  // 禁止自动建表
        properties.put("hibernate.format_sql", true);

        return builder
            .dataSource(dataSource)
            .packages(
                "com.dataplatform.ai.entity.vector",
                "com.dataplatform.knowledge.entity"  // Task 4: 添加 knowledge entity 包
            )
            .persistenceUnit("postgres")
            .properties(properties)
            .build();
    }

    @Bean(name = "postgresTransactionManager")
    public PlatformTransactionManager postgresTransactionManager(
            @Qualifier("postgresEntityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }

    /**
     * Task 4: pgvectorTransactionManager 别名
     * 用于测试中的 @Transactional("pgvectorTransactionManager")
     */
    @Bean(name = "pgvectorTransactionManager")
    public PlatformTransactionManager pgvectorTransactionManager(
            @Qualifier("postgresTransactionManager") PlatformTransactionManager transactionManager) {
        return transactionManager;
    }
}
