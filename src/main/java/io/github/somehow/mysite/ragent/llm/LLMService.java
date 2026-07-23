package io.github.somehow.mysite.ragent.llm;

import io.github.somehow.mysite.ragent.llm.model.ChatRequest;
import reactor.core.publisher.Flux;

/**
 * 统一聊天接口——所有 LLM 供应商都实现这个接口
 *
 * 为什么用 Flux<String> 而不是 String？
 *  流式生成（Server-Sent Events）：LLM 是一个 token 一个 token 生成的。
 *  用 Flux 可以让每个 token 立刻推送到前端，用户看到打字机效果
 *  而不是等 30s 全部生成完才显示
 */
public interface LLMService {

    /**
     * 流式聊天（最常用）
     * @return 每个元素是一个 token（可能是 1 个或多个字符）
     */
    Flux<String> chatStream(ChatRequest request);

    /**
     * 同步聊天（用于非流式场景，如摘要生成、查询重写）
     */
    String chat(ChatRequest request);
}
