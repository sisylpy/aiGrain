# **`SHADOW` Composite 灰度上线策略**

> **读者**：架构 / SRE / 后端负责人。  
> **现网**：**`ShadowPolicy`** / **`ShadowDecision`**、**`BusinessDiagnosisCompositeProductionGate`**、**`maybeExecuteShadowCompositePlanner`** 已落地；默认 **`ai.composite.businessDiagnosis.shadow.enabled=false`**（不旁路 Composite）。  
> **权威依赖**：**[`business-diagnosis-production-composite-execution-design.md`](./business-diagnosis-production-composite-execution-design.md)**（**`SHADOW` 语义 / SSE 字段**）、**[`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md)**。  
> **观测清单**：**[`business-diagnosis-shadow-observation-checklist.md`](./business-diagnosis-shadow-observation-checklist.md)**。  
> **边界**：**不接 PRIMARY**；**不替换** **`finalAnswerText` / `answerPreview`**；**`compositeShadowFinalAnswerReplaced` 须恒 `false`**。  
> **用途**：真实生产开灰度（`shadow.enabled`、白名单、限流 cap）时与 SRE/运营对齐；**C-66** 集中 dashboard / 跨实例限流见 **[`next-business-capability-roadmap.md`](./next-business-capability-roadmap.md)** §6。

## **1. `SHADOW` 当前状态（基线）**

| 项 | 说明 |
|----|------|
| **`BusinessDiagnosisCompositeProductionGate`** | **已有**：结构化 **`allowed` / `reasonCode`**；**先于** Composite 旁路判定 **intent/path/ref**（**不读用户原文 **`contains`/regex** 新增**）。 |
| **`ShadowPolicy` / `ShadowDecision`** | **已有**（**C-63**）：在 **`maybeExecuteShadowCompositePlanner`** 内、**`tryExecute` 之前**；**默认 **`shadow.enabled=false`** ⇒ **不旁路** **`tryExecute`**。 |
| **`HARNESS_ONLY`** | **不经 **`ShadowPolicy`****；与本文 **普通 Run `SHADOW`** 灰度策略 **独立**。 |
| **旁路语义** | **`executionMode=SHADOW`** 时 **只旁路执行** Composite（**PlannerExecutor + Readonly Composer**）；**与用户主终稿并行观测**，**不换终稿**。 |
| **终稿契约** | **`compositeShadowFinalAnswerReplaced` 须恒 `false`**；**`compositeFinalAnswerText`** 仅 **SSE / 调试摘要**，**不得**覆盖 **`finalAnswerText` / `answerPreview`**。 |
| **C-63 验收** | **三轮**（**shadow 关 / whitelist 命中 / whitelist 未命中**）已通过；详见 composite **§17.2**。 |
| **性能告警** | **样例**单次旁路墙钟 **≈ 27s**（**`compositeShadowLatencyMs` ~27 000ms**）；与 **legacy 同请求**叠加 **四域读放大**（**§13.3**），上线必须 **限流 + 小范围白名单**。 |

---

## **2. 推荐灰度范围（谁、什么场景）**

### **2.1 阶段一（强烈建议）**

- **只允许内部 / 可追责账号**：**`userWhitelist`** 为主闸门；人数 **极少**（例如个位数），可 **逐人登记、可回滚**。  
- **`distributerId` / `departmentId`（或 department 名单）**：仅 **少量、明确授权** 的租户 / 门店维度；与 **user** 维 **OR** 语义下仍须 **运营可控**，避免「一开一大片」。  
- **`scopeWhitelist`**：**STORE** 与 **GROUP** **分开开**，**不要同一时段对两种 scope **大范围**同时放开**（配置与监控维度清晰，避免混淆）。  
- **意图 / 问法收敛**：**每次灰度只开一个业务场景** — 例如固定 **「这个月经营得怎么样？」** 类 **经营诊断 Composite** 路径；**不乱扩**其他自然语言变体到 **旁路全量**（**Gate** 已约束 path/intent，运营上仍应 **公告内场景** 与配置变更同步）。  
- **`scopeType` 与 VisibleStores**：**GROUP** 会话下 **`departmentId`** 可能 **null** — 必须依赖 **Resolver + Gate** 已物化的 **`AiResolvedOrgScope`**；灰度清单须与 **现网 GROUP 会话** **对齐演练**后再放量。

### **2.2 不推荐**

- **生产全用户** + **`shadow.enabled=true`** **且无名单 / 无限流**。  
- **FIRST** 放量即 **多租户、多 scope、多意图** 同时放开。

### **2.3 何时能开 · 开多久**

- **能开**：**C-63** **三轮验收**已收口（composite **§17.2**）；**SRE / 后端负责人** 对 **白名单 + cap + 观测面板/日志抓取** **书面或工单确认**；若环境允许，**staging**（或等价）上 **同等 `shadow.*` 语义** **抽检**再放生产。  
- **开多久**：以 **离散批次**为单位 — **每批**放行后 **24～72h** **高密度**看人看数 + **复盘**；**未复盘不得**单方面 **放大配额或名单**。**不设终局日期**并不等于 **无限开大**：任一 **§5** 触发即 **关闸**。  

---

## **3. 推荐限流策略（`shadow.*`）**

配置键与语义见 composite **§16.4 / §16.6**；**进程内 MVP**，多实例需后续 **C-66+** 外挂计数。

| 键 | 用途 | **初期建议（保守）** |
|----|------|----------------------|
| **`shadow.maxRunsPerMinute`** | 全域每分钟进入 **`tryExecute`** 次数上限 | **极小**；若与 **小时 cap** 并用，以 **更严者** 为准。 |
| **`shadow.maxRunsPerHour`** | 全域每小时 cap | **每小时 3～5 次**量级起步（按 **实例** 计；多实例则 **总流量 ≈ N× cap** — 须心理预期或 **C-66+** 统一计数）。 |
| **`shadow.cooldownSeconds`** | 同一 **`userId` / `distributerId`** 两次旁路最小间隔 | **建议开启**（例如 **60～300s** 级），避免单人连点 **打满** 四域。 |
| **`GROUP` vs `STORE`** | 读放大与 SQL 形态 | **GROUP 更谨慎**：四域 **多店可见性** 更重；**小时 cap** 建议 **低于** 同等 STORE 试错（例如 **STORE 先试** 再 **GROUP 更低配额**）。 |

**原则**：**先 cap 到人能数得过来的请求量**，再按 **metrics** **缓慢**上调；任何 **调高** **须书面 / 工单** 留痕。

---

## **4. 必须观察的指标**

### **4.1 SSE / 摘要字段（已实现）**

- **`compositeShadowLatencyMs`**：**P50/P95/P99**（需 **日志或采集** 聚合；单行 SSE 仅能 **事后** CSV）。  
- **`compositeExecutionSuccess`**、**`compositeFallbackRequired`**  
- **`compositeExecutionErrorCode` / `compositeExecutionErrorMessage`**（或等价摘要键）  
- **`compositePlannerDegradedSteps`**、**`compositePlannerOverallStatus`**（若透出）  
- **trace 侧 **`usedTools`****（是否与 **四域 Tool** 预期一致；**异常重复 / 缺口**）。  
- **`compositeFinalAnswerText`**：**是否非空**、长度 **是否暴增**（**SSE 体积**）。  
- **`compositeShadowSkipped` / `SkipReason` / `ThrottleHit` / `WhitelistMatched`**：**灰度闸**是否按预期挡住 **超额** 请求。  

### **4.2 主链路与健康**

- **legacy**：**`answer_delta.text`** / **`finalAnswerText`** / **`answerPreview`** **是否与未开 SHADOW 时一致好**（**无空白、无报错、无时序错乱**）。  
- **DB / Tool**：若有 **APM / 慢查询日志 / Tool 耗时日志**，须对比 **放量前后** **同一租户、同窗口**；关注 **热点表 QPS**。  
- **SSE / 端到端**：**首包、`run_finished` 延迟** 是否 **可被用户感知变慢**。

### **4.3 周期**

- **开闸后至少 24～72h** **密集看人看数**；稳定后再 **逐级**放宽 **whitelist / cap**。

---

## **5. 必须立即关闭 `SHADOW` 旁路的条件**

满足 **任一条**，建议 **`shadow.enabled=false`** **或** **收紧到等价无流量**，并 **复盘**，**未经批准不再开**：

1. **旁路平均 / P95 耗时** **明显高于** **C-63 基线**（例如 **持续 > ~30–40s** 且无合理解释）或 **上涨趋势**明显。  
2. **DB 压力**升高：**连接池打满**、**慢查询** **与 Composite 时间段** **相关**。  
3. **Composite 失败率高**：**`compositeExecutionSuccess`** **长期偏低**，或 **`compositeFallbackRequired`** **异常偏多**。  
4. **任一四域 Tool** **错误率 / 超时** **放大**（相对 **legacy 同请求**）。  
5. **SSE 输出变慢** 或 **前台体验**（首字、流式、超时）**受到可感知影响**。  
6. **错误 scope / 口径**：例如 **GROUP** 会话下 **Composite 正文或 summary** **滑成单店口径**、**visibleStores** 与 **配置** **不一致**（**诚实性 / 合规** 风险）。  
7. **任何疑似** **`finalAnswerText` / `answerPreview` **被 Composite 替换**** 或与 **legacy** **错乱** — **零容忍**，**先关后查**。

**操作**：关闭后依赖 **默认值** **`shadow.enabled=false`** 即可 **停止旁路** **`tryExecute`**（**Gate** 仍可 **allowed**）；必要时可同时 **`productionEnabled=false`** **`executionMode≠SHADOW`**（**运维总闸**，见 composite **§16 / §17**）。

---

## **6. C-65：**[`business-diagnosis-shadow-observation-checklist.md`](./business-diagnosis-shadow-observation-checklist.md)**（观测与复盘清单）**

**不重复粘贴全文**：批次须记录的 **字段表**（**`userId` / `distributerId` / `departmentId` / `scopeType`**、`compositeGate*`、`compositeExecuted`、`compositeExecutionSuccess`、`compositeFallbackRequired`、`compositeExecutionError*`、`compositePlanner*`、`compositeShadow*`、**`compositeFinalAnswerText` 非空**、**legacy 正常**）、**每日复盘表**、**扩大灰度** 与 **暂停灰度** 条件 — 均以 **该文件**为 **C-65 权威**。

---

## **7. C-66 以后再考虑**

以下 **不作为 C-64 / C-65 放行或扩灰前置条件**，等 **`SHADOW` 稳定 observability + 低风险放量**后再排期：

- **日志聚合 metrics**：按 **租户 / path / Tool** **分桶** RED、histogram。  
- **Dashboard**：**Grafana / 自建** 聚合 **`compositeShadow*`** **与** infra。  
- **跨实例限流**：**统一 cap**（**Redis** 等），替代进程内 **`shadow.maxRuns*`** MVP。  
- **四域重复查询优化**：Planner **剪枝**、并行度、超时 **分域**（与 **composite §13.3** 对齐）。  
- **只读复用 legacy `toolResults`**：**须**单独 **ADR**（信任边界、TTL、一致性）。  
- **PRIMARY**：**预研须在 `SHADOW` 稳定并完成风险复盘之后**；**不接 PRIMARY** **仍是** **硬边界**。

---

## **参考**

- [`business-diagnosis-production-composite-execution-design.md`](./business-diagnosis-production-composite-execution-design.md) — **§13.3** 读放大、**§16～§19**、`ShadowPolicy`  
- [`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md) — **Gate**、**§C-62～C-65**  
- [`business-diagnosis-shadow-observation-checklist.md`](./business-diagnosis-shadow-observation-checklist.md) — **C-65** 观测与复盘清单  
- [`planner-executor-v1-design.md`](./planner-executor-v1-design.md) — **§27** Composite 生产链路索引  

**文档版本**：灰度策略（**`ShadowPolicy` 已实装**）+ **C-65 观测清单**；**C-66** dashboard 见 **[`next-business-capability-roadmap.md`](./next-business-capability-roadmap.md)** §6。
