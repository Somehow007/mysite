# 11-17 生产 AI 问答报错：embedding 模型名写错

## 现象

线上 RAG 问答（`/v1/rag/chat/stream`）报错。排查时发现两件事：

1. 当时我探测到全站 API 502 —— 实为 11:27 SEO 部署重启窗口（~1min），属正常部署中断，非故障。
2. 真正的 AI 报错：application.log 11:06 `BaiLianEmbeddingService` 报
   `400 Bad Request from POST .../compatible-mode/v1/embeddings`，`model=qwen3.7-text-embedding`。

## 根因

PG `t_llm_provider_setting`（bailian 行）+ `/opt/mysite/.env` 的 `BAILIAN_EMBEDDING_MODEL`
被写成了 `qwen3.7-text-embedding` —— 该模型名在 DashScope 不存在（实测 400）。
行 `update_time=2026-09-17 09:34`，应是后台「更新 LLM 供应商」保存时按
`qwen3.7-text-rerank`（该 rerank 名实测 200 合法）对称类推填入的错误名。
运行时在重启后才从 PG overlay 生效，故 9/17 保存后一段时间仍正常，
10/14 文章入库开始 FAILED，11/17 问答检索路径 400。

## 处置

- `UPDATE t_llm_provider_setting SET embedding_model='text-embedding-v4' WHERE name='bailian'`
- `/opt/mysite/.env`：`BAILIAN_EMBEDDING_MODEL=text-embedding-v4`（sed 同步）
- `start.sh restart`（overlay 仅启动时应用）。日志确认
  `BaiLianEmbeddingService runtime config: model=text-embedding-v4`，外网 API 200。

**刻意不走后台 update 接口**：`invalidateEmbeddingsIfNeeded` 见模型变更会
`deleteAll()` 清空旧向量；旧向量本就是 v4 语义空间，直接改库保留有效数据。

## 残留

- 文档 `2110282970944704513`《2. 引入AgentScope…》status=FAILED（10/14 起），
  需在后台重新处理：`POST /v1/rag/kbs/2080300168421761025/docs/2110282970944704513/reprocess`
- 建议：管理后台保存供应商时调 ping/embedding 冒烟验证模型名，错误名当场拦下。

## 验证

- `curl .../embeddings`：`qwen3.7-text-embedding`→400、`text-embedding-v4`→200
- `curl .../rerank`：`qwen3.7-text-rerank`→200、`qwen3-rerank`→200、`gte-rerank-v2`→200、`gte-rerank`→403
- 重启后外部 `/v1/categories`、`/v1/articles/page` 均 200；无新增 embedding 失败日志

## Resolution

2026-11-17 已修复并重启生效：PG + .env 模型名改回 `text-embedding-v4`，
embedding/rerank 外网实测全通，问答链路恢复。任务归档。
