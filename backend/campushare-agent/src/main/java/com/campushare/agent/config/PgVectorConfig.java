package com.campushare.agent.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PostgreSQL + pgvector 第二数据源配置。
 *
 * 与 MySQL 主数据源隔离：
 * - MySQL 走 Spring Boot 自动配置（spring.datasource 前缀），MyBatis-Plus 使用
 * - PostgreSQL 走自定义前缀 app.datasource.pgvector，手动创建 DataSource + JdbcTemplate
 * - VectorStore 通过 @Qualifier("pgvectorJdbcTemplate") 注入，不与 MyBatis-Plus 冲突
 */
@Configuration
public class PgVectorConfig {

    @Bean(name = "pgvectorDataSource")
    @ConfigurationProperties(prefix = "app.datasource.pgvector")
    public DataSource pgvectorDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean(name = "pgvectorJdbcTemplate")
    public JdbcTemplate pgvectorJdbcTemplate(@Qualifier("pgvectorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
