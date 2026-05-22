# `out/` — 历史运行输出与抓包（非自动现网契约）

本目录存放 **本地 replay、权限 spot gate、回归 bundle** 等 **历史 JSON / 文本输出**。文件用于 diff、联调留档与 Harness 回放对照，**不**自动代表当前生产 Tool 注册表、语义主链或 SSE 契约。

## 使用须知

1. **当前契约**请以以下文档及代码为准（按优先级）：
   - `docs/AI_HARNESS_REPLAY_CASES.md`（含 **Replay 断言契约**）
   - `docs/ai/semantic-allowed-output-contract-design.md`（语义 wire / slots **现网契约**）
   - `docs/ai/semantic-contract-strict-mode-plan.md`
   - `docs/ai/phase2-tool-request-sql-input-plan.md`
   - `docs/API_INTEGRATION.md`
   - `docs/PERMISSION_MODEL.md`
   - `docs/AI_AGENT_DEVELOPMENT_GUIDE.md`
   - `docs/AI_MAINLINE_INDEX.md`
   - `src/main/java/com/nongxinle/ai/tool/business/AiBusinessToolIds.java`
   - 各 Harness case 的 **最新 expected**（非本目录旧 JSON）

2. **Replay 断言优先级（现网）**
   - **主断言**：`effectiveIntentCode`、`effectivePathCode`、`semanticSlots`（含 `structuredIntentDetailWire`）、`structuredIntentDetail`、时间窗、`selectedTools` / `usedTools`、AnswerPlan 探针。
   - **非主断言**：`metric.rankingType` 仅 **debug/compat**；不得仅凭历史 JSON 中的 rankingType 判定 wire 是否正确。
   - **历史 JSON** 中若出现已删除 Tool id（如 `purchase_query`、`stock_query`、`dish_sales_query`、`gross_margin_calculator`、`business_overview_query`），视为 **旧抓包**；说明见 `docs/AI_MAINLINE_INDEX.md`。**禁止**据此恢复 Tool、Composer raw fallback 或 classic business overview 主链。

3. **不要**批量修改本目录下 JSON 以「对齐现网」；契约变更应改代码、Harness expected 与 `docs/`，而非改历史抓包。

## 子目录（示例）

| 目录 | 用途 |
|------|------|
| `replay-regression-bundle/` | 回归 bundle 单次运行输出 |
| `replay-purchase-followup-core/` | 采购追问 core replay |
| `replay-single-case/` | 单 case 调试输出 |
| `permission-spot-gate-*/` | 权限 spot 检查快照 |

生成新输出时，建议在 commit message 或 PR 中注明 **生成时间与对应用例 id**，避免与现网文档混读。
