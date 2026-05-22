# 下一阶段业务能力路线图（**D-1**）

> **读者**：产品经理、Harness / Planner 工程师、后端负责人。  
> **性质**：路线图与优先级；重大编码任务须另开 **`docs/ai/*.md`** 设计后再动工。  
> **前提**：Composite 经营诊断 **生产安全框架** 已实装（四域 RealBridge、`BusinessDiagnosisCompositeAnswerPlan`、`BusinessDiagnosisCompositeProductionGate`、`HARNESS_ONLY`、`SHADOW` + `ShadowPolicy`）；灰度运营见 shadow rollout / observation 文档。

**交叉引用**：阶段边界与进度 — **[`TODO_MULTI_AGENT.md`](../TODO_MULTI_AGENT.md)**；PlannerExecutor / Composite **设计细节** — [`planner-executor-v1-design.md`](./planner-executor-v1-design.md)、[`business-diagnosis-composite-plan-design.md`](./business-diagnosis-composite-plan-design.md)。

---

## 1. 当前 Harness / Composite / Shadow 框架完成到什么程度？

| 能力域 | 状态（摘要） |
|--------|----------------|
| **四域 Adapter + Tool** | Revenue / Purchase / StockReduce / DishProfit — **Hydrated RealBridge**，经 **Harness / curl** 验收；Composite 可走 **STORE** 与 **GROUP**（**C-35 / C-48**）四数据域真实链路。 |
| **PlannerExecutor** | 多步计划、**StepResult**、**degradedSteps**、**usedTools**、**trace**；**CONTINUE_WITH_DEGRADED** 等策略已纳入设计并实现路径。 |
| **Composite AnswerPlan** | **`BusinessDiagnosisCompositeAnswerPlan`**：四域 summary、**dataCoverage**、**diagnosisSignals**、**riskLevel**、**keyFindings**、**suggestedNextQuestions**、**summaryText** 等结构化承载。 |
| **Readonly Composer** | **只读** AnswerPlan；**不重读 toolResults**、**不调 LLM**、**不编造数据** → **finalAnswerText**（Harness / 观测链路）。 |
| **Composite Production Gate** | **`BusinessDiagnosisCompositeProductionGate`**：**只读** `AiResolvedQueryContext` / `AiRunState`；**不读用户原文**作路由；**不用 contains/regex**；结构化 **intent / path / scope / time** 判定是否允许进入 Composite。 |
| **HARNESS_ONLY** | Composite **PlannerExecutor** 仅在 Harness **`GRAPH_RUN`** 编排下执行既定 case；**不**把 Harness **GraphCase** 当成 **`/api/ai/runs`** 的唯一生产入口。 |
| **SHADOW** | 普通 run 可 **旁路**执行 Composite；**不替换** legacy **finalAnswerText** / **answerPreview**；输出 **compositeExecution\*** / **compositeShadow\*** 供观测。 |
| **ShadowPolicy** | 白名单、**scope** 白名单、分桶限流、cooldown；默认 **关闭**。 |
| **Rollout / 观测** | **C-64** 灰度策略、**C-65** 人工复盘清单已具备；**不**在本路线图内继续铺开 **运维大盘**（见 §6）。 |

**结论**：**「能安全地、可观测地跑一次 Composite」**的工程闭环已对齐；下一阶段重心应从 ** Shadow 深挖**转向 **老板问得出、答得稳的业务能力**。

---

## 2. 哪些框架能力已经够用，暂时不要继续深挖？

以下条目 **刻意「够用即止」** — 除非出现生产事故或与 P0 业务能力 **硬耦合**，否则 **D-2 及短期内不默认排期**：

| 类目 | 说明 |
|------|------|
| **Shadow dashboard / 集中 metrics** | **C-66** 暂缓；现有 **compositeShadow\*** + 日志 + **C-65** 表足够支撑 **小规模灰度人肉复盘**。 |
| **Redis / 跨实例分布式限流** | 同属 **C-66**；单机 / 进程内 **ShadowPolicy** 已满足当前「默认关 + 小规模试跑」。 |
| **PRIMARY 替换终稿** | **不接** — **finalAnswerText** / **answerPreview** **保持 legacy**；Composite 产出仅 **平行观测**或 Harness。 |
| **Gate / Composite 与用户原文 heuristic** | 继续 **禁止** contains/regex 路由；不要将「补案例」变相做成字符串匹配。**意图**仍须走 **结构化解析 / Lexicon / 模板**。 |
| **Harness GraphCase → 线上主入口** | GraphCase **仅**回归与对齐契约；线上主入口仍是 **`/api/ai/runs`** 及既有 Master 拓扑（**不改 Master 主调度**仍为默认红线，除非单列任务）。 |

---

## 3. 下一阶段主业务能力候选

问题形态按 **老板自然语言** 聚合（与现行四域 Tool **能力对齐**或可自然延伸）：

- **全景 / 诊断型**：这个月经营怎么样？哪里有问题？成本压力来自哪？AAA 和汀兰谁更好？
- **单域事实型**：哪店营业额最高/最低？采购是否偏高？出库/损耗是否异常？哪些菜毛利低？
- **对比 / 排行型**：门店对比、商品/菜品排行、供应商占比。
- **指代与继承型**：「那上个月呢？」「AAA 呢？」「那采购呢？」「哪个最高？」

下文 **§4** 按 **P0～P3** 收敛为可排期条目。

---

## 4. 优先级（P0 / P1 / P2 / P3）

### P0 — 老板最常问、最影响「产品可用」

| ID | 能力 | 与现有后端关系（高层） |
|----|------|-------------------------|
| **P0-A** | **本月 / 给定时间窗经营怎么样**（单店或多店一句话结论 + 可追溯摘要） | 依赖 Composite 或 **多域 ROUTED/MULTI** 已与 **ResolvedContext** 对齐；须 **AnswerPlan / dataCoverage** 可解释。**不**必先上 PRIMARY。 |
| **P0-B** | **哪店最好/最差**（营收或综合signals，需定义默认排序口径） | **GROUP** Composite 已有 harness；产品上需 **话术 + 结构化 keyFindings** 与 Gate **intent/path** 对齐。 |
| **P0-C** | **成本压力来自哪里**（采购 vs 出库/损耗 vs 毛利—四域归因边界） | 四域数据已在 Composite；归因 **话术**须 **Composer 只读**、**signals 规则**可审计。**禁止**随口 LLM 编造比例。 |
| **P0-D** | **采购是否偏高 / 出库是否异常 / 哪些菜毛利低** | 分别对应 **purchase_overview / stock_reduce_query / dish_profit_analysis**；可能 **单域**即可，须有 **阈值/同比**等产品口径（可先文档后配置化）。 |
| **P0-E** | **两店对比**（例：AAA vs 汀兰） | **GROUP** scope + 四域 summary；Gate 与 **`visibleStores`** 契约已存在；补齐 **intent 命名**与用户问法映射（**结构化**，非正则抢跑）。 |

### P1 — 单域深挖（事实 + 排行 + 拆分）

| ID | 能力 |
|----|------|
| **P1-1** | 营收门店对比（表格级事实，仍以 AnswerPlan 为界） |
| **P1-2** | 采购商品金额排行、供货商采购占比 |
| **P1-3** | 出库商品排行；损耗 / 报损 / 退货 **拆分**（若数据层已有字段则可规划；**不改 SQL** 为当前红线则只做「缺口说明」文档） |
| **P1-4** | 菜品毛利最低排行、**指定菜品**毛利分析 |

### P2 — 多轮上下文与回答体验

| ID | 能力 |
|----|------|
| **P2-1** | 时间继承、门店继承、业务域切换（「那上个月呢？」「AAA 呢？」） |
| **P2-2** | **`suggestedNextQuestions`** 与 **follow-up** Resolver 对齐；与 **AiConversationTurnMemory** 共生迭代 |

### P3 — 前台与上线治理（长期）

| ID | 能力 |
|----|------|
| **P3-1** | AI 回答区、建议追问 UI、调试面板 |
| **P3-2** | 数据覆盖 / 降级可视化 |
| **P3-3** | Shadow dashboard、PRIMARY 预研（与 **§6 C-66** **明确切割**） |

---

## 5. 推荐的下一条要实现的主链路

**主推（D-2 默认提案）**：**P0-A + P0-B + P0-E 的合体 —「经营简报 / 对标」主轴**

**理由简述**：

1. 直接对应老板 **「这个月怎么样？谁家好？两家比比？」**，与已完成 **GROUP Composite + AnswerPlan + Readonly Composer** **同向**，复用度高。  
2. 技术风险主要在 **产品与 Gate**：用 **结构化 intent/path**（及已有 Lexicon）扩 **问法覆盖面**，而非新造一条并行执行栈。  
3. **不依赖** PRIMARY、Shadow dashboard、Redis — 与现行 **暂缓项**一致。  
4. 验收路径清晰：**curl / Harness replay**；对比 **compositeShadow\***（若开 SHADOW）与 **Harness summary**；**不重写 SQL / Tool**。  

**备选**：若产品与运营坚持 **先有单域爽点**，则次选 **P0-D（采购偏高 / 出库异常 / 低毛利菜品）** 中 **一条** 做 **单域 ROUTED** 深化 + **AnswerPlan** 模板 — 仍优先 **文档化口径** 再动 Java。

---

## 6. C-66（metrics / dashboard / Redis 跨实例限流）— 明确暂缓

以下内容 **本阶段不立项、不排期、不补充实现级设计**（与 **Composite 阶段收口**、**`TODO_MULTI_AGENT.md`** 一致）：

- 集中 **metrics** 与 **Shadow dashboard**  
- **Redis** 或跨实例 **分布式限流**  
- 任何 **PRIMARY** 切换或 **替换 finalAnswerText / answerPreview**  

若未来重启，应单开 **C-66** 或 **E-1** 文档，与 **业务能力 D 线**解耦。

---

## 7. D-2 建议任务（承接 D-1）

**目标**：把 **§5 推荐主链路**落成 **可验收的最小增量**（仍以 **文档 / 契约** 为先，Java 次步、单任务 PR）。

| 序号 | 任务 | 产出物 |
|------|------|--------|
| **D-2.1** | **老板 TOP 问法 → 结构化 intent/path 映射表**（含 GROUP / 时间窗 / 单店） | 新设计文档或在 **[`business-diagnosis-production-gate-design.md`](./business-diagnosis-production-gate-design.md)** 增 **附录**；**禁止**用户原文正则路由。 |
| **D-2.2** | **「经营简报 / 对标」AnswerPlan / Composer 话术与字段对齐清单**（keyFindings、dataCoverage 提示、降级 copy） | `docs/ai/*.md`；对齐 **Readonly Composer** 边界。 |
| **D-2.3** | **Harness / replay case 清单**：补 **P0 问法**对应的 **GraphCase 或回放 JSON 期望字段**（`diagnosisSignals`、`riskLevel`、`summaryText` 等） | 更新 **[`AI_HARNESS_REPLAY_CASES.md`](../AI_HARNESS_REPLAY_CASES.md)**（若项目约定在此维护） |
| **D-2.4** | **（可选编码窗）** 在 **不动 SQL / Tool / Resolver 主逻辑** 前提下，仅做 **白名单映射或 Gate 增补** — 须 **单列任务**评审 | Java 改动需与用户确认 **frozen 边界** |

**协作提醒**：默认 **不改 `src/test/**`**；验证由用户本地 **curl / replay**，Agent 交付 **命令与期望值说明**。

---

## 8. 版本

| 版本 | 日期 | 说明 |
|------|------|------|
| **D-1 v1** | 2026-05-14 | 首版：**业务能力**路线图；**C-66** 暂缓；推荐 **Composite GROUP「经营简报/对标」**为下一主轴。 |
