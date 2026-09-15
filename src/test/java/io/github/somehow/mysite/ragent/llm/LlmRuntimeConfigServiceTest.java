package io.github.somehow.mysite.ragent.llm;

import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.dao.entity.LlmProviderSettingDO;
import io.github.somehow.mysite.ragent.dao.mapper.LlmProviderSettingMapper;
import io.github.somehow.mysite.ragent.dto.LlmProviderUpdateRequest;
import io.github.somehow.mysite.ragent.service.KnowledgeBaseService;
import io.github.somehow.mysite.ragent.vector.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("LlmRuntimeConfigService — API Key 加密存储")
class LlmRuntimeConfigServiceTest {

    private RagProperties ragProperties;
    private LlmProviderSettingMapper settingMapper;
    private SecretCrypto crypto;
    private LlmRuntimeConfigService service;
    private VectorStore vectorStore;
    private KnowledgeBaseService knowledgeBases;
    private ObjectProvider<VectorStore> vectorStoreProvider;
    private ObjectProvider<KnowledgeBaseService> knowledgeBasesProvider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ragProperties = new RagProperties();
        RagProperties.Provider deepseek = new RagProperties.Provider();
        deepseek.setEnabled(true);
        deepseek.setChatModel("deepseek-v4-flash");
        deepseek.setApiKey("yaml-key");
        ragProperties.getLlm().getProviders().put("deepseek", deepseek);
        ragProperties.getLlm().getProviders().put("bailian", bailianProvider());
        ragProperties.getLlm().setEnvFile("mysite-test-missing.env");

        settingMapper = mock(LlmProviderSettingMapper.class);
        vectorStore = mock(VectorStore.class);
        knowledgeBases = mock(KnowledgeBaseService.class);
        vectorStoreProvider = mock(ObjectProvider.class);
        knowledgeBasesProvider = mock(ObjectProvider.class);
        when(vectorStoreProvider.getIfAvailable()).thenReturn(vectorStore);
        when(knowledgeBasesProvider.getIfAvailable()).thenReturn(knowledgeBases);
        crypto = new SecretCrypto(masterKey());
        service = newService(crypto);
    }

    @Nested
    @DisplayName("persist 加密")
    class Persist {

        @Test
        @DisplayName("后台保存的 Key 入库为 enc:v1 密文，内存仍是明文")
        void persistEncryptsKey() {
            when(settingMapper.selectById("deepseek")).thenReturn(null);
            LlmProviderUpdateRequest req = new LlmProviderUpdateRequest();
            req.setApiKey("sk-secret");

            service.update("deepseek", req);

            ArgumentCaptor<LlmProviderSettingDO> captor = ArgumentCaptor.forClass(LlmProviderSettingDO.class);
            verify(settingMapper).insert(captor.capture());
            String stored = captor.getValue().getApiKey();
            assertTrue(stored.startsWith(SecretCrypto.PREFIX));
            assertFalse(stored.contains("sk-secret"));
            assertEquals("sk-secret", crypto.decrypt(stored));
            assertEquals("sk-secret", provider().getApiKey());
        }

        @Test
        @DisplayName("缺主密钥时拒绝保存 Key")
        void persistWithoutMasterKeyFails() {
            service = newService(new SecretCrypto(""));
            when(settingMapper.selectById("deepseek")).thenReturn(null);
            LlmProviderUpdateRequest req = new LlmProviderUpdateRequest();
            req.setApiKey("sk-secret");

            ClientException ex = assertThrows(ClientException.class, () -> service.update("deepseek", req));
            assertTrue(ex.getMessage().contains(SecretCrypto.ENV_NAME));
            verify(settingMapper, never()).insert(any(LlmProviderSettingDO.class));
        }
    }

    @Nested
    @DisplayName("overlay 解密")
    class Overlay {

        @Test
        @DisplayName("启动时把密文解到内存")
        void decryptsCiphertextIntoMemory() {
            LlmProviderSettingDO row = new LlmProviderSettingDO();
            row.setName("deepseek");
            row.setChatModel("db-model");
            row.setApiKey(crypto.encrypt("sk-from-db"));
            when(settingMapper.selectList(null)).thenReturn(List.of(row));

            service.onReady();

            assertEquals("sk-from-db", provider().getApiKey());
            assertEquals("db-model", provider().getChatModel());
            verify(settingMapper, never()).updateById(any(LlmProviderSettingDO.class));
        }
    }

    @Nested
    @DisplayName("明文迁移")
    class Migration {

        @Test
        @DisplayName("有主密钥时把历史明文就地加密")
        void upgradesPlaintextInPlace() {
            LlmProviderSettingDO row = new LlmProviderSettingDO();
            row.setName("deepseek");
            row.setApiKey("plain-sk");
            when(settingMapper.selectList(null)).thenReturn(List.of(row));

            service.onReady();

            ArgumentCaptor<LlmProviderSettingDO> captor = ArgumentCaptor.forClass(LlmProviderSettingDO.class);
            verify(settingMapper).updateById(captor.capture());
            String stored = captor.getValue().getApiKey();
            assertTrue(stored.startsWith(SecretCrypto.PREFIX));
            assertEquals("plain-sk", crypto.decrypt(stored));
            assertEquals("plain-sk", provider().getApiKey());
        }

        @Test
        @DisplayName("缺主密钥且全是明文：警告并继续，yaml 引导 Key 仍可用")
        void plaintextWithoutKeyKeepsRunning() {
            service = newService(new SecretCrypto(""));
            LlmProviderSettingDO row = new LlmProviderSettingDO();
            row.setName("deepseek");
            row.setApiKey("plain-sk");
            when(settingMapper.selectList(null)).thenReturn(List.of(row));

            service.onReady();

            assertEquals("plain-sk", provider().getApiKey());
            verify(settingMapper, never()).updateById(any(LlmProviderSettingDO.class));
        }

        @Test
        @DisplayName("缺主密钥且库里已有密文：启动失败")
        void ciphertextWithoutKeyFailsStartup() {
            service = newService(new SecretCrypto(""));
            LlmProviderSettingDO row = new LlmProviderSettingDO();
            row.setName("deepseek");
            row.setApiKey(SecretCrypto.PREFIX + "AAAA");
            when(settingMapper.selectList(null)).thenReturn(List.of(row));

            ClientException ex = assertThrows(ClientException.class, service::onReady);
            assertTrue(ex.getMessage().contains("拒绝启动"));
        }
    }

    @Nested
    @DisplayName("不再回写 .env 中的 Key")
    class EnvFile {

        @TempDir
        Path tmp;

        @Test
        @DisplayName("保存 Key 与模型时只回写模型名")
        void doesNotWriteApiKey() throws Exception {
            Path env = tmp.resolve(".env");
            Files.writeString(env, """
                    DEEPSEEK_API_KEY=keep-me
                    DEEPSEEK_CHAT_MODEL=old
                    """);
            ragProperties.getLlm().setEnvFile(env.toAbsolutePath().toString());
            when(settingMapper.selectById("deepseek")).thenReturn(null);

            LlmProviderUpdateRequest req = new LlmProviderUpdateRequest();
            req.setApiKey("sk-new");
            req.setChatModel("new-model");
            service.update("deepseek", req);

            String text = Files.readString(env, StandardCharsets.UTF_8);
            assertTrue(text.contains("DEEPSEEK_API_KEY=keep-me"));
            assertFalse(text.contains("sk-new"));
            assertTrue(text.contains("DEEPSEEK_CHAT_MODEL=new-model"));
        }
    }

    @Nested
    @DisplayName("换 Embedding 后清理旧向量")
    class EmbeddingInvalidate {

        @BeforeEach
        void stubInsert() {
            when(settingMapper.selectById("bailian")).thenReturn(null);
        }

        @Test
        @DisplayName("只改模型：清空向量并标记文档")
        void modelChangeDeletesAll() {
            LlmProviderUpdateRequest req = new LlmProviderUpdateRequest();
            req.setEmbeddingModel("text-embedding-v3");

            service.update("bailian", req);

            verify(vectorStore).deleteAll();
            verify(vectorStore, never()).migrateEmbeddingDimension(anyInt());
            verify(knowledgeBases).onGlobalEmbeddingChanged("text-embedding-v3", 1024);
        }

        @Test
        @DisplayName("只改维度：改 PG 列，不清空后再 deleteAll")
        void dimensionChangeMigratesColumn() {
            LlmProviderUpdateRequest req = new LlmProviderUpdateRequest();
            req.setEmbeddingDimension(768);

            service.update("bailian", req);

            verify(vectorStore).migrateEmbeddingDimension(768);
            verify(vectorStore, never()).deleteAll();
            verify(knowledgeBases).onGlobalEmbeddingChanged("text-embedding-v4", 768);
        }

        @Test
        @DisplayName("模型与维度都没变：不碰向量")
        void unchangedSkipsInvalidate() {
            LlmProviderUpdateRequest req = new LlmProviderUpdateRequest();
            req.setEmbeddingModel("text-embedding-v4");
            req.setEmbeddingDimension(1024);
            req.setChatModel("qwen3-max");

            service.update("bailian", req);

            verify(vectorStore, never()).deleteAll();
            verify(vectorStore, never()).migrateEmbeddingDimension(anyInt());
            verify(knowledgeBases, never()).onGlobalEmbeddingChanged(any(), anyInt());
        }
    }

    @SuppressWarnings("unchecked")
    private LlmRuntimeConfigService newService(SecretCrypto secretCrypto) {
        return new LlmRuntimeConfigService(
            ragProperties,
            settingMapper,
            List.of(),
            mock(LLMService.class),
            mock(ObjectProvider.class),
            mock(ObjectProvider.class),
            vectorStoreProvider,
            knowledgeBasesProvider,
            secretCrypto);
    }

    private RagProperties.Provider provider() {
        return ragProperties.getLlm().getProviders().get("deepseek");
    }

    private static RagProperties.Provider bailianProvider() {
        RagProperties.Provider bailian = new RagProperties.Provider();
        bailian.setEnabled(true);
        bailian.setChatModel("qwen3-max");
        bailian.setEmbeddingModel("text-embedding-v4");
        bailian.setEmbeddingDimension(1024);
        bailian.setRerankModel("qwen3-rerank");
        bailian.setApiKey("bailian-key");
        return bailian;
    }

    private static String masterKey() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }
}
