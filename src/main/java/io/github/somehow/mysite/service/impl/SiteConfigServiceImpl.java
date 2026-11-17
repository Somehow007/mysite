package io.github.somehow.mysite.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.somehow.mysite.config.SiteProperties;
import io.github.somehow.mysite.dao.entity.SiteConfigDO;
import io.github.somehow.mysite.dao.mapper.SiteConfigMapper;
import io.github.somehow.mysite.dto.req.site.SiteConfigUpdateReqDTO;
import io.github.somehow.mysite.service.SiteConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 站点配置：env/yaml 为默认值（{@link SiteProperties} 单例），t_site_config 单行表为
 * 后台覆盖值。启动时把 DB 非空字段覆盖到单例；PUT 保存后同样即时覆盖，无需重启。
 * 表未建/不可用时静默降级为纯 env 配置（init 捕获异常仅告警）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteConfigServiceImpl implements SiteConfigService {

    private static final long CONFIG_ID = 1L;

    private final SiteConfigMapper siteConfigMapper;
    private final SiteProperties siteProperties;

    @PostConstruct
    public void init() {
        try {
            SiteConfigDO row = siteConfigMapper.selectById(CONFIG_ID);
            if (row != null) {
                apply(row);
                log.info("站点配置已从 t_site_config 加载覆盖值");
            }
        } catch (Exception e) {
            log.warn("t_site_config 不可用，站点配置使用 env/yaml 默认值: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public SiteProperties update(SiteConfigUpdateReqDTO dto) {
        SiteConfigDO row = SiteConfigDO.builder()
                .id(CONFIG_ID)
                .title(normalize(dto.getTitle()))
                .subtitle(normalize(dto.getSubtitle()))
                .description(normalize(dto.getDescription()))
                .author(normalize(dto.getAuthor()))
                .authorBio(normalize(dto.getAuthorBio()))
                .authorTagline(normalize(dto.getAuthorTagline()))
                .aboutContent(normalize(dto.getAboutContent()))
                .techStack(normalize(dto.getTechStack()))
                .avatar(normalize(dto.getAvatar()))
                .url(normalize(dto.getUrl()))
                .github(normalize(dto.getGithub()))
                .rss(normalize(dto.getRss()))
                .commentEnabled(dto.getCommentEnabled())
                .build();

        SiteConfigDO existing = siteConfigMapper.selectById(CONFIG_ID);
        if (existing == null) {
            siteConfigMapper.insert(row);
        } else {
            // updateById 默认忽略 null 字段写不进「清除覆盖值」，改用 UpdateWrapper 显式 set 全列
            siteConfigMapper.update(null, Wrappers.<SiteConfigDO>lambdaUpdate()
                    .eq(SiteConfigDO::getId, CONFIG_ID)
                    .set(SiteConfigDO::getTitle, row.getTitle())
                    .set(SiteConfigDO::getSubtitle, row.getSubtitle())
                    .set(SiteConfigDO::getDescription, row.getDescription())
                    .set(SiteConfigDO::getAuthor, row.getAuthor())
                    .set(SiteConfigDO::getAuthorBio, row.getAuthorBio())
                    .set(SiteConfigDO::getAuthorTagline, row.getAuthorTagline())
                    .set(SiteConfigDO::getAboutContent, row.getAboutContent())
                    .set(SiteConfigDO::getTechStack, row.getTechStack())
                    .set(SiteConfigDO::getAvatar, row.getAvatar())
                    .set(SiteConfigDO::getUrl, row.getUrl())
                    .set(SiteConfigDO::getGithub, row.getGithub())
                    .set(SiteConfigDO::getRss, row.getRss())
                    .set(SiteConfigDO::getCommentEnabled, row.getCommentEnabled()));
        }

        apply(row);
        return siteProperties;
    }

    /** DB 非空字段覆盖到 SiteProperties 单例；NULL 字段保留 env/yaml 绑定值 */
    private void apply(SiteConfigDO d) {
        if (d.getTitle() != null) siteProperties.setTitle(d.getTitle());
        if (d.getSubtitle() != null) siteProperties.setSubtitle(d.getSubtitle());
        if (d.getDescription() != null) siteProperties.setDescription(d.getDescription());
        if (d.getAuthor() != null) siteProperties.setAuthor(d.getAuthor());
        if (d.getAuthorBio() != null) siteProperties.setAuthorBio(d.getAuthorBio());
        if (d.getAuthorTagline() != null) siteProperties.setAuthorTagline(d.getAuthorTagline());
        if (d.getAboutContent() != null) siteProperties.setAboutContent(d.getAboutContent());
        if (d.getTechStack() != null) siteProperties.setTechStack(d.getTechStack());
        if (d.getAvatar() != null) siteProperties.setAvatar(d.getAvatar());
        if (d.getUrl() != null) siteProperties.setUrl(d.getUrl());
        if (d.getGithub() != null) siteProperties.setGithub(d.getGithub());
        if (d.getRss() != null) siteProperties.setRss(d.getRss());
        if (d.getCommentEnabled() != null) siteProperties.setCommentEnabled(d.getCommentEnabled());
    }

    /** 空白串归一为 null（=清除覆盖值，回退 env 默认） */
    private String normalize(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }
}
