package io.github.somehow.mysite.ragent.llm;

import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.ragent.config.ClassifierLLMConfig;
import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.dao.entity.LlmProviderSettingDO;
import io.github.somehow.mysite.ragent.dao.mapper.LlmProviderSettingMapper;
import io.github.somehow.mysite.ragent.dto.LlmProviderPingDTO;
import io.github.somehow.mysite.ragent.dto.LlmProviderUpdateRequest;
import io.github.somehow.mysite.ragent.llm.embedding.BaiLianEmbeddingService;
import io.github.somehow.mysite.ragent.llm.model.ChatMessage;
import io.github.somehow.mysite.ragent.llm.model.ChatRequest;
import io.github.somehow.mysite.ragent.llm.provider.AbstractOpenAiProvider;
import io.github.somehow.mysite.ragent.llm.rerank.BaiLianRerankProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台修改模型 / API Key 的运行时入口：覆盖 {@link RagProperties}、热更新客户端、
 * 写入 PG，并在探测到 .env 时回写 Key / chat-model，与生产 {@code start.sh load_env()} 对齐。
 */
@Slf4j
@Service
@DependsOn("ragentSchemaMigration")
public class LlmRuntimeConfigService {

    private final RagProperties ragProperties;
    private final LlmProviderSettingMapper settingMapper;
    private final List<LLMProvider> providers;
    private final LLMService classificationLLM;
    private final ObjectProvider<BaiLianEmbeddingService> embedding;
    private final ObjectProvider<BaiLianRerankProvider> rerank;

    public LlmRuntimeConfigService(RagProperties ragProperties,
                                   LlmProviderSettingMapper settingMapper,
                                   List<LLMProvider> providers,
                                   @Qualifier("classificationLLM") LLMService classificationLLM,
                                   ObjectProvider<BaiLianEmbeddingService> embedding,
                                   ObjectProvider<BaiLianRerankProvider> rerank) {
        this.ragProperties = ragProperties;
        this.settingMapper = settingMapper;
        this.providers = providers;
        this.classificationLLM = classificationLLM;
        this.embedding = embedding;
        this.rerank = rerank;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            List<LlmProviderSettingDO> rows = settingMapper.selectList(null);
            if (rows == null || rows.isEmpty()) {
                return;
            }
            for (LlmProviderSettingDO row : rows) {
                overlay(row);
            }
            refreshLiveClients();
            log.info("Applied {} persisted LLM provider override(s)", rows.size());
        } catch (Exception e) {
            log.warn("Failed to apply persisted LLM settings, using yaml/env: {}", e.getMessage());
        }
    }

    public Path envFile() {
        return LlmEnvFile.resolve(ragProperties);
    }

    public Set<String> boundProviderNames() {
        return providers.stream().map(LLMProvider::getName).collect(Collectors.toSet());
    }

    public boolean isPersisted(String name) {
        return settingMapper.selectById(name) != null;
    }

    public Set<String> persistedNames() {
        List<LlmProviderSettingDO> rows = settingMapper.selectList(null);
        if (rows == null || rows.isEmpty()) {
            return Set.of();
        }
        return rows.stream().map(LlmProviderSettingDO::getName).collect(Collectors.toSet());
    }

    public RagProperties.Provider requireProvider(String name) {
        RagProperties.Provider p = ragProperties.getLlm().getProviders().get(name);
        if (p == null) {
            throw new ClientException("未知的 LLM 供应商: " + name);
        }
        return p;
    }

    public synchronized void update(String name, LlmProviderUpdateRequest req) {
        RagProperties.Provider p = requireProvider(name);
        if (req.getEnabled() != null) {
            p.setEnabled(req.getEnabled());
        }
        if (req.getPriority() != null) {
            p.setPriority(req.getPriority());
        }
        if (req.getBaseUrl() != null && !req.getBaseUrl().isBlank()) {
            p.setBaseUrl(req.getBaseUrl().trim());
        }
        if (req.getChatModel() != null && !req.getChatModel().isBlank()) {
            p.setChatModel(req.getChatModel().trim());
        }
        boolean keyChanged = StringUtils.hasText(req.getApiKey());
        if (keyChanged) {
            p.setApiKey(req.getApiKey().trim());
        }
        if (p.isEnabled() && LlmEnvFile.requiresApiKey(name) && !StringUtils.hasText(p.getApiKey())) {
            throw new ClientException("启用 " + name + " 需要先填写 API Key");
        }

        persist(name, p, keyChanged);
        syncEnvFile(name, p, keyChanged);
        refreshLiveClients();
    }

    public LlmProviderPingDTO ping(String name) {
        requireProvider(name);
        LLMProvider provider = providers.stream()
                .filter(p -> name.equals(p.getName()))
                .findFirst()
                .orElseThrow(() -> new ClientException("该供应商尚未接入运行时，无法测试: " + name));
        RagProperties.Provider cfg = ragProperties.getLlm().getProviders().get(name);
        LlmProviderPingDTO dto = new LlmProviderPingDTO();
        dto.setProvider(name);
        dto.setModel(cfg != null ? cfg.getChatModel() : null);
        long t0 = System.currentTimeMillis();
        try {
            String preview = provider.chat(ChatRequest.builder()
                    .messages(List.of(ChatMessage.user("ping")))
                    .temperature(0)
                    .maxTokens(8)
                    .build());
            dto.setOk(true);
            dto.setPreview(preview != null && preview.length() > 80 ? preview.substring(0, 80) : preview);
            dto.setMessage("连通");
        } catch (Exception e) {
            dto.setOk(false);
            dto.setMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        dto.setLatencyMs(System.currentTimeMillis() - t0);
        return dto;
    }

    public void refreshLiveClients() {
        Map<String, RagProperties.Provider> map = ragProperties.getLlm().getProviders();
        for (LLMProvider provider : providers) {
            RagProperties.Provider cfg = map.get(provider.getName());
            if (cfg == null) {
                continue;
            }
            if (provider instanceof AbstractOpenAiProvider openAi) {
                Duration timeout = cfg.getChatTimeout() != null ? cfg.getChatTimeout() : Duration.ofSeconds(120);
                openAi.applyRuntime(cfg.getBaseUrl(), cfg.getApiKey(), cfg.getChatModel(), timeout);
            }
        }
        RagProperties.Provider bailian = map.get("bailian");
        BaiLianEmbeddingService emb = embedding.getIfAvailable();
        if (emb != null && bailian != null) {
            emb.applyRuntime(bailian);
        }
        BaiLianRerankProvider rr = rerank.getIfAvailable();
        if (rr != null && bailian != null) {
            rr.applyRuntime(bailian);
        }
        if (classificationLLM instanceof AbstractOpenAiProvider cheap) {
            RagProperties.Provider best = ClassifierLLMConfig.findBestProvider(ragProperties);
            if (best != null) {
                Duration timeout = best.getChatTimeout() != null ? best.getChatTimeout() : Duration.ofSeconds(30);
                cheap.applyRuntime(best.getBaseUrl(), best.getApiKey(),
                    ClassifierLLMConfig.resolveCheapModel(best), timeout);
            }
        }
    }

    private void overlay(LlmProviderSettingDO row) {
        RagProperties.Provider p = ragProperties.getLlm().getProviders().get(row.getName());
        if (p == null) {
            log.warn("Ignoring persisted LLM setting for unknown provider {}", row.getName());
            return;
        }
        if (row.getEnabled() != null) {
            p.setEnabled(row.getEnabled());
        }
        if (row.getPriority() != null) {
            p.setPriority(row.getPriority());
        }
        if (StringUtils.hasText(row.getBaseUrl())) {
            p.setBaseUrl(row.getBaseUrl());
        }
        if (StringUtils.hasText(row.getChatModel())) {
            p.setChatModel(row.getChatModel());
        }
        if (StringUtils.hasText(row.getApiKey())) {
            p.setApiKey(row.getApiKey());
        }
    }

    private void persist(String name, RagProperties.Provider p, boolean includeKey) {
        LlmProviderSettingDO existing = settingMapper.selectById(name);
        LlmProviderSettingDO row = existing != null ? existing : new LlmProviderSettingDO();
        row.setName(name);
        row.setEnabled(p.isEnabled());
        row.setPriority(p.getPriority());
        row.setBaseUrl(p.getBaseUrl());
        row.setChatModel(p.getChatModel());
        row.setEmbeddingModel(p.getEmbeddingModel());
        row.setRerankModel(p.getRerankModel());
        if (includeKey) {
            row.setApiKey(p.getApiKey());
        } else if (existing == null) {
            row.setApiKey(null);
        }
        row.setUpdateTime(LocalDateTime.now());
        if (existing == null) {
            settingMapper.insert(row);
        } else {
            settingMapper.updateById(row);
        }
    }

    private void syncEnvFile(String name, RagProperties.Provider p, boolean includeKey) {
        Path file = envFile();
        if (file == null) {
            return;
        }
        Map<String, String> updates = new LinkedHashMap<>();
        if (includeKey) {
            String keyName = LlmEnvFile.apiKeyEnvName(name);
            if (keyName != null) {
                updates.put(keyName, p.getApiKey() != null ? p.getApiKey() : "");
            }
        }
        String modelName = LlmEnvFile.chatModelEnvName(name);
        if (modelName != null && StringUtils.hasText(p.getChatModel())) {
            updates.put(modelName, p.getChatModel());
        }
        if (updates.isEmpty()) {
            return;
        }
        try {
            LlmEnvFile.upsert(file, updates);
            log.info("Wrote LLM env overrides for {} to {}", name, file);
        } catch (Exception e) {
            log.warn("Failed to write {}: {}", file, e.getMessage());
        }
    }
}
