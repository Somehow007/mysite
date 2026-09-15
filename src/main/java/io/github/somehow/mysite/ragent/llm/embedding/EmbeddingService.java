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

    /**
     * 当前实际用来生成向量的模型名。入库必须写这个名字，而不是知识库创建时记下的旧值。
     * 未实现时返回 {@code null}，调用方回退到知识库上的 {@code embeddingModel}。
     */
    default String currentModel() {
        return null;
    }
}
