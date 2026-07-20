package io.github.somehow.mysite.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 主数据源（MySQL，博客数据）显式声明。
 * <p>
 * 为什么需要这个类？
 * RAG 的 PG 数据源加入后容器里有两个 DataSource，而主数据源原来靠
 * Spring Boot 自动配置、没有 @Primary，所有 byType 注入点都会报
 * NoUniqueBeanDefinitionException。显式声明 + @Primary 后：
 * - Boot 的 DataSource 自动配置退避（@ConditionalOnMissingBean）
 * - 主库的 SqlSessionFactory 也在这里显式创建（因为 RagentDataSourceConfig
 *   创建了 ragentSqlSessionFactory 后，MyBatis-Plus 自动配置的
 *   @ConditionalOnMissingBean(SqlSessionFactory.class) 会退避）
 * - spring.datasource.hikari.* 连接池配置继续生效
 * </p>
 * <p>
 * 注意：必须显式定义 DataSourceProperties bean 并通过 @Qualifier 注入，
 * 否则 RagentDataSourceConfig 里的 ragentDataSourceProperties 会让容器中
 * 存在两个 DataSourceProperties，导致 dataSource(DataSourceProperties) 按类型
 * 注入时报 NoUniqueBeanDefinitionException。
 * </p>
 */
@Configuration
public class PrimaryDataSourceConfig {

    /** 显式绑定 spring.datasource.*，替代 Boot 自动配置的 DataSourceProperties */
    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(
            @Qualifier("primaryDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    /**
     * 主库 SqlSessionFactory —— 因为 RagentDataSourceConfig 也创建了
     * SqlSessionFactory（ragentSqlSessionFactory），MyBatis-Plus 自动配置的
     * @ConditionalOnMissingBean(SqlSessionFactory.class) 会退避，
     * 所以必须显式创建这个 bean（bean name = "sqlSessionFactory"）。
     *
     * 注入 DataBaseConfiguration 中定义的 MybatisPlusInterceptor（分页插件）
     * 和 MetaObjectHandler（字段自动填充），保持与原来自动配置的行为一致。
     */
    @Primary
    @Bean
    public SqlSessionFactory sqlSessionFactory(
            @Qualifier("dataSource") DataSource dataSource,
            MybatisPlusInterceptor mybatisPlusInterceptor,
            MetaObjectHandler myMetaObjectHandler) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTypeAliasesPackage("io.github.somehow.mysite.dao.entity");
        factory.setPlugins(mybatisPlusInterceptor);
        // 注册 MetaObjectHandler（字段自动填充：createTime/updateTime/delFlag）
        com.baomidou.mybatisplus.core.config.GlobalConfig globalConfig =
            new com.baomidou.mybatisplus.core.config.GlobalConfig();
        globalConfig.setMetaObjectHandler(myMetaObjectHandler);
        factory.setGlobalConfig(globalConfig);
        return factory.getObject();
    }
}
