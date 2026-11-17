package io.github.somehow.mysite.ragent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.ragent.dto.LlmBillingDTO;
import io.github.somehow.mysite.ragent.dto.LlmHolidayDTO;
import io.github.somehow.mysite.ragent.dto.LlmHolidaysUpdateRequest;
import io.github.somehow.mysite.ragent.dto.LlmModelPriceDTO;
import io.github.somehow.mysite.ragent.dto.LlmModelPriceUpsertRequest;
import io.github.somehow.mysite.ragent.dto.LlmProviderPingDTO;
import io.github.somehow.mysite.ragent.dto.LlmProviderUpdateRequest;
import io.github.somehow.mysite.ragent.dto.LlmProviderViewDTO;
import io.github.somehow.mysite.ragent.dto.LlmUsageLogDTO;
import io.github.somehow.mysite.ragent.dto.LlmUsageSummaryDTO;
import io.github.somehow.mysite.ragent.service.AdminAiService;
import io.github.somehow.mysite.ragent.service.AdminBillingService;
import io.github.somehow.mysite.ragent.service.AdminPricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final AdminPricingService pricingService;
    private final AdminBillingService billingService;

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

    @Operation(summary = "当前 LLM 供应商（Key 脱敏）")
    @GetMapping("/providers")
    public Result<List<LlmProviderViewDTO>> providers() {
        return Results.success(adminAiService.listProviders());
    }

    @Operation(summary = "更新 LLM 供应商（热生效；Key 加密入库，模型名回写 .env）")
    @PutMapping("/providers/{name}")
    public Result<List<LlmProviderViewDTO>> updateProvider(
            @PathVariable String name,
            @RequestBody LlmProviderUpdateRequest request) {
        return Results.success(adminAiService.updateProvider(name, request));
    }

    @Operation(summary = "测试供应商连通性（短请求）")
    @PostMapping("/providers/{name}/ping")
    public Result<LlmProviderPingDTO> pingProvider(@PathVariable String name) {
        return Results.success(adminAiService.pingProvider(name));
    }

    // ── 计价 ──

    @Operation(summary = "模型价卡列表（含历史版本）")
    @GetMapping("/prices")
    public Result<List<LlmModelPriceDTO>> prices() {
        return Results.success(pricingService.listPrices());
    }

    @Operation(summary = "新建价卡")
    @PostMapping("/prices")
    public Result<List<LlmModelPriceDTO>> createPrice(@RequestBody LlmModelPriceUpsertRequest request) {
        return Results.success(pricingService.createPrice(request));
    }

    @Operation(summary = "修订价卡（版本化：闭合旧版 + 插新版）")
    @PutMapping("/prices/{id}")
    public Result<List<LlmModelPriceDTO>> revisePrice(
            @PathVariable Long id, @RequestBody LlmModelPriceUpsertRequest request) {
        return Results.success(pricingService.revisePrice(id, request));
    }

    @Operation(summary = "停用价卡当前版本（effective_to=now）")
    @DeleteMapping("/prices/{id}")
    public Result<List<LlmModelPriceDTO>> retirePrice(@PathVariable Long id) {
        return Results.success(pricingService.retirePrice(id));
    }

    // ── 节假日（DeepSeek 峰谷排除依据） ──

    @Operation(summary = "法定节假日清单")
    @GetMapping("/holidays")
    public Result<List<LlmHolidayDTO>> holidays() {
        return Results.success(pricingService.listHolidays());
    }

    @Operation(summary = "全量替换节假日清单")
    @PutMapping("/holidays")
    public Result<List<LlmHolidayDTO>> replaceHolidays(@RequestBody LlmHolidaysUpdateRequest request) {
        return Results.success(pricingService.replaceHolidays(request));
    }

    // ── 官方账单对账 ──

    @Operation(summary = "拉取各 provider 官方余额/配额并与上一快照对账")
    @GetMapping("/billing")
    public Result<LlmBillingDTO> billing() {
        return Results.success(billingService.query());
    }
}
