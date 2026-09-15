package io.github.somehow.mysite.ragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.somehow.mysite.ragent.config.RagProperties;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeBaseDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeDocumentDO;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeBaseMapper;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeChunkMapper;
import io.github.somehow.mysite.ragent.dao.mapper.KnowledgeDocumentMapper;
import io.github.somehow.mysite.ragent.dto.KnowledgeBaseDTO;
import io.github.somehow.mysite.ragent.vector.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeDocumentMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

    public List<KnowledgeBaseDTO> listAll() {
        List<KnowledgeBaseDO> kbs = kbMapper.selectList(null);
        List<KnowledgeBaseDTO> result = new ArrayList<>();
        for (KnowledgeBaseDO kb : kbs) {
            Long docCount = docMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeDocumentDO>()
                    .eq(KnowledgeDocumentDO::getKbId, kb.getId()));
            result.add(toDTO(kb, docCount.intValue()));
        }
        return result;
    }

    public KnowledgeBaseDTO getById(Long id) {
        KnowledgeBaseDO kb = kbMapper.selectById(id);
        if (kb == null) return null;
        Long docCount = docMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeDocumentDO>()
                .eq(KnowledgeDocumentDO::getKbId, id));
        return toDTO(kb, docCount.intValue());
    }

    public KnowledgeBaseDTO create(KnowledgeBaseDO kb) {
        if (kb.getCollectionName() == null || kb.getCollectionName().isBlank()) {
            // collection_name 有 NOT NULL 约束，前端不需要感知这个内部字段，后端自动生成
            String base = kb.getName().trim().replaceAll("\\s+", "_");
            if (base.isEmpty()) base = "kb";
            kb.setCollectionName(base + "_" + System.currentTimeMillis() % 100000);
        }
        if (kb.getChunkingMode() == null || kb.getChunkingMode().isBlank()) {
            kb.setChunkingMode("MARKDOWN_HEADING");
        }
        applyGlobalEmbedding(kb);
        kbMapper.insert(kb);
        return toDTO(kb, 0);
    }

    public KnowledgeBaseDTO update(Long id, KnowledgeBaseDO update) {
        KnowledgeBaseDO kb = kbMapper.selectById(id);
        if (kb == null) return null;
        update.setId(id);
        kbMapper.updateById(update);
        return getById(id);
    }

    public KnowledgeBaseDTO toggleEnabled(Long id) {
        KnowledgeBaseDO kb = kbMapper.selectById(id);
        if (kb == null) return null;
        boolean newEnabled = kb.getEnabled() == null || !kb.getEnabled();
        kb.setEnabled(newEnabled);
        kbMapper.updateById(kb);
        log.info("知识库 {} 已{}用: id={}, name={}",
            newEnabled ? "启" : "禁", id, kb.getName());
        return getById(id);
    }

    // 必须显式限定事务管理器：主库事务管理器是 @Primary（见 PrimaryDataSourceConfig），
    // 而本服务操作的是 PG 数据源
    @Transactional("ragentTransactionManager")
    public void delete(Long id) {
        KnowledgeBaseDO kb = kbMapper.selectById(id);
        if (kb == null) return;

        List<KnowledgeDocumentDO> docs = docMapper.selectList(
            new LambdaQueryWrapper<KnowledgeDocumentDO>()
                .eq(KnowledgeDocumentDO::getKbId, id));
        for (KnowledgeDocumentDO doc : docs) {
            vectorStore.deleteByDocId(doc.getId());
            chunkMapper.deleteByDocId(doc.getId());
        }
        docMapper.delete(new LambdaQueryWrapper<KnowledgeDocumentDO>()
            .eq(KnowledgeDocumentDO::getKbId, id));
        kbMapper.deleteById(id);
        log.info("知识库已删除: id={}, name={}, docs={}", id, kb.getName(), docs.size());
    }

    /**
     * 全局 embedding 模型或维度变更后：同步各知识库元数据，并把文档标为 FAILED 以便重新处理。
     */
    public void onGlobalEmbeddingChanged(String model, int dimension) {
        List<KnowledgeBaseDO> kbs = kbMapper.selectList(null);
        if (kbs == null) {
            return;
        }
        for (KnowledgeBaseDO kb : kbs) {
            if (StringUtils.hasText(model)) {
                kb.setEmbeddingModel(model);
            }
            kb.setEmbeddingDimension(dimension);
            kbMapper.updateById(kb);
        }
        int n = docMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocumentDO>()
                .in(KnowledgeDocumentDO::getStatus, List.of("READY", "PENDING", "CHUNKING"))
                .set(KnowledgeDocumentDO::getStatus, "FAILED")
                .set(KnowledgeDocumentDO::getFailReason,
                    "Embedding 已切换为 " + model + "（维度 " + dimension + "），请重新处理文档"));
        log.info("Global embedding changed to model={}, dim={}, kbs={}, docsMarkedFailed={}",
            model, dimension, kbs.size(), n);
    }

    private void applyGlobalEmbedding(KnowledgeBaseDO kb) {
        RagProperties.Provider bailian = ragProperties.getLlm().getProviders().get("bailian");
        if (bailian == null) {
            return;
        }
        if (bailian.getEmbeddingDimension() != null && bailian.getEmbeddingDimension() > 0) {
            kb.setEmbeddingDimension(bailian.getEmbeddingDimension());
        }
        if (StringUtils.hasText(bailian.getEmbeddingModel())) {
            kb.setEmbeddingModel(bailian.getEmbeddingModel());
        }
    }

    private KnowledgeBaseDTO toDTO(KnowledgeBaseDO kb, int docCount) {
        KnowledgeBaseDTO dto = new KnowledgeBaseDTO();
        dto.setId(String.valueOf(kb.getId()));
        dto.setName(kb.getName());
        dto.setDescription(kb.getDescription());
        dto.setCollectionName(kb.getCollectionName());
        dto.setEmbeddingModel(kb.getEmbeddingModel());
        dto.setEmbeddingDimension(kb.getEmbeddingDimension());
        dto.setChunkSize(kb.getChunkSize());
        dto.setChunkOverlap(kb.getChunkOverlap());
        dto.setChunkingMode(kb.getChunkingMode());
        dto.setEnabled(kb.getEnabled() != null ? kb.getEnabled() : true);
        dto.setDocCount(docCount);
        dto.setCreateTime(kb.getCreateTime());
        dto.setUpdateTime(kb.getUpdateTime());
        return dto;
    }
}
