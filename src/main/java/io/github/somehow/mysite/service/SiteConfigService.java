package io.github.somehow.mysite.service;

import io.github.somehow.mysite.config.SiteProperties;
import io.github.somehow.mysite.dto.req.site.SiteConfigUpdateReqDTO;

public interface SiteConfigService {

    /**
     * 全量更新站点配置（后台表单语义）：字符串空白 → 清除覆盖值回退 env/yaml 默认；
     * 保存后立即覆盖到 {@link SiteProperties} 单例，GET /v1/site/config 即时生效。
     */
    SiteProperties update(SiteConfigUpdateReqDTO dto);
}
