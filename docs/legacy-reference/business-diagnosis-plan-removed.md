# BusinessDiagnosisPlan / Builder / PlanNode — Historical removed

> **D-AI-FILE-INVENTORY-CLEANUP-P2（2026-05-20）**  
> 仓库内已无 `BusinessDiagnosisPlan.java`、`BusinessDiagnosisPlanBuilder.java`、`BusinessDiagnosisPlanNode.java`。  
> **勿**在 Cursor 重构中恢复为现网主链。

---

## 现网替代（`business_diagnosis_path`）

| 历史 | 现网 |
|------|------|
| `BusinessDiagnosisPlanNode` 挂载 | `StubOutcomeReviewNode` → `DiagnosisPlanBuilder.attachIfApplicable` |
| `BusinessDiagnosisPlanBuilder` 读 tool 信封 | `DiagnosisPlanBuilder` **只读** 四域 `*AnswerPlan` |
| `BusinessDiagnosisPlan` DTO | `DiagnosisPlan`（`OVERALL_BUSINESS_DIAGNOSIS`） |
| 门店优先 `storePriorityRanking` 块 | `DiagnosisPlan` + `BusinessDiagnosisAgentV1.enrich` + `DiagnosisDeterministicRenderer` |
| Harness `businessDiagnosisPlan` 嵌套摘要 | **已移除**；Replay 以 **`diagnosisPlan`** 嵌套对象 + 扁平 **`diagnosisPlan*`** 为准 |
| Harness `businessDiagnosisPlanExists` 等扁平键 | **P3 deprecated compat**：Summarizer 仍镜像，与 **`diagnosisPlanExists`** 同义，不代表旧 DTO |

### Harness 键名（P3，2026-05-20）

| 推荐（现网 `DiagnosisPlan`） | Deprecated compat（勿当 DTO 名） |
|------------------------------|--------------------------------|
| `diagnosisPlan` / `diagnosisPlanPresent` / `diagnosisPlanExists` / `diagnosisPlanType` | `businessDiagnosisPlanExists`、`businessDiagnosisPlanType` |
| 同上 | `harnessReplayBusinessDiagnosisPlanType`（Explorer `probe` 前缀） |
| `diagnosisQuestionType` + `diagnosisTopStoreName` 等（`DiagnosisPlan.debug`） | `storePriorityRanking*` / `harnessReplayStorePriorityRanking*`（门店优先排行摊平） |

未找到：`harnessReplayBusinessDiagnosisPlanExists`（历史文档笔误或未实现）。

**仍活跃（勿删）**：`BusinessDiagnosisAgentV1` — 非 Graph 节点，为 `DiagnosisPlan` 的 **enrich** 工具类。

---

## 与 Composite 的边界

`BusinessDiagnosisComposite*`（`BusinessDiagnosisCompositeAnswerPlan` 等）为 **PlannerExecutor 实验链**：

- **HARNESS_ONLY**：Harness `GRAPH_RUN` 同步跑完后旁路执行  
- **SHADOW**：普通 `/api/ai/runs` 图完成后旁路；**不写** `finalAnswerText`  
- **PRIMARY**：枚举存在，**未接**用户可见主链  

详见 `BusinessDiagnosisCompositeExecutionMode`、`AiRunService#maybeExecuteShadowCompositePlanner`。

---

## 文档漂移清理

若旧文档仍写「`BusinessDiagnosisPlanBuilder` / `renderStorePriorityRanking(BusinessDiagnosisPlan)`」，以本文与 `docs/ai/business-overview-diagnosis-domain-capability-matrix.md` §9 为准。
