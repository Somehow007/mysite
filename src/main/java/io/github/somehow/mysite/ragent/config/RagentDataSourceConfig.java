package io.github.somehow.mysite.ragent.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * RAG 专用数据源配置（PostgreSQL + pgvector）。
 *
 * 关键知识点：Spring Boot 多数据源
 *   1. 主数据源见 PrimaryDataSourceConfig（@Primary）
 *   2. 第二数据源需要自己的 DataSource、SqlSessionFactory、TransactionManager
 *   3. @MapperScan 通过 basePackages 把不同包的 Mapper 绑定到不同数据源
 *   4. 用 DataSourceProperties 而不是直接 @ConfigurationProperties 绑 DataSource ——
 *      Hikari 的 URL 属性名是 jdbcUrl，直接绑 url 会静默失败、启动才报错
 *
 * 注意：手工构建的 ragentSqlSessionFactory 不会自动带上主库 DataBaseConfiguration 里的
 * MybatisPlusInterceptor（分页插件）和 MetaObjectHandler（字段自动填充）。
 * RAG 实体保持简单：不做逻辑删除，create_time/update_time 在代码里显式 set。
 */
@Configuration
@MapperScan(
    basePackages = "io.github.somehow.mysite.ragent.dao.mapper",
    sqlSessionFactoryRef = "ragentSqlSessionFactory"
)
public class RagentDataSourceConfig {

    @Bean
    @ConfigurationProperties("rag.datasource")
    public DataSourceProperties ragentDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("rag.datasource.hikari")
    public DataSource ragentDataSource(
            @Qualifier("ragentDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public SqlSessionFactory ragentSqlSessionFactory(
            @Qualifier("ragentDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        // 实体扫描
        factory.setTypeAliasesPackage("io.github.somehow.mysite.ragent.dao.entity");
        return factory.getObject();
    }

    @Bean
    public PlatformTransactionManager ragentTransactionManager(
            @Qualifier("ragentDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
