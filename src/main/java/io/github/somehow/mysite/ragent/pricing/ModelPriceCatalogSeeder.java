package io.github.somehow.mysite.ragent.pricing;

import io.github.somehow.mysite.ragent.config.RagentSchemaMigration;
import io.github.somehow.mysite.ragent.dao.entity.LlmModelPriceDO;
import io.github.somehow.mysite.ragent.dao.mapper.LlmModelPriceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 启动时把官方价目增量灌进 t_llm_model_price：按 (provider, model) 判重，
 * 已有任意版本（含管理员修订/停用）的模型跳过，新模型卡随版本发布自动补入存量库。
 * 价格单位：每 1M token；时间均为峰时基准价，谷时由 off_peak_factor 折算。
 * 数据截至 2026-11-17 核实的官网价目，来源见 .trellis/tasks/11-17-ai-cost-accuracy/prd.md。
 */
@Slf4j
@Component
@DependsOn("ragentSchemaMigration")
@RequiredArgsConstructor
public class ModelPriceCatalogSeeder implements ApplicationRunner {

    /** DeepSeek 峰时：UTC 01:00-04:00 / 06:00-10:00，仅周一至周五，排除中国法定节假日。 */
    private static final String DEEPSEEK_PEAK_WINDOWS = """
            [{"start":"01:00","end":"04:00","days":[1,2,3,4,5]},
             {"start":"06:00","end":"10:00","days":[1,2,3,4,5]}]""";

    private static final LocalDateTime SEED_FROM = LocalDateTime.of(2026, 1, 1, 0, 0);

    private final LlmModelPriceMapper mapper;
    private final ModelPriceResolver resolver;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Set<String> existing = new HashSet<>();
            for (LlmModelPriceDO row : mapper.selectList(null)) {
                existing.add(key(row));
            }
            int inserted = 0;
            for (LlmModelPriceDO row : buildSeeds()) {
                // 管理员改过的模型已有版本行则跳过；整模型被删光时按默认价目重建
                if (existing.add(key(row))) {
                    mapper.insert(row);
                    inserted++;
                }
            }
            if (inserted > 0) {
                resolver.evict();
                log.info("Seeded {} new LLM model price cards", inserted);
            }
        } catch (Exception e) {
            log.warn("Price catalog seeding skipped: {}", e.getMessage());
        }
    }

    private static String key(LlmModelPriceDO row) {
        return row.getProvider() + "|" + row.getModel();
    }

    static List<LlmModelPriceDO> buildSeeds() {
        return List.of(
                // ── DeepSeek（CNY/1M；峰时基准价，谷时×0.5）──
                deepseek("deepseek-flash", "DeepSeek V4.1 Flash", "2", "0.04", "8"),
                deepseek("deepseek-v4-flash", "DeepSeek V4 Flash（旧名，按 Flash 计）", "2", "0.04", "8"),
                deepseek("deepseek-v4-flash-vision-exp", "DeepSeek V4 Flash 视觉（旧名）", "2", "0.04", "8"),
                deepseek("deepseek-chat", "DeepSeek Chat（旧名，按 Flash 计）", "2", "0.04", "8"),
                deepseek("deepseek-reasoner", "DeepSeek Reasoner（旧名，按 Flash 计）", "2", "0.04", "8"),
                deepseek("deepseek-v4-pro", "DeepSeek V4 Pro", "9", "0.30", "27"),

                // ── 百炼（CNY/1M，华北2北京价）──
                bailian("text-embedding-v4", "百炼 Embedding v4", "0.5", null, "0"),
                bailian("qwen3-rerank", "百炼 Qwen3 Rerank", "0.5", null, "0"),
                bailian("qwen3.7-text-rerank", "千问 3.7 文本重排", "0.5", null, "0"),
                // qwen3.5-plus 阶梯：≤128K ¥0.8/¥4.8；128-256K ¥2/¥12；256K-1M ¥4/¥24
                // 显式缓存命中按输入 10%（≤128K 档 ¥0.08）
                tiered("qwen3.5-plus", "千问 3.5 Plus", "0.8", "0.08", "4.8",
                        """
                        [{"max_input_tokens":131072,"input_price":0.8,"cache_read_price":0.08,"output_price":4.8},
                         {"max_input_tokens":262144,"input_price":2,"cache_read_price":0.2,"output_price":12},
                         {"max_input_tokens":1048576,"input_price":4,"cache_read_price":0.4,"output_price":24}]"""),
                // qwen3.7-plus：≤256K ¥2/¥8；256K-1M ¥6/¥24；隐式缓存命中 ¥0.4（输入 20%）
                tiered("qwen3.7-plus", "千问 3.7 Plus", "2", "0.4", "8",
                        """
                        [{"max_input_tokens":262144,"input_price":2,"cache_read_price":0.4,"output_price":8},
                         {"max_input_tokens":1048576,"input_price":6,"cache_read_price":1.2,"output_price":24}]"""),
                bailian("qwen3.7-max", "千问 3.7 Max", "12", "1.2", "36"),
                bailian("qwen-max", "千问 Max", "2.4", "0.48", "9.6"),
                // qwen-plus 阶梯（非思考模式）：≤128K ¥0.8/¥2；128-256K ¥2.4/¥20；256K-1M ¥4.8/¥48
                // 隐式缓存命中 = 输入 20%（¥0.16/¥0.48/¥0.96）
                tiered("qwen-plus", "千问 Plus", "0.8", "0.16", "2",
                        """
                        [{"max_input_tokens":131072,"input_price":0.8,"cache_read_price":0.16,"output_price":2},
                         {"max_input_tokens":262144,"input_price":2.4,"cache_read_price":0.48,"output_price":20},
                         {"max_input_tokens":1048576,"input_price":4.8,"cache_read_price":0.96,"output_price":48}]"""),
                // qwen-flash 阶梯：≤128K ¥0.15/¥1.5；128-256K ¥0.6/¥6；256K-1M ¥1.2/¥12
                // 隐式缓存命中 = 输入 20%（¥0.03/¥0.12/¥0.24）
                tiered("qwen-flash", "千问 Flash", "0.15", "0.03", "1.5",
                        """
                        [{"max_input_tokens":131072,"input_price":0.15,"cache_read_price":0.03,"output_price":1.5},
                         {"max_input_tokens":262144,"input_price":0.6,"cache_read_price":0.12,"output_price":6},
                         {"max_input_tokens":1048576,"input_price":1.2,"cache_read_price":0.24,"output_price":12}]"""),
                bailian("qwen-turbo", "千问 Turbo（旧名，待核实）", "0.3", null, "0.6")
        );
    }

    private static LlmModelPriceDO deepseek(String model, String name,
                                            String input, String cacheRead, String output) {
        LlmModelPriceDO d = base("deepseek", model, name, "CNY");
        d.setInputPrice(new BigDecimal(input));
        d.setCacheReadPrice(new BigDecimal(cacheRead));
        d.setOutputPrice(new BigDecimal(output));
        d.setPeakWindows(DEEPSEEK_PEAK_WINDOWS);
        d.setOffPeakFactor(new BigDecimal("0.5"));
        d.setExcludeHolidays(true);
        return d;
    }

    private static LlmModelPriceDO bailian(String model, String name,
                                           String input, String cacheRead, String output) {
        LlmModelPriceDO d = base("bailian", model, name, "CNY");
        d.setInputPrice(new BigDecimal(input));
        if (cacheRead != null) {
            d.setCacheReadPrice(new BigDecimal(cacheRead));
        }
        d.setOutputPrice(new BigDecimal(output));
        return d;
    }

    private static LlmModelPriceDO tiered(String model, String name,
                                          String input, String cacheRead, String output, String tiers) {
        LlmModelPriceDO d = bailian(model, name, input, cacheRead, output);
        d.setTiers(tiers);
        return d;
    }

    private static LlmModelPriceDO base(String provider, String model, String name, String currency) {
        LlmModelPriceDO d = new LlmModelPriceDO();
        d.setProvider(provider);
        d.setModel(model);
        d.setDisplayName(name);
        d.setCurrency(currency);
        d.setUnit("PER_MILLION_TOKENS");
        d.setEffectiveFrom(SEED_FROM);
        d.setSource("SEED");
        d.setCacheReadFactor(BigDecimal.ONE);
        d.setCacheWriteFactor(BigDecimal.ZERO);
        d.setCreateTime(LocalDateTime.now());
        d.setUpdateTime(LocalDateTime.now());
        return d;
    }
}
