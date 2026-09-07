package io.github.somehow.mysite.ragent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.ragent.dto.LlmProviderViewDTO;
import io.github.somehow.mysite.ragent.dto.LlmUsageLogDTO;
import io.github.somehow.mysite.ragent.dto.LlmUsageSummaryDTO;
import io.github.somehow.mysite.ragent.service.AdminAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/ai")
@Tag(name = "AI 管理（管理员）")
public class AdminAiController {

    private final AdminAiService adminAiService;

    @Operation(summary = "调用记录分页")
    @GetMapping("/logs")
    public Result<IPage<LlmUsageLogDTO>> logs(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String callType,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String traceId) {
        return Results.success(adminAiService.listLogs(
                current, size, from, to, callType, provider, model, keyword, success, traceId));
    }

    @Operation(summary = "消费看板汇总")
    @GetMapping("/summary")
    public Result<LlmUsageSummaryDTO> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return Results.success(adminAiService.summary(from, to));
    }

    @Operation(summary = "当前 LLM 供应商（只读，Key 脱敏）")
    @GetMapping("/providers")
    public Result<List<LlmProviderViewDTO>> providers() {
        return Results.success(adminAiService.listProviders());
    }
}
