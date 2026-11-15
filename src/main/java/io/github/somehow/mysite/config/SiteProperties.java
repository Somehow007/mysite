package io.github.somehow.mysite.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 站点信息配置（{@code site.*}），由 {@code GET /v1/site/config} 原样序列化给前端。
 *
 * <p>标量真实值走环境变量（见 application.yaml {@code site:} 段的 {@code ${SITE_*:}} 占位），
 * 本地与生产分别由项目根 {@code .env} / {@code /opt/mysite/.env} 注入；
 * 类内默认值与历史硬编码一致，不配 env 时行为不变。
 * {@code navigation} 无环境变量入口，默认值在此；部署侧可用 {@code site.navigation}
 * 整体覆盖（见 deploy/config/application-production.yml 的 additional-location 机制）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "site")
public class SiteProperties {

    private String title = "MySite";
    private String subtitle = "";
    private String description = "一个极简的个人博客";
    private String author = "Admin";
    private String authorBio = "";
    private String authorTagline = "";
    /** About 页长文（markdown）；env 只能放单行，多行内容走 yaml {@code site.about-content} 覆盖 */
    private String aboutContent = "";
    /** 站点技术栈一句话（如「Vue 3 · Spring Boot · MySQL」），About 页作者卡展示 */
    private String techStack = "";
    private String avatar = "";
    private String url = "";
    private String github = "";
    private String rss = "";
    private boolean commentEnabled = true;
    private List<NavItem> navigation = List.of(
            navItem("首页", "/"),
            navItem("归档", "/archive"),
            navItem("关于", "/about"));

    /** 首页「直接问」示例问题 chips；yaml 可用 {@code site.ask-examples} 整体覆盖 */
    private List<String> askExamples = List.of(
            "LLM 熔断怎么做的",
            "pgvector 和 ES 怎么选",
            "为什么前端 ID 用 string",
            "最近在学什么");

    /**
     * 首页「直接问」示例问答 + 出处卡；默认 null，前端不渲染示例块。
     * 用 {@code site.ask-demo} 配置启用，{@code answer} 置空同样视为关闭。
     */
    private AskDemo askDemo;

    private static NavItem navItem(String label, String path) {
        NavItem item = new NavItem();
        item.setLabel(label);
        item.setPath(path);
        return item;
    }

    @Data
    public static class NavItem {
        private String label;
        private String path;
    }

    @Data
    public static class AskDemo {
        private String question;
        private String answer;
        private List<AskSource> sources = List.of();
    }

    @Data
    public static class AskSource {
        private String title;
        /** 标题后的补充说明（如「§4 多模型路由」），可空 */
        private String section;
        /** 文章 ID；有值前端渲染 /post/:id 链接，无值渲染静态卡 */
        private String articleId;
    }
}
