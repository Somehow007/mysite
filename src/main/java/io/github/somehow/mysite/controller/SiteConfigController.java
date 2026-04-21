package io.github.somehow.mysite.controller;

import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "站点配置")
@RequestMapping("/v1/site")
public class SiteConfigController {

    @Operation(summary = "获取站点配置")
    @GetMapping("/config")
    public Result<Map<String, Object>> getSiteConfig() {
        Map<String, Object> config = Map.of(
                "title", "MySite",
                "description", "一个极简的个人博客",
                "author", "Admin",
                "url", "",
                "navigation", List.of(
                        Map.of("label", "首页", "path", "/"),
                        Map.of("label", "归档", "path", "/archive"),
                        Map.of("label", "关于", "path", "/about")
                )
        );
        return Results.success(config);
    }
}
