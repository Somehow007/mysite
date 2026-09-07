package io.github.somehow.mysite.ragent.usage;

import com.alibaba.ttl.TransmittableThreadLocal;
import lombok.Builder;
import lombok.Data;

/**
 * 一次计费调用的业务上下文。同步路径（改写/分类/embedding/rerank）直接读 ThreadLocal；
 * 流式 chat 在 {@code chatStream()} 入口 snapshot，避免 WebClient 线程丢失上下文。
 */
public final class UsageContext {

    private static final ThreadLocal<State> HOLDER = new TransmittableThreadLocal<>();

    private UsageContext() {}

    public static void open(State state) {
        HOLDER.set(state);
    }

    public static void setCallType(String callType) {
        State s = HOLDER.get();
        if (s != null) {
            s.setCallType(callType);
        }
    }

    public static void setConversationId(Long conversationId) {
        State s = HOLDER.get();
        if (s != null) {
            s.setConversationId(conversationId);
        }
    }

    public static void setDocumentId(Long documentId) {
        State s = HOLDER.get();
        if (s != null) {
            s.setDocumentId(documentId);
        }
    }

    public static State snapshot() {
        State s = HOLDER.get();
        return s == null ? State.builder().callType("CHAT").build() : s.copy();
    }

    public static void close() {
        HOLDER.remove();
    }

    @Data
    @Builder
    public static class State {
        private String traceId;
        private String callType;
        private Long userId;
        private String username;
        private String visitorId;
        private String userRole;
        private Long conversationId;
        private Long documentId;

        public State copy() {
            return State.builder()
                    .traceId(traceId)
                    .callType(callType)
                    .userId(userId)
                    .username(username)
                    .visitorId(visitorId)
                    .userRole(userRole)
                    .conversationId(conversationId)
                    .documentId(documentId)
                    .build();
        }
    }
}
