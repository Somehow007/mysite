package io.github.somehow.mysite.ragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.dao.entity.LlmUsageDO;
import io.github.somehow.mysite.ragent.dao.mapper.LlmUsageMapper;
import io.github.somehow.mysite.ragent.dto.LlmProviderPingDTO;
import io.github.somehow.mysite.ragent.dto.LlmProviderUpdateRequest;
import io.github.somehow.mysite.ragent.dto.LlmProviderViewDTO;
import io.github.somehow.mysite.ragent.dto.LlmUsageLogDTO;
import io.github.somehow.mysite.ragent.dto.LlmUsageSummaryDTO;
import io.github.somehow.mysite.ragent.llm.LlmEnvFile;
import io.github.somehow.mysite.ragent.llm.LlmRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminAiService {

    private final LlmUsageMapper usageMapper;
    private final RagProperties ragProperties;
    private final LlmRuntimeConfigService runtimeConfig;

    public IPage<LlmUsageLogDTO> listLogs(long current, long size,
                                          Instant from, Instant to,
                                          String callType, String provider, String model,
                                          String keyword, Boolean success, String traceId) {
        Page<LlmUsageDO> page = new Page<>(current, size);
        LambdaQueryWrapper<LlmUsageDO> wrapper = new LambdaQueryWrapper<LlmUsageDO>()
                .orderByDesc(LlmUsageDO::getCreateTime);
        if (from != null) {
            wrapper.ge(LlmUsageDO::getCreateTime, toLdt(from));
        }
        if (to != null) {
            wrapper.lt(LlmUsageDO::getCreateTime, toLdt(to));
        }
        if (StringUtils.hasText(callType)) {
            wrapper.eq(LlmUsageDO::getCallType, callType);
        }
        if (StringUtils.hasText(provider)) {
            wrapper.eq(LlmUsageDO::getProvider, provider);
        }
        if (StringUtils.hasText(model)) {
            wrapper.eq(LlmUsageDO::getModel, model);
        }
        if (success != null) {
            wrapper.eq(LlmUsageDO::getSuccess, success);
        }
        if (StringUtils.hasText(traceId)) {
            wrapper.eq(LlmUsageDO::getTraceId, traceId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(LlmUsageDO::getUsername, keyword)
                    .or().like(LlmUsageDO::getVisitorId, keyword));
        }
        IPage<LlmUsageDO> raw = usageMapper.selectPage(page, wrapper);
        return raw.convert(this::toLogDto);
    }

    public LlmUsageSummaryDTO summary(Instant from, Instant to) {
        LocalDateTime end = to != null ? toLdt(to) : LocalDateTime.now().plusSeconds(1);
        LocalDateTime start = from != null ? toLdt(from) : end.minusDays(7);

        Map<String, Object> totals = usageMapper.selectTotals(start, end);
        LlmUsageSummaryDTO dto = new LlmUsageSummaryDTO();
        dto.setCurrency(ragProperties.getLlm().getPricing().getCurrency());
        dto.setTotalCost(decimal(totals, "total_cost"));
        dto.setTotalTokens(longVal(totals, "total_tokens"));
        dto.setTotalCalls(longVal(totals, "total_calls"));
        long successCalls = longVal(totals, "success_calls");
        dto.setSuccessRate(dto.getTotalCalls() == 0 ? 0
                : (successCalls * 100.0 / dto.getTotalCalls()));

        for (Map<String, Object> row : usageMapper.selectDaily(start, end)) {
            LlmUsageSummaryDTO.DailyPoint p = new LlmUsageSummaryDTO.DailyPoint();
            p.setDay(String.valueOf(row.get("day")));
            p.setCost(decimal(row, "cost"));
            p.setTokens(longVal(row, "tokens"));
            p.setCalls(longVal(row, "calls"));
            dto.getSeries().add(p);
        }
        for (Map<String, Object> row : usageMapper.selectByModel(start, end)) {
            LlmUsageSummaryDTO.NamedSlice s = new LlmUsageSummaryDTO.NamedSlice();
            s.setName(str(row, "model"));
            s.setProvider(str(row, "provider"));
            s.setCost(decimal(row, "cost"));
            s.setTokens(longVal(row, "tokens"));
            s.setCalls(longVal(row, "calls"));
            dto.getByModel().add(s);
        }
        for (Map<String, Object> row : usageMapper.selectByCallType(start, end)) {
            LlmUsageSummaryDTO.NamedSlice s = new LlmUsageSummaryDTO.NamedSlice();
            s.setName(str(row, "call_type"));
            s.setCost(decimal(row, "cost"));
            s.setTokens(longVal(row, "tokens"));
            s.setCalls(longVal(row, "calls"));
            dto.getByCallType().add(s);
        }
        for (Map<String, Object> row : usageMapper.selectTopUsers(start, end)) {
            LlmUsageSummaryDTO.UserSlice u = new LlmUsageSummaryDTO.UserSlice();
            u.setActor(str(row, "actor"));
            Object uid = row.get("user_id");
            u.setUserId(uid == null ? null : String.valueOf(uid));
            u.setVisitorId(str(row, "visitor_id"));
            u.setCost(decimal(row, "cost"));
            u.setTokens(longVal(row, "tokens"));
            u.setCalls(longVal(row, "calls"));
            dto.getByUser().add(u);
        }
        return dto;
    }

    public List<LlmProviderViewDTO> listProviders() {
        List<LlmProviderViewDTO> list = new ArrayList<>();
        String envPath = runtimeConfig.envFile() != null ? runtimeConfig.envFile().toString() : null;
        Set<String> bound = runtimeConfig.boundProviderNames();
        Set<String> persisted = runtimeConfig.persistedNames();
        ragProperties.getLlm().getProviders().forEach((name, p) -> {
            LlmProviderViewDTO dto = new LlmProviderViewDTO();
            dto.setName(name);
            dto.setEnabled(p.isEnabled());
            dto.setPriority(p.getPriority());
            dto.setBaseUrl(p.getBaseUrl());
            dto.setApiKeyMasked(maskKey(p.getApiKey()));
            dto.setChatModel(p.getChatModel());
            dto.setEmbeddingModel(p.getEmbeddingModel());
            dto.setRerankModel(p.getRerankModel());
            dto.setConfigured(StringUtils.hasText(p.getApiKey()) || !LlmEnvFile.requiresApiKey(name));
            dto.setEnvApiKeyName(LlmEnvFile.apiKeyEnvName(name));
            dto.setEnvChatModelName(LlmEnvFile.chatModelEnvName(name));
            dto.setEnvFile(envPath);
            dto.setRuntimeBound(bound.contains(name));
            dto.setPersisted(persisted.contains(name));
            dto.setRequiresApiKey(LlmEnvFile.requiresApiKey(name));
            list.add(dto);
        });
        list.sort(Comparator.comparingInt(LlmProviderViewDTO::getPriority));
        return list;
    }

    public List<LlmProviderViewDTO> updateProvider(String name, LlmProviderUpdateRequest req) {
        runtimeConfig.update(name, req);
        return listProviders();
    }

    public LlmProviderPingDTO pingProvider(String name) {
        return runtimeConfig.ping(name);
    }

    private LlmUsageLogDTO toLogDto(LlmUsageDO row) {
        LlmUsageLogDTO dto = new LlmUsageLogDTO();
        BeanUtils.copyProperties(row, dto);
        return dto;
    }

    private static LocalDateTime toLdt(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    static String maskKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, Math.min(3, key.length())) + "****" + key.substring(key.length() - 4);
    }

    private static BigDecimal decimal(Map<String, Object> row, String key) {
        if (row == null) {
            return BigDecimal.ZERO;
        }
        Object v = row.get(key);
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        return new BigDecimal(v.toString());
    }

    private static long longVal(Map<String, Object> row, String key) {
        if (row == null) {
            return 0;
        }
        Object v = row.get(key);
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(v.toString());
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : v.toString();
    }
}
