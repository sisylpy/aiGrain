# **`SHADOW` 灰度观测与复盘清单**

> **读者**：架构 / SRE / 后端负责人 / 运营值班。  
> **现网**：旁路 Composite 时 SSE / Harness 摘要已输出 **`compositeShadow*`**、**`compositeGate*`**、**`compositePlanner*`** 等字段（见 **[`business-diagnosis-production-composite-execution-design.md`](./business-diagnosis-production-composite-execution-design.md)** §13～§16）。  
> **放量策略**：**[`business-diagnosis-shadow-rollout-plan.md`](./business-diagnosis-shadow-rollout-plan.md)**。  
> **Gate**：**`BusinessDiagnosisCompositeProductionGate`** — 仅结构化 **`allowed` / `reasonCode`**，**禁止**用户原文 **`contains`/regex** 分流。  
> **边界**：**不接 PRIMARY**；**不替换** **`finalAnswerText` / `answerPreview`**。  
> **用途**：**`SHADOW` 批次人工复盘** 固定字段表与日表口径；自动化 dashboard（**C-66**）见路线图 §6。

---

## **1. 每次 `SHADOW` 批次须落库或导出的字段（逐请求）**

以下为 **普通 Run**、`executionMode=SHADOW`、**`productionEnabled=true`** 场景下建议 **按 run / 按事件** 存档的最小集合（与 **composite §13.4 / §15～§16** 一致；日志键名以 **现网 SSE / summarizer** 为准）。**所有放行/跳过原因** 必须以 **结构化** 结果为准，**禁止** 仅以问句文本路由。

| 类别 | 字段 | 用途 |
|------|------|------|
| **租户与 scope** | **`userId`** | 白名单、冷却、问题归属。 |
| | **`distributerId`** | 租户维限流 / 冷却 / 压力对齐。 |
| | **`departmentId`** | 单店维；**GROUP** 会话下可能为 **null** — 以 **Resolver 物化**为准，不可臆断。 |
| | **`scopeType`**（**`STORE` / `GROUP`**） | 与 **`shadow.scopeWhitelist`**、**GROUP/STORE 分时放量** 对齐；复盘 **口径错误**。 |
| **Gate** | **`compositeGateAllowed`** | 若为 **`false`**，本 run **不应**进入 **`ShadowPolicy`** 旁路语义（与设计一致时再核对摘要是否仍带 **`executionMode`**）。 |
| | **`compositeGateReasonCode`** | 结构化拒绝原因（**非**原文匹配）。 |
| **Composite 执行** | **`compositeExecuted`** | 是否真实调用 **`tryExecute`**（与 **`compositeShadowSkipped`** 互斥判读）。 |
| | **`compositeExecutionSuccess`** | 旁路 PlannerExecutor + Readonly Composer **是否整体成功**。 |
| | **`compositeFallbackRequired`** | Composite 侧是否声明需兜底（**不得**反噬 legacy §13.5）。 |
| | **`compositeExecutionErrorCode`** | 顶层错误码（如 **`COMPOSITE_SHADOW_EXCEPTION`**）。 |
| | **`compositeExecutionErrorMessage`**（或 SSE 等价 **`errorMessage` 摘要**） | 截断/message；用于 **Top errorCode** 聚类。 |
| **Planner 健康** | **`compositePlannerOverallStatus`** | Planner 根 **overallStatus** 摘要（成功 / 降级 / 失败）。 |
| | **`compositePlannerDegradedSteps`** | **降级域** 列表（可截断）；用于 **§2 降级统计**。 |
| **Shadow 耗时** | **`compositeShadowLatencyMs`** | **`tryExecute` 墙钟**；批次内算 **均值 / P95**。 |
| **灰度闸** | **`compositeShadowSkipped`** | **`true`** ⇒ **未**调用 **`tryExecute`**。 |
| | **`compositeShadowSkipReason`**（ **`skipReason`**） | **`SHADOW_GRAY_DISABLED`** / **`WHITELIST_NO_MATCH`** / **`SCOPE_NOT_ALLOWED`** / **`THROTTLE_*`** 等。 |
| | **`compositeShadowWhitelistMatched`** | 名单维是否命中（限流 SKIP 时查阅 **composite §16.5** 语义）。 |
| | **`compositeShadowThrottleHit`** | 全域或冷却 **限流** 命中标记。 |
| **正文契约** | **`compositeFinalAnswerText` 是否非空** | 仅 **SSE / 调试**；体积异常可提示 **SSE 膨胀**。 |
| | **legacy：`finalAnswerText` / `answerPreview` / `answer_delta.text`** | **须**与 **未开 SHADOW 同路径**对照：**空白、报错、顺序错乱均为事故线索**。 |

**补强（建议每 run 导出 JSON 或 CSV）**：**`compositeShadowFinalAnswerReplaced`** 须恒为 **`false`**；**`compositeShadowLegacyAnswerPresent`** / **`compositeShadowCompositeAnswerPresent`** — 便于判断两侧是否可同时对比（**composite §15**）。

---

## **2. 每日复盘表（日维度 / 批次维度）**

在 **批次起止时间窗口**（建议 **对齐一次配置变更**：开闸、扩容名单、调高 cap）内汇总；**多日趋势**可与 **infra / DB** 指标 **同坐标**对齐。

| 指标 | 口径 | 判读目的 |
|------|------|-----------|
| **总请求数** | 符合条件：`productionEnabled=true` **且** **`executionMode=SHADOW`** 的 **`POST /api/ai/runs`**（可加 **Gate `ALLOWED*`** 子集便于对比） | 流量规模与配额是否异常。 |
| **Gate allowed 数** | **`compositeGateAllowed=true`** 计数 | Gate 通过率；与 Shadow 放行 **分层**看清 **谁在挡**。 |
| **Shadow 实际执行数** | **`compositeExecuted=true`** **且** **`compositeShadowSkipped=false`**（或等价） | 真实 **`tryExecute`** 次数 ⇒ **×2 读放大**负载。 |
| **Shadow skipped 数** | **`compositeShadowSkipped=true`** | **`SHADOW_GRAY_DISABLED`** vs **`WHITELIST_NO_MATCH`** vs **`THROTTLE_*`** 再分桶。 |
| **成功率** | **`compositeExecutionSuccess=true`** / **Shadow 实际执行数** | Composite **旁路**质量；连日下跌须警惕。 |
| **平均耗时** | **`compositeShadowLatencyMs`** 算术平均（仅限 **`compositeExecuted=true`**) | 与 **§3 放量前基线**（如 ~27s 样例）对比。 |
| **P95 耗时** | 同上，`compositeShadowLatencyMs` | **SSE / `run_finished` 长尾**与用户感知关联。 |
| **降级域统计** | **`compositePlannerDegradedSteps`** 拆解（四域：**revenue / purchase / stock / dish**） | 某一域集中降级 ⇒ **Adapter / Tool / 数据覆盖**定向查。 |
| **Top errorCode** | **`compositeExecutionErrorCode`** + **`compositeShadowSkipReason`** 频数 | 快速分类 **Bug vs 配额 vs 环境**。 |
| **scope 口径错误** | 人工抽检 + 结构化：**GROUP** 会话下 **`visibleStores` / AnswerPlan **`dataCoverage`** / summary** 是否与 **GROUP** 授权一致（**不出现**「集团问句 → 单店口径」）；**STORE** 是否错绑部门 | **诚实性 / 合规**一票否决项（对齐 **composite §13 / group 设计**）。 |
| **是否影响 legacy** | **`compositeShadowFinalAnswerReplaced≠false`** **或** legacy 终稿与 **shadow 开关无关路径**不一致 **或** 错误率/`run_finished` 异常升高 | **任一条**：按 **rollout §5 / 本文 §4** **先关后查**。 |

---

## **3. 扩大灰度的准入条件（建议同时满足再做配置变更工单）**

1. **连续 24～72 小时**（与 **C-64**「每批高密度观测」一致）**无 legacy 不良影响**：**`finalAnswerText` / `answerPreview`** 与用户可感知流式表现 **与关 SHADOW 时一致**；**无**「疑似 Composite 泄露进主终稿」。  
2. **Composite 成功率达标**：**`compositeExecutionSuccess`** 在 **Shadow 实际执行**子集上 **稳定**高于团队约定阈值（例如 **≥95%** 连续两日，**阈值由业务方书面确认**；若样本极小则以 **零 P0** 为先）。  
3. **P95 耗时可接受**：**`compositeShadowLatencyMs` P95** **未**相对 **本批次基线** **持续恶化**（例如 **未**长期 **> ~35–40s** 且无合理解释）；并与 **SSE 尾包**监控交叉验证。  
4. **无 GROUP/STORE 口径错误**：复盘 **§2**「scope 口径错误」 **零确认案例**；抽检覆盖 **新开租户 / 边界 visibleStores**。  
5. **DB / Tool 压力正常**：热点表 **QPS**、连接池、四域 Tool **超时率**相对 **放量前对照窗** **无异常放大**。  

**扩张动作**仍须 **离散批次**：**whitelist**、**cap**、**cooldown** **每次只改一类或小幅改**，禁止 **多维同时放大**。

---

## **4. 暂停灰度 / 立即关闸的触发条件（满足任一条→`shadow.enabled=false` 或收至等价零流量）**

与 **[`business-diagnosis-shadow-rollout-plan.md`](./business-diagnosis-shadow-rollout-plan.md) §5** **一致**，**C-65** 从观测侧 **操作化**如下：

| 触发 | 判据示例 |
|------|-----------|
| **耗时明显升高** | **P95** **`compositeShadowLatencyMs`** **持续**高于基线 **+陡峭上升**；或 **`run_finished` 长尾**告警。 |
| **Tool 错误放大** | 四域 **超时 / 业务错误码** **与 SHADOW 执行数**同步上升（对照 legacy 同源请求）。 |
| **降级率过高** | **`compositePlannerDegradedSteps`** 非空占比 **超出**阈值，或 **`compositePlannerOverallStatus`** **长期 degraded**。 |
| **GROUP 写成单店** | **结构化**复检发现 **GROUP** scope 下层 **可视门店集合**与 **composite 摘要** **不一致**。 |
| **SSE / 前台变慢** | 首字、心跳、 **`run_finished`** **可感知**劣化 **且**与 **旁路放量**时间相关。 |
| **疑似替换 legacy 正文** | **`compositeShadowFinalAnswerReplaced=true`** **或** **`finalAnswerText`** **被 Composite 覆盖** **或** **内容错乱** — **零容忍**，**先关后查**。 |

---

## **5. C-66 以后再考虑（本清单不列为扩灰前置条件）**

以下 **需要工程化 metrics / 跨实例协调 / 产品决策**，**排在 C-65 稳定复盘之后**：

- **日志聚合 metrics**（按租户、path、Tool 分桶 **RED + histogram**）。  
- **Dashboard**（Grafana / 自建： **`compositeShadow*`** + infra）。  
- **跨实例限流**（Redis 等 **统一 cap**，替代进程内 MVP）。  
- **只读复用 legacy `toolResults`**（**须**独立 **ADR**：信任边界、TTL、一致性）。  
- **PRIMARY 预研**（**须在 `SHADOW` 长期无事故与复盘完成后**）。

---

## **参考**

- [`business-diagnosis-shadow-rollout-plan.md`](./business-diagnosis-shadow-rollout-plan.md) — **C-64** 策略总册 **§5 关闸**、**§6 → C-66+**  
- [`business-diagnosis-production-composite-execution-design.md`](./business-diagnosis-production-composite-execution-design.md) — **§13～§17**  
- [`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md) — **§C-65** 索引  

**文档版本**：**`SHADOW` 灰度观测与复盘清单**（字段与 composite execution 设计 §13～§16 对齐）；**C-66** 自动化 dashboard 见 **[`next-business-capability-roadmap.md`](./next-business-capability-roadmap.md)** §6。
