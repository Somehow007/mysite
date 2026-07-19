package io.github.somehow.mysite.ragent.llm;

/**
 * 供应商标记接口（防路由自注入）
 * RoutingLLMService 注入 List<LLMProvider> 而不是 List<LLMService>
 * 避免把自己（也是 LLMService）注入进来造成循环依赖
 */
public interface LLMProvider extends LLMService {

    /**
     * 供应商标识、与配置 rag.llm.providers.{name} 对应，如 "bailian"
     */
    String getName();
}
