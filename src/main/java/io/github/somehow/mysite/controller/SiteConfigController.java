package io.github.somehow.mysite.controller;

import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.config.SiteProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "站点配置")
@RequestMapping("/v1/site")
public class SiteConfigController {

    private final SiteProperties siteProperties;

    @Operation(summary = "获取站点配置")
    @GetMapping("/config")
    public Result<SiteProperties> getSiteConfig() {
        return Results.success(siteProperties);
    }
}
