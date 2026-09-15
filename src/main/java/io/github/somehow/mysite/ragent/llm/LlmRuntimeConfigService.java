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
import io.github.somehow.mysite.ragent.service.KnowledgeBaseService;
import io.github.somehow.mysite.ragent.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
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
 * API Key 加密写入 PG；.env 只回写模型名，不再回写 Key。
 */
@Slf4j
@Service
@DependsOn("ragentSchemaMigration")
public class LlmRuntimeConfigService implements ApplicationRunner {

    private final RagProperties ragProperties;
    private final LlmProviderSettingMapper settingMapper;
    private final List<LLMProvider> providers;
    private final LLMService classificationLLM;
    private final ObjectProvider<BaiLianEmbeddingService> embedding;
    private final ObjectProvider<BaiLianRerankProvider> rerank;
    private final ObjectProvider<VectorStore> vectorStore;
    private final ObjectProvider<KnowledgeBaseService> knowledgeBases;
    private final SecretCrypto secretCrypto;

    public LlmRuntimeConfigService(RagProperties ragProperties,
                                   LlmProviderSettingMapper settingMapper,
                                   List<LLMProvider> providers,
                                   @Qualifier("classificationLLM") LLMService classificationLLM,
                                   ObjectProvider<BaiLianEmbeddingService> embedding,
                                   ObjectProvider<BaiLianRerankProvider> rerank,
                                   ObjectProvider<VectorStore> vectorStore,
                                   ObjectProvider<KnowledgeBaseService> knowledgeBases,
                                   SecretCrypto secretCrypto) {
        this.ragProperties = ragProperties;
        this.settingMapper = settingMapper;
        this.providers = providers;
        this.classificationLLM = classificationLLM;
        this.embedding = embedding;
        this.rerank = rerank;
        this.vectorStore = vectorStore;
        this.knowledgeBases = knowledgeBases;
        this.secretCrypto = secretCrypto;
    }

    @Override
    public void run(ApplicationArguments args) {
        onReady();
    }

    public void onReady() {
        List<LlmProviderSettingDO> rows;
        try {
            rows = settingMapper.selectList(null);
        } catch (Exception e) {
            log.warn("Failed to load persisted LLM settings, using yaml/env: {}", e.getMessage());
            return;
        }
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (LlmProviderSettingDO row : rows) {
            overlay(row);
        }
        migratePlaintextKeys(rows);
        refreshLiveClients();
        log.info("Applied {} persisted LLM provider override(s)", rows.size());
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
        String previousModel = p.getEmbeddingModel();
        int previousDim = effectiveDimension(p);
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
        if (req.getEmbeddingModel() != null && !req.getEmbeddingModel().isBlank()) {
            p.setEmbeddingModel(req.getEmbeddingModel().trim());
        }
        if (req.getEmbeddingDimension() != null) {
            p.setEmbeddingDimension(validateDimension(req.getEmbeddingDimension()));
        }
        if (req.getRerankModel() != null && !req.getRerankModel().isBlank()) {
            p.setRerankModel(req.getRerankModel().trim());
        }
        boolean keyChanged = StringUtils.hasText(req.getApiKey());
        if (keyChanged) {
            p.setApiKey(req.getApiKey().trim());
        }
        if (p.isEnabled() && LlmEnvFile.requiresApiKey(name) && !StringUtils.hasText(p.getApiKey())) {
            throw new ClientException("启用 " + name + " 需要先填写 API Key");
        }

        persist(name, p, keyChanged);
        syncEnvFile(name, p);
        if ("bailian".equalsIgnoreCase(name)) {
            invalidateEmbeddingsIfNeeded(p, previousModel, previousDim);
        }
        refreshLiveClients();
    }

    /**
     * 换 embedding 模型或维度后，旧向量与新查询不在同一语义空间。
     * 维度变了要改 PG 列；只换模型则清空向量。两种情况都把文档标 FAILED，等重建。
     */
    private void invalidateEmbeddingsIfNeeded(RagProperties.Provider p, String previousModel, int previousDim) {
        int newDim = effectiveDimension(p);
        boolean dimChanged = newDim != previousDim;
        boolean modelChanged = StringUtils.hasText(previousModel)
                && StringUtils.hasText(p.getEmbeddingModel())
                && !previousModel.equals(p.getEmbeddingModel());
        if (!dimChanged && !modelChanged) {
            return;
        }
        VectorStore store = vectorStore.getIfAvailable();
        if (store == null) {
            log.warn("Skip embedding invalidate: VectorStore unavailable");
            return;
        }
        try {
            if (dimChanged) {
                store.migrateEmbeddingDimension(newDim);
            } else {
                store.deleteAll();
            }
            KnowledgeBaseService kbs = knowledgeBases.getIfAvailable();
            if (kbs != null) {
                kbs.onGlobalEmbeddingChanged(p.getEmbeddingModel(), newDim);
            }
        } catch (Exception e) {
            throw new ClientException("更新 Embedding 后清理旧向量失败: " + e.getMessage());
        }
    }

    static int validateDimension(int dimension) {
        if (dimension < 64 || dimension > 4096) {
            throw new ClientException("Embedding 维度须在 64–4096 之间");
        }
        return dimension;
    }

    static int effectiveDimension(RagProperties.Provider p) {
        if (p == null || p.getEmbeddingDimension() == null || p.getEmbeddingDimension() <= 0) {
            return 1024;
        }
        return p.getEmbeddingDimension();
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
        if (StringUtils.hasText(row.getEmbeddingModel())) {
            p.setEmbeddingModel(row.getEmbeddingModel());
        }
        if (row.getEmbeddingDimension() != null && row.getEmbeddingDimension() > 0) {
            p.setEmbeddingDimension(row.getEmbeddingDimension());
        }
        if (StringUtils.hasText(row.getRerankModel())) {
            p.setRerankModel(row.getRerankModel());
        }
        if (StringUtils.hasText(row.getApiKey())) {
            p.setApiKey(secretCrypto.decrypt(row.getApiKey()));
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
        row.setEmbeddingDimension(p.getEmbeddingDimension());
        row.setRerankModel(p.getRerankModel());
        if (includeKey) {
            row.setApiKey(secretCrypto.encrypt(p.getApiKey()));
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

    /** 启动时把历史明文 API Key 就地升级为 enc:v1 密文。缺主密钥且已有密文由 {@link SecretCrypto#decrypt} 直接失败。 */
    private void migratePlaintextKeys(List<LlmProviderSettingDO> rows) {
        boolean anyPlaintext = false;
        for (LlmProviderSettingDO row : rows) {
            String stored = row.getApiKey();
            if (!StringUtils.hasText(stored) || secretCrypto.isCiphertext(stored)) {
                continue;
            }
            anyPlaintext = true;
            if (!secretCrypto.isConfigured()) {
                continue;
            }
            row.setApiKey(secretCrypto.encrypt(stored));
            row.setUpdateTime(LocalDateTime.now());
            settingMapper.updateById(row);
            log.info("Migrated plaintext LLM API key for {} to {}", row.getName(), SecretCrypto.PREFIX);
        }
        if (anyPlaintext && !secretCrypto.isConfigured()) {
            log.warn("PG 中仍有明文 LLM API Key；配置 {} 后将在下次启动加密", SecretCrypto.ENV_NAME);
        }
    }

    private void syncEnvFile(String name, RagProperties.Provider p) {
        Path file = envFile();
        if (file == null) {
            return;
        }
        Map<String, String> updates = new LinkedHashMap<>();
        String modelName = LlmEnvFile.chatModelEnvName(name);
        if (modelName != null && StringUtils.hasText(p.getChatModel())) {
            updates.put(modelName, p.getChatModel());
        }
        String embeddingName = LlmEnvFile.embeddingModelEnvName(name);
        if (embeddingName != null && StringUtils.hasText(p.getEmbeddingModel())) {
            updates.put(embeddingName, p.getEmbeddingModel());
        }
        String dimName = LlmEnvFile.embeddingDimensionEnvName(name);
        if (dimName != null && p.getEmbeddingDimension() != null && p.getEmbeddingDimension() > 0) {
            updates.put(dimName, String.valueOf(p.getEmbeddingDimension()));
        }
        String rerankName = LlmEnvFile.rerankModelEnvName(name);
        if (rerankName != null && StringUtils.hasText(p.getRerankModel())) {
            updates.put(rerankName, p.getRerankModel());
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
