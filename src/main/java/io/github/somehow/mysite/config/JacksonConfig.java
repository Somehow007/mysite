package io.github.somehow.mysite.config;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置。
 *
 * <p>核心修复：前端接收 {@code ArticleSelectRespDTO} 时拿到的是字符串化的 Long（通过
 * {@code @JsonSerialize(using = ToStringSerializer.class)} 避免 64 位精度丢失），
 * 编辑后再发回 PUT 请求时仍然是字符串，但 {@code ArticleUpdateReqDTO} 中
 * {@code categoryId} / {@code tagIds} 等字段声明为 Long。Jackson 2.14 默认不允许
 * String→Number 的强制转换，导致反序列化失败，文章编辑保存报错。</p>
 *
 * <p>这里全局启用 String→Integer 的 TryConvert 策略，使 Jackson 在遇到字符串时
 * 自动尝试转为数值，与前端行为保持一致。</p>
 *
 * @see io.github.somehow.mysite.dto.resp.ArticleSelectRespDTO
 * @see io.github.somehow.mysite.dto.req.article.ArticleUpdateReqDTO
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCoercionCustomizer() {
        return builder -> builder.postConfigurer(objectMapper -> {
            // 允许 Integer / Long / BigInteger 等整数类型从 String 反序列化
            objectMapper.coercionConfigFor(LogicalType.Integer)
                    .setCoercion(CoercionInputShape.String, CoercionAction.TryConvert);

            // 允许集合元素进行同样的强制转换（修复 List<Long> tagIds 反序列化）
            objectMapper.coercionConfigDefaults()
                    .setCoercion(CoercionInputShape.String, CoercionAction.TryConvert);
        });
    }
}
