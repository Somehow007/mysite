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
    private String avatar = "";
    private String url = "";
    private String github = "";
    private String rss = "";
    private boolean commentEnabled = true;
    private List<NavItem> navigation = List.of(
            navItem("首页", "/"),
            navItem("归档", "/archive"),
            navItem("关于", "/about"));

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
}
