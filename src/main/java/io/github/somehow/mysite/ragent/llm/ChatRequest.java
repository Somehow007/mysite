package io.github.somehow.mysite.ragent.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 聊天请求 DTO（含 ChatMessage）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    private String model;                // 模型名（如 qwen3-max）
    private List<ChatMessage> messages;
    private double temperature;          // 0.0~2.0，创作性越高越大
    private int maxTokens;              // 最大输出 token 数

    /** 快捷构造：单条用户消息 */
    public static ChatRequest of(String model, String userMessage) {
        return ChatRequest.builder()
            .model(model)
            .messages(List.of(ChatMessage.user(userMessage)))
            .temperature(0.7)
            .maxTokens(2048)
            .build();
    }
}

