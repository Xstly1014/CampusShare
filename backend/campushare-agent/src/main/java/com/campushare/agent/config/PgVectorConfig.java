package com.campushare.agent.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 双数据源配置：MySQL（主）+ PostgreSQL/pgvector（辅）。
 *
 * MySQL 主数据源：
 * - 通过 @Primary 标记，MyBatis-Plus 自动使用
 * - 使用 DataSourceProperties 正确映射 url → jdbcUrl
 *
 * PostgreSQL pgvector 辅数据源：
 * - 仅用于 VectorStore，通过 @Qualifier("pgvectorJdbcTemplate") 注入
 * - 不与 MyBatis-Plus 冲突
 */
@Configuration
public class PgVectorConfig {

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mysqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "dataSource")
    public DataSource mysqlDataSource(@Qualifier("mysqlDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

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
