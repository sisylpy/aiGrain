# Project handoff：**C 阶段收口 → D-1 业务能力**

> **用途**：关 Cursor 窗口、新开会话时的 **单行入口**。不重复编年史 — 细节见 **`planner-executor-v1-design.md` §27**、**`business-diagnosis-production-composite-execution-design.md`**。

---

## A. 当前已完成（C 线 — Composite 与安全框架）

| 领域 | 说明 |
|------|------|
| **四域 Adapter** | Revenue / Purchase / StockReduce / DishProfit — Hydrated → 真实 Tool |
| **Composite AnswerPlan** | **`BusinessDiagnosisCompositeAnswerPlan`** + Builder |
| **Readonly Composer** | **C-50 / C-51** — 只读 AnswerPlan，不重读 **toolResults** |
| **Gate** | **`BusinessDiagnosisCompositeProductionGate`** — 结构化 **intent/path/ref/scope**，禁用户原文 **contains/regex** |
| **`HARNESS_ONLY`** | **C-58** — Harness **`GRAPH_RUN`** 执行 Composite · **STORE / GROUP Harness** 已验证 |
| **`SHADOW`** | **C-60 / C-61** — 普通 Run 旁路，**`compositeShadow*`** 可观测 · **不换终稿** |
| **`ShadowPolicy`** | **C-63** — 白名单、`scopeWhitelist`、分频限流、cooldown；默认 **`shadow.enabled=false`** |
| **Rollout / 观测** | **`business-diagnosis-shadow-rollout-plan.md`（C-64）**、**`business-diagnosis-shadow-observation-checklist.md`（C-65）** |

---

## B. 当前明确不做

- **C-66**：集中 **metrics**、**dashboard**、**Redis 跨实例限流** — **本阶段不继续做**
- **`PRIMARY`**；**不替换** **`finalAnswerText` / `answerPreview`**
- **不接前台**本轮工程（除非 D-1 另开任务明示）
- **不改** SQL / 四域 Tool / Resolver / Master / Composer **主逻辑**（除非独立任务与白名单改动）
- **禁止**用户原文 **contains/regex** 做 **Gate / Composite 路由**

---

## C. 下一阶段方向（**D-1**）

回到 **餐饮经营分析主业务能力**，而非继续铺 Shadow 工程化：

- 经营诊断 **真实业务问法** 覆盖与话术
- **单域深挖**（营收 / 采购 / 库存 / 菜品毛利）问答能力
- **多轮上下文**（随访、实体、时间范围继承）
- **前台展示**与 **调试体验**
- **老板常问问题** 结构化清单 → 对齐 intent / path

**路线图**（D-1 v1，2026-05-14 定稿）：**[`next-business-capability-roadmap.md`](./next-business-capability-roadmap.md)** — 内含 **§1～§8**（框架完成度、够用即止边界、候选人能力、**P0～P3**、**推荐主链路**、**C-66** 暂缓、**D-2** 建议任务）。

---

## D. **D-2** 建议首任务（承接 **D-1**）

在进入 Java 窗口前优先完成路线图 **§7**：

1. **老板 TOP 问法 → 结构化 intent/path 映射表**（**禁止**用户原文正则路由）
2. **「经营简报 / 对标」（P0-A+B+E）**与 **AnswerPlan / Readonly Composer** 话术、**dataCoverage** 与降级 copy **对齐清单**
3. **Harness / replay**：补 **期望字段** **caseId** 或 JSON 快照（与用户约定维护位置，常为 **`AI_HARNESS_REPLAY_CASES.md`**）

**编码窗**须在 **单列任务**中明确：**不动 SQL / Tool / Resolver / Master 主逻辑**（除非白名单增补已评审）。

---

## E. 协作约定

| 约定 | 内容 |
|------|------|
| **测试** | 非必要 **不改** **`src/test/**`** |
| **验证** | 用户本地 **curl / replay**；JSON 可复制给 ChatGPT 分析 |
| **Agent** | 仅按 **明确任务**改代码或 Markdown；改完汇报 **文件列表 / 是否动 Java·test / 是否触碰禁止项** |
| **范围** | 每轮改动 **收窄**；大图变更拆 PR |

---

## 硬边界（再强调）

- **不把** Harness **GraphCase** 当 **`/api/ai/runs`** 唯一生产入口
- **不写** Shadow **dashboard/metrics** 细化实施案，除非单独立项 **C-66**
