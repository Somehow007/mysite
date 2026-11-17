# AI 消费看板精度改造 — 调研与设计方案

> 2026-11-17 调研+设计稿，未动代码。目标：让费用估算贴近真实账单，价格可编辑可更新，尽量接官方账单数据。

## 1. 背景与目标

用户诉求：不同厂商计价规则差异大（按量/积分/额度、峰谷价、缓存价、阶梯价），当前看板估算不准；希望
1. 调研 DeepSeek 等厂商当前定价规则；
2. 价格可编辑、可更新（后台维护，不改 yaml 重启）；
3. 能对接官方消费/账单数据更好（至少余额级，最好用量级）。

## 2. 现状盘点（代码证据）

- 记录：`t_llm_usage`（PG，`ragent-schema.sql:164`）逐调用落 `prompt/completion/total_tokens`、`cost`（写入时按价算死）、`currency`、`token_source`(API/ESTIMATED)。
- 计价：`RagProperties.PricingProperties`（`application.yaml:190-221`）静态 `models.<model>.{input,output}` 每百万 token 单价，**全局单一币种 CNY**，无缓存/峰谷/阶梯维度。
- 计算：`LlmUsageRecorder.calculateCost`（`usage/LlmUsageRecorder.java:99`）只按 input/output 两维算。
- Token 解析：`AbstractOpenAiProvider.extractUsage`（`llm/provider/AbstractOpenAiProvider.java:199`）只读 `prompt_tokens/completion_tokens/total_tokens`，**丢弃缓存字段**；embedding/rerank 各自解析同样不含缓存。
- 管理端：`/v1/admin/ai/{logs,summary,providers}`（`AdminAiController`），前端 `AiManageView.vue` 三页签（调用记录/消费看板/模型与 API）。
- 先例：`t_llm_provider_setting` 已是「yaml+env 种子 → PG 运行时覆盖」模式；`t_site_config` 是单行可编辑配置先例。

## 3. 厂商调研（2026-11-17 核实）

### 3.1 DeepSeek（api-docs.deepseek.com/quick_start/pricing）

- 现役模型：`deepseek-flash`（V4.1-Flash-1017，含视觉）、`deepseek-v4-pro`（V4-Pro-0813）。旧名 `deepseek-chat`/`deepseek-reasoner`/`deepseek-v4-flash` 已退役，请求仍受理但**按 Flash 价计费**。
- 计价维度 = **输入缓存命中 / 输入缓存未命中 / 输出**，每 1M token，美元页与人民币页并存。
- **峰谷**：峰时 = UTC 01:00–04:00 与 06:00–10:00，仅周一至周五且**排除中国法定节假日**；其余全部谷时，谷价 = 峰价 × 0.5（含周末/节假日全天）。价格按**请求开始时刻**判定。北京时段 = 09:00–12:00、14:00–18:00 工作日。
- USD 价（每 1M）：flash 峰 $0.006命中/$0.30未命中/$1.20输出；v4-pro 峰 $0.044/$1.32/$3.96。CNY 价（每 1M）：flash 峰 ¥0.04/¥2/¥8，谷 ¥0.02/¥1/¥4；v4-pro 峰 ¥0.30/¥9/¥27，谷 ¥0.15/¥4.5/¥13.5。
- **缓存字段**：响应 `usage` 带 `prompt_cache_hit_tokens` / `prompt_cache_miss_tokens`，`prompt_tokens = hit + miss`；命中价约为未命中的 1/30~1/50，无单独写缓存费（自动缓存）。
- **官方 API**：`GET https://api.deepseek.com/user/balance`（API Key 直连可用）返回 `is_available` + `balance_infos[]{currency,total_balance,granted_balance,topped_up_balance}`——**只有余额，无逐调用账单 API**。控制台详细用量走 platform.deepseek.com 私有 session 接口（非官方、易碎，不建议接）。

### 3.2 阿里百炼 DashScope（help.aliyun.com 模型价格页）

- `qwen3-max`（北京）：**按单次请求输入长度阶梯**——≤32K: ¥2.5/¥10；32–128K: ¥4/¥16；128–256K: ¥7/¥28（每 1M 输入/输出）。上下文缓存：隐式命中输入 ×0.2、显式读 ×0.1、显式写 ×1.25；Batch 半价（本系统不用）。**注意：qwen3-max 已于 2026-10-10 下线**，现役为 qwen3.7/3.8/4-max 系列（¥10–12/¥30–36 档），当前 yaml 的 `BAILIAN_CHAT_MODEL:qwen3-max` 大概率已失效或被别名接管，需另行确认。
- `text-embedding-v4`：¥0.5/1M 输入（北京），新用户 90 天 100 万 token 免费额度。
- `qwen3-rerank`：¥0.5/1M 输入（北京），无输出计费。
- **官方 API**：`GET https://dashscope.aliyuncs.com/api/v1/quotas`（DashScope Key 认证）返回账户级 `daily_spend`/`monthly_spend`/`credits`/`available`/`spend_limit` + 分模型用量额度——**官方用量级数据可接**。另有新 `modelstudio` 账单 API（`GetBillingOverview` 等，2026-02-10 版，按月/维度聚合，认证方式待核实，可能要阿里云 AccessKey 签名而非 DashScope Key）。

### 3.3 其他厂商（当前均 disabled，设计需兼容）

- SiliconFlow：按量 CNY，无峰谷；`/v1/user/info` 可查余额（待核实）。
- AIHubMix：美元计价代理，按 token；账单接口待核实。
- Ollama：本地免费，无成本。
- 「积分/额度」类：DeepSeek `granted_balance`（赠金）与百炼免费额度本质是**余额组成**而非计价单位，设计上不需要专门「积分」维度，余额对账即可覆盖。

### 3.4 存量价格表问题（顺手发现）

| yaml 条目 | 现值（标 CNY） | 实际 | 结论 |
|---|---|---|---|
| `deepseek-v4-flash` in/out | 0.2 / 0.8 | Flash 峰 ¥2/¥8、谷 ¥1/¥4（未命中输入） | **低估 2.5–10 倍** |
| `deepseek-chat` in/out | 0.28 / 0.42 | 这是 **USD 价**误标 CNY；且模型已退役 | 错误数据 |
| `qwen3-max` in/out | 2.5 / 10 | ≤32K 档正确，但模型已下线 | 档位不全+模型失效 |
| `text-embedding-v4` / `qwen3-rerank` | 0.5 输入 | ¥0.5 正确 | OK |
| `qwen-turbo` | 0.3 / 0.6 | ¥0.3/¥0.6 基本正确（注意是否仍是现役名） | 待核实 |

## 4. 设计方案

### 4.1 总体思路

```
厂商响应 usage ──解析──▶ TokenUsage{prompt,completion,total,cacheRead,cacheWrite}
                              │
模型价卡 t_llm_model_price ──▶ ModelPriceResolver（内存缓存+版本号）
   (provider+model+生效期 → 币种/阶梯/峰谷窗口/缓存价)
                              │
                              ▼
                    t_llm_usage.cost（写入时算死，含 price_id 溯源）
                              │
        ┌─────────────────────┼──────────────────────┐
        ▼                     ▼                      ▼
  消费看板(内部估算)   官方账单探针(余额/日耗)    计价管理UI(可编辑)
```

核心决策：
- **成本仍在写入时算死**（保持现状），但算价来源从 yaml 静态表换成 DB 价卡 + 生效期版本化。改价只影响新记录；历史重算做成可选后续项。
- **币种跟价卡走**，`t_llm_usage.currency` 已有列；summary 所有聚合**必须 GROUP BY currency**（现为隐式单币种，混币后会错加——必须改）。
- **官方数据定位为「对账参照」而非替代**：内部日志管逐调用明细，官方 API 管账户余额/周期花费，两者并排展示+差值。

### 4.2 TokenUsage 与 usage 解析扩展

- `TokenUsage` record 增加 `cacheReadTokens`、`cacheWriteTokens`（静态工厂保持旧签名兼容，新增重载）。
- `AbstractOpenAiProvider.extractUsage` 兼容三种形态：
  - OpenAI 系 `usage.prompt_tokens_details.cached_tokens`（AIHubMix/OpenAI 系/部分兼容网关）；
  - DeepSeek `usage.prompt_cache_hit_tokens`（`prompt_tokens` 含 hit+miss，cacheRead=hit，miss=prompt−hit 推导）；
  - 防御性兼容 `cache_read_input_tokens`/`cache_creation_input_tokens`（Anthropic 风格，未来接入用）。
- `BaiLianEmbeddingService`、`BaiLianRerankProvider` 的 extractUsage 同步补 `cached_tokens` 解析（百炼兼容模式若返回即用）。
- `t_llm_usage` 加列：`cache_read_tokens INT DEFAULT 0`、`cache_write_tokens INT DEFAULT 0`、`price_id BIGINT`（命中哪张价卡，审计/重算用）。历史行默认 0，不重算。
- reasoning_tokens（思考链）计入 output 计费即可，不单独立列（P5 可选）。

### 4.3 价卡模型 `t_llm_model_price`（PG）

```sql
id BIGINT PK,
provider VARCHAR(32) NOT NULL,          -- deepseek / bailian / ...
model VARCHAR(128) NOT NULL,            -- 请求里的模型名（兼容旧名也可建卡）
display_name VARCHAR(128),
currency VARCHAR(8) NOT NULL,           -- CNY / USD，跟账户实际结算币种
unit VARCHAR(24) NOT NULL DEFAULT 'PER_MILLION_TOKENS',
input_price NUMERIC(14,6) NOT NULL,     -- 未命中输入（每 1M）
cache_read_price NUMERIC(14,6),         -- 缓存命中输入；NULL→=input_price
cache_write_price NUMERIC(14,6),        -- 缓存写入；NULL→=0
output_price NUMERIC(14,6) NOT NULL,
peak_windows JSONB,                     -- [{"start":"01:00","end":"04:00","days":[1,2,3,4,5]},...UTC]
off_peak_factor NUMERIC(6,4),           -- 谷时系数（DeepSeek=0.5）；空/1=无折扣
exclude_holidays BOOLEAN DEFAULT false, -- 节假日视为谷时（中国法定节假日）
tiers JSONB,                            -- [{max_input_tokens,input_price,cache_read_price?,output_price,...}]
effective_from TIMESTAMP NOT NULL,      -- 版本生效起点
effective_to TIMESTAMP,                 -- NULL=当前有效
source VARCHAR(16) DEFAULT 'MANUAL',    -- SEED/MANUAL/OFFICIAL_SYNC
note VARCHAR(500),
create_time TIMESTAMP, update_time TIMESTAMP,
UNIQUE(provider, model, effective_from)
```

- `t_llm_holiday`（date PK, name）：中国法定节假日清单，种子灌 2026，后台可维护（DeepSeek 峰谷排除依赖）。
- 解析顺序：provider+model → effective_from≤t<effective_to 取最新 → prompt_tokens 选 tier → 判定峰/谷（UTC 时间窗+周几+节假日）→ cacheRead/cacheWrite 拆分计价。无卡 → cost=0 + `price_id=NULL`（看板继续显示「未标价」语义）。
- YAML `rag.llm.pricing` 降级为**首次启动种子**（表空时导入），之后以 DB 为准；yaml 块最终移除。种子数据按 §3.1/§3.2 填 DeepSeek flash/v4-pro 双币种卡、百炼 embedding/rerank/qwen 系列阶梯卡。

### 4.4 后端 API 与前端

后端（均 `/v1/admin/ai`，ADMIN，沿用 `WebSecurityConfig` 既有 `/v1/admin/ai/**` 鉴权）：

- `GET /v1/admin/ai/prices` — 价卡列表（含历史版本）
- `POST /v1/admin/ai/prices` — 新建（effective_from=now 或指定）
- `PUT /v1/admin/ai/prices/{id}` — 改当前版=自动闭合旧版 effective_to + 插新版（版本化，不覆盖历史）
- `GET/PUT /v1/admin/ai/holidays` — 节假日清单
- `GET /v1/admin/ai/billing` — 各 provider 官方余额/周期花费（探针聚合）
- `GET /v1/admin/ai/logs`、`/summary` — 扩展返回 cache_read_tokens、按币种分组的 totals/series

前端 `AiManageView.vue`：
- 新页签「计价」或并入「模型与 API」：价卡表格 + 编辑抽屉（基础价/缓存价/峰谷窗口/阶梯/生效期）。
- 「消费看板」：币种分组展示；官方账单卡（余额、月耗）与内部估算并排 + 差值提示。
- 「调用记录」：加缓存 token 列、峰/谷徽标。

### 4.5 官方账单探针（P4）

`BillingProbe` 接口 + provider 实现，`GET /v1/admin/ai/billing` 聚合：

| Provider | 接口 | 能拿到 | 认证 |
|---|---|---|---|
| deepseek | `GET {baseUrl}/user/balance` | 余额（total/granted/topped_up，按币种） | API Key ✓ |
| bailian | `GET dashscope.aliyuncs.com/api/v1/quotas` | daily/monthly spend、credits、分模型用量 | DashScope Key ✓ |
| siliconflow | `GET /v1/user/info`（待核实） | 余额 | API Key |
| aihubmix | 待调研 | ? | ? |
| ollama | — | 免费 | — |

Model Studio `GetBillingOverview` 账单 API 列为后续调研项（认证可能需阿里云 AccessKey 签名，复杂度高）。

### 4.6 分期计划

- **P1 数据基建**：TokenUsage 扩展 + 三处 usage 解析补缓存字段 + t_llm_usage 加列 + migration + DTO/mapper 同步。验收：DeepSeek 调用日志出现 cache_read_tokens>0。
- **P2 计价引擎**：t_llm_model_price + holiday 表 + migration 种子 + ModelPriceResolver + LlmUsageRecorder 改造 + yaml 种子导入 + summary GROUP BY currency。验收：单测覆盖峰谷/阶梯/缓存命中三场景；新调用 cost 与官方价目手算一致。
- **P3 管理端**：价卡 CRUD API + 前端计价页 + 日志缓存列 + 看板币种分组。验收：后台改价即时影响新记录，历史不变。
- **P4 官方对账**：BillingProbe（deepseek balance + bailian quotas）+ 看板官方/估算对照卡。验收：余额与控制台一致。
- **P5 可选**：历史重算（reprice）、汇率折算统一视图、Model Studio 账单明细、节假日自动同步、reasoning_tokens 列。

## 5. 开放问题（需拍板）

1. **币种展示**：混币后看板按币种分组展示（推荐，零汇率依赖）还是要配汇率折算成单一货币？
2. **DeepSeek 账户币种**：你的 DeepSeek 余额是 CNY 还是 USD 结算？（决定种子价卡币种；两套价我都已拿到）
3. **价卡版本化**：改价=闭合旧版+开新版（推荐，成本可审计）还是直接覆盖当前价？
4. **qwen3-max 已下线 / deepseek-v4-flash 旧名**：本任务顺手修配置（换现役模型名+重建价卡），还是另开任务？注意与 11-17-prod-embedding-400 同类的「模型名失效」风险。
5. **对账深度**：P4 先做余额+百炼日/月耗够不够？DeepSeek 无官方逐调用账单，只能「期初余额−期末余额+充值」反推，误差来自赠金消耗顺序。
6. **历史数据**：旧行 cache=0、cost 不动；是否需要 P5 重算工具？（建议先不做，量小）

## 6. 验证方式（实施时）

- `./scripts/verify.sh`（quick）+ 涉及 PG schema 时 `full` 或本地 docker PG 手工验证 migration 幂等。
- 计价引擎单测：构造 (provider,model,time,promptTokens,cacheRead) 矩阵断言 cost。
- 端到端：本地 docker 起 PG/MySQL，发一条真实 DeepSeek 流式请求，查 t_llm_usage 确认 cache_read_tokens 与 cost。
- 前端：`npm run type-check && npm run lint && npm run build`；看板截图沿用 `scripts/dash-shots.mjs`。

## 7. 消费点清单（改动前按 AGENTS.md §2 先列）

- `TokenUsage`：`AbstractOpenAiProvider`、`BaiLianEmbeddingService`、`BaiLianRerankProvider`、`LlmUsageRecorder`、测试 `AbstractOpenAiProviderTest`。
- `t_llm_usage`：`LlmUsageDO`、`LlmUsageMapper`（5 条聚合 SQL 需加 currency 分组）、`AdminAiService`、`LlmUsageLogDTO`/`LlmUsageSummaryDTO`、`AiManageView.vue`+`aiAdmin.ts`。
- `rag.llm.pricing`：`RagProperties`、`LlmUsageRecorder`、`AdminAiService.summary`（currency 兜底）、`application.yaml`、可能的测试。
- 新表：`RagentSchemaMigration` + `docker/init/ragent-schema.sql` 双写（新卷走 init，旧卷走 migration）。
- 新端点：`AdminAiController`（`/v1/admin/ai/**` 已在 security 白名单内，无需改 `WebSecurityConfig`）。
- `BillingProbe` 需读 provider apiKey：复用 `LlmRuntimeConfigService` 的解密链路（api_key 密文存 `t_llm_provider_setting`），注意密钥不出服务端、不进日志。

## 5.1 用户决策（2026-11-17 已拍板）

1. **币种**：DeepSeek 账户 CNY 结算；看板**按币种分组展示**（不做汇率折算）。
2. **价卡版本化**：改价=闭合旧版 effective_to + 插新版，不覆盖历史。
3. **顺手修配置**：DeepSeek chat-model → `deepseek-flash`（旧名仍受理但按 Flash 价计，新名更直白）；百炼 chat-model `qwen3-max` 已下线需换现役模型（实施时核实可用名）。
4. **对账范围（工程判断）**：DeepSeek 无官方逐调用账单 API → 采用**余额快照表 + 定时/按需轮询**，周期实花 = 快照差值（赠金先扣规则下 Δtotal 即真实消耗，充值事件稀有需人工注意）；百炼 quotas 直接给日/月耗。定位=「官方口径 vs 内部估算」差值对账，个人博客规模足够，不追逐逐调用级官方明细。

## Resolution

### 实施记录（2026-11-17）

**P1 数据基建 ✅**
- `TokenUsage` + `UsageJson` 解析 `prompt_cache_hit_tokens`/`prompt_cache_miss_tokens`（DeepSeek）与百炼 `cached_tokens` 变体；`t_llm_usage` 加 `cache_read_tokens`/`cache_write_tokens`/`price_id`/`currency` 列（init SQL + `RagentSchemaMigration` 幂等 DDL）。

**P2 计价引擎 ✅**
- 新表 `t_llm_model_price`（币种/缓存读写价或倍率/峰谷窗 JSON/输入长度阶梯 JSON/生效期版本化）+ `t_llm_holiday`；`ModelPriceResolver`（内存缓存，版本选择 `effective_from <= t < COALESCE(effective_to)` 最新者胜，峰谷=UTC 时段+周几+节假日排除）+ `ModelPriceCatalogSeeder`（yaml 种子仅首次启动写入，2026 法定节假日 7 段 33 天已按国办发明电〔2025〕7号录入）。
- `LlmUsageRecorder` 改为经 resolver 计价，写 cache 列 + `price_id` + 每模型币种；成本仍写入时算死。
- `LlmUsageMapper` 5 条聚合全部 `GROUP BY currency`；`LlmUsageSummaryDTO` 重构 `byCurrency` 分组；`LlmUsageLogDTO` 加 `priceId`。
- `RagProperties` 删 `pricing` 死配置；yaml 删 pricing 块，chat 模型改 `deepseek-flash` / `qwen3.5-plus`。
- 验证：`ModelPriceResolverTest` 10 用例全绿（峰谷/节假日/缓存命中/阶梯/版本链）。

**P3 管理端 ✅**
- 后端：`AdminPricingService` + `AdminAiController` 价卡 CRUD（POST 新建 / PUT 修订=版本化 / DELETE 停用）与节假日全量替换。
- 前端：新 `AiPricingPanel.vue`（价卡表+编辑弹窗+节假日编辑器）；`AiManageView` 加「计价」页签、日志缓存读/写列、看板按币种切换（`dash` computed 过滤各序列）。

**P4 官方对账 ✅**
- `t_llm_balance_snapshot` 快照表；`BillingProbe` 接口 + `DeepSeekBillingProbe`（`GET {baseUrl 去 /v1}/user/balance`）+ `BailianBillingProbe`（`GET dashscope.aliyuncs.com/api/v1/quotas`，Bearer Key，`code=="Success"` 校验，未识别字段截断存 raw 便于口径排查）；`BillingHttpSupport` 共用 java.net.http 客户端（10s 超时，错误体不落日志）。
- `AdminBillingService.query()`：遍历 enabled provider，有探针的才拉；用 `LlmRuntimeConfigService.requireProvider` 取运行时生效配置（后台覆盖+解密 apiKey）；每次结果落快照；balance 类按币种算与上一份成功快照的 total 差值（充值致余额上升时计 0）。
- `GET /v1/admin/ai/billing` → `LlmBillingDTO{probedAt, providers[{provider,ok,latencyMs,message,data,delta}]}`。
- 前端：`AiBillingCard.vue`（挂在消费看板底部，手动刷新+挂载即查一次）——余额类显示 total/充值/赠金+区间实花；配额类显示日/月花费+token/请求量。
- 验证：`./scripts/verify.sh` quick 全绿（hermetic 测试 + vue-tsc）；eslint/oxlint 改动文件 0 问题。

**遗留/注意**
- DeepSeek 余额差值依赖快照频率：只在点「刷新」时产生，区间实花=两次点击间隔；充值会让差值失真（已按币种计 0 兜底，人工注意）。
- 百炼 quotas 接口按 OpenUsage 文档实现（`data.daily_spend` 等字段），若阿里侧口径变化，raw 已截断存快照可排查。
- 工作区存在无关在途改动（Article* prod-embedding-400），`ArticleServiceImpl` 预存语法错误已顺手修复。

### 走查修复（2026-11-17，P3/P4 复查后）

走查发现并修复以下问题（均已编译/vue-tsc 验证）：

1. **编辑弹窗必败**：`openEdit` 预填旧版本 `effectiveFrom`，后端要求严格晚于旧版 → 原样提交必 400。改为空（=now）。
2. **effectiveFrom 时区错位 8h**：前端 `toISOString()` 发 UTC，后端 `LocalDateTime` 剥 `Z` 留 UTC 墙钟，与服务器本地墙钟语义差 8h。统一为 naive 本地墙钟：前端 `datetime-local` 值直接发 `yyyy-MM-ddTHH:mm` 不转 UTC，删除 `toLocalInput`；`LlmModelPriceUpsertRequest.effectiveFrom` 注释写明语义。
3. **死版本行**：`revisePrice` 拒 `newFrom ≥ chainTail`（否则插 `from>to` 空窗）；`retirePrice` 对未来生效版本闭合为空窗口（`effective_to=effective_from`）而非 `now`；`createPrice` 加 `assertNoDuplicateVersion`+`assertNoOverlappingVersion`（同模型仍有生效版本时报错引导走「修订」，排除 `from<to` 死行）；`replaceHolidays` 加日期去重，避免唯一键 500。
4. **遗留到 P5**（需先讨论）：`t_llm_usage` 加 `peak` 列+日志峰/谷徽标（PRD §4.4）；对账卡补「官方实花 vs 内部估算」对照差值。

### 走查修复第二轮（2026-11-17，P1/P2 复查后，用户批准「有问题就修」）

1. **价卡覆盖缺口**：本地实际生效模型 `qwen-max`/`qwen3.7-text-rerank` 无卡会静默 cost=0。seeder 补 `bailian("qwen-max","千问 Max","2.4","0.48","9.6")`（北京价）与 `qwen3.7-text-rerank`（¥0.5/M 输入）；`.env.example` 默认值同步（`BAILIAN_CHAT_MODEL=qwen3.5-plus`、`BAILIAN_RERANK_MODEL=qwen3.7-text-rerank`、`DEEPSEEK_CHAT_MODEL=deepseek-flash`）。
2. **seeder 改增量 upsert**：原「表空才灌」导致存量库永远补不进新卡。改为按 `(provider,model)` 判重——已有任意版本（含管理员修订/软删 `effective_to`）的模型跳过，新模型卡随版本发布自动补入；整模型删光会按默认价目重建（已注释说明）。
3. **peak 列+徽标落地**（原 P5 遗留项提前做）：`t_llm_usage.peak` 三态（卡无峰谷配置落 NULL，前端不显示）；`ResolvedPrice` 加 `peakAware`；DO/DTO/Recorder 全链路；日志「费用」列加 峰/谷 徽标（峰=warning、谷=success）+ hover 提示「峰时价/谷时价」；前端 `LlmUsageLog.peak?: boolean | null`。
4. **cachePrice 回退顺序修正**：原实现把 tier/卡级显式价与 factor 分别合并导致**卡级显式价压过 tier factor**（与注释约定矛盾）；重写为——命中阶梯档：tier 显式价 > tier factor×档输入 > 卡级 factor×档输入 > 卡级显式价（首档扁平价，套高阶梯档会系统性低估，故排最后）；未命中阶梯：卡级显式价 > 卡级 factor×输入。
5. **畸形 JSON 丢整条 usage**：`buildRow` 里 `resolve` 单独 try/catch（`resolvePrice` 包装），tiers/peak_windows 字段类型非法时降级 cost=0 占位记录，不再丢整行。
6. **节假日种子复活**：`t_llm_seed_marker` 标记表 + 数据修改 CTE 单语句原子「插标记→仅新标记时灌种子」；管理员删过日期后重启不再复活（schema.sql 同步建表）。
7. **计价时点偏差**：峰谷判定用 `now - latencyMs` 近似请求开始时刻（provider 侧 t0 在 POST 前、latency 覆盖整条流），长流跨峰谷边界按请求侧口径判定；`create_time` 仍记落库时刻。
8. **陈旧引用清理**：`ClassifierLLMConfig.resolveCheapModel` → deepseek-flash/qwen-flash；`QueryRewriter` 不再硬编码 `deepseek-chat`（ChatRequest 不带 model，由 cheap bean 的 snap.model 决定）；`AiProviderCard` 模型建议更新；`BaiLianIntegrationTest` → qwen3.5-plus 并**删除硬编码泄露的 API key**（改走 `BAILIAN_API_KEY` env + assumeTrue）。

验证：`./mvnw compile` 绿；`./mvnw test -DexcludedGroups=needs-infra` 356 例 0 失败（ModelPriceResolverTest 10/10）；前端 `vue-tsc`+eslint/oxlint 改动文件 0 问题。

---

## 附：调研原始出处

- DeepSeek 定价：<https://api-docs.deepseek.com/quick_start/pricing/>（2026-10/11 多方核对一致）
- DeepSeek 缓存字段：<https://api-docs.deepseek.com/guides/kv_cache.md>
- DeepSeek 余额 API：`GET https://api.deepseek.com/user/balance`（apidog 镜像 + CodexBar/openburn 文档佐证）
- 百炼价格：<https://help.aliyun.com/zh/model-studio/model-pricing>；qwen3-rerank：<https://help.aliyun.com/zh/model-studio/qwen3-rerank>
- 百炼账单：<https://docs.modelstudio.console.alibabacloud.com/zh/model-studio/bill-query-and-cost-management>、`GetBillingOverview` API 文档
- DashScope quotas：OpenUsage 文档 <https://openusage.sh/docs/providers/alibaba-cloud/>
- qwen3-max 阶梯/生命周期：llmrates.cn、model-atlas.zhangxinhao.net
- usage 字段对照：NVIDIA AIPerf vendor-usage-field-reference、aispendops DeepSeek docs
