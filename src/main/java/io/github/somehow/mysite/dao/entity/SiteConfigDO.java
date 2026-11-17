package io.github.somehow.mysite.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.somehow.mysite.commons.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 站点配置单行表（id 固定为 1）。字段 NULL = 继承 env/yaml 默认值（见 SiteProperties /
 * application.yaml {@code site:} 段）；非空 = 后台保存的覆盖值，运行时覆盖到
 * {@link io.github.somehow.mysite.config.SiteProperties} 单例上即时生效。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_site_config")
public class SiteConfigDO extends BaseDO {

    private Long id;
    private String title;
    private String subtitle;
    private String description;
    private String author;
    private String authorBio;
    private String authorTagline;
    private String aboutContent;
    private String techStack;
    private String avatar;
    private String url;
    private String github;
    private String rss;
    private Boolean commentEnabled;
}
