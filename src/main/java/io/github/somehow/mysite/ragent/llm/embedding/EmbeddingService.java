package io.github.somehow.mysite.ragent.llm.embedding;

import java.util.List;

/**
 * 统一嵌入接口 —— 把文本变成向量
 */
public interface EmbeddingService {

    /**
     * 单条文本嵌入
     *
     * @param text  待嵌入文本
     * @return      向量，如 float[1024]
     */
    float[] embed(String text);

    /**
     * 批量嵌入（用于批量索引入库，一次 API 调用处理多条文本）
     *
     * @param texts 待嵌入文本列表
     * @return      向量列表，每个向量对应一条输入文本
     */
    List<float[]> embedBatch(List<String> texts);
}
