# D-11 权限边界 — Frozen Role Fixture 基线（文档-only）

> **目的**：统一 **Harness / 本地 Replay** 的 `userId`、`departmentId`、`scopeMode` 与 **`gb_department_user` 实表**一致，避免「角色说是 AAA 店长，请求却锚在汀兰」类前提污染后续 **D-11** 权限断言。  
> **范围**：**仅梳理与约定**；**不改** Java、Composer、Tool、权限逻辑、**不新增/修改** test。  
> **权威**：角色 → 权限以 **`docs/PERMISSION_MODEL.md`**、`AiRoleMapper` / `AiPermissionGuard` 为准；组织范围以 **`docs/DOMAIN_ORG_MODEL.md`** 为准。

---

## Final Frozen Role Fixture（D-11 final spot check）

下列为 **D-11 权限边界回放收口后冻结的四账号**（与目标库 **`gb_department_user`** 一致为前提）；Harness Replay 须 **`userId` / `departmentId` / `scopeMode`** 与本表对齐，避免断言污染。

| 角色 | `userId` | `roleCode`（典型） | `scopeMode` | `departmentId`（请求锚点） | 门店语义 |
|------|----------|-------------------|-------------|---------------------------|----------|
| 集团管理员 | **3** | `GROUP_MANAGER` | **`GROUP`** | 可不传（集团会话） | 集团广角 |
| 门店采购 | **2** | **`STORE_PURCHASER`**（口语「采购员」） | **`STORE`** | **3** | **汀兰餐厅** |
| 库房 | **1** | `WAREHOUSE_MANAGER` | **`STORE`** | **1** | **AAA** |
| AAA 店长 | **4** | `STORE_MANAGER` | **`STORE`** | **1** | **AAA** |

---

## D-11 最小门卫（已通过）

手工 / Replay **spot check** 约定断言方向（见 **`docs/AI_HARNESS_REPLAY_CASES.md`** **D-11** 小节）：

| Persona | 要点 |
|---------|------|
| **集团管理员**（`userId=3`，`scopeMode=GROUP`） | 可见 **多门店**；经营诊断 / 对比类问法可出 **排行、双店对比** 等集团视角表述（在数据与权限允许时）。 |
| **门店采购**（`userId=2`，`departmentId=3`） | **采购**链路可看；问 **营业额** 时须有 **权限提示**，且 **不得**再出现「库房端可继续询问…」收尾（应为 **采购视角** follow-up）。 |
| **库房**（`userId=1`，`departmentId=1`） | **库存 / 出库核销**等可看；问 **营业额** 时权限提示；**经营诊断**路径 **降级为库房视角**（非集团排行口吻）。 |
| **AAA 店长**（`userId=4`，`departmentId=1`） | **单店**范围可看；追问 **跨店 / 不可见门店** 须有 **权限提示**；单店 STORE scope 诊断终稿中 **出库/核销等措辞须为本店口径**，不得误称「集团口径」。 |

---

## 公共修复点（收口备忘，非代码清单）

以下为 D-11 周期内已落地的 **文案 / Composer / Renderer** 走向（实现细节以仓库为准；**本文档不改代码**）：

1. **Composer 权限拒绝短路**：营业额等无权限时优先 **短文权限答复**，避免幻觉数值或冗长误诊。  
2. **`business_diagnosis` 权限降级 Renderer**：库房 / 采购等非完整经营 persona 时 **确定性降级正文**，抑制越权「集团排行」类话术。  
3. **采购员 `STORE_PURCHASER` · revenue denied follow-up**：营业额被拒后的「可继续询问」**采购视角**文案，与库房收尾区分。  
4. **STORE scope 终稿措辞**：单店场景下将误用的 **「集团口径」** 纠正为 **「本店口径」**（Composer / Renderer 层补丁）。  

---

## 非阻塞 polish（后续可做）

- **STORE scope、单店诊断**中，标题或小节如需提及兄弟门店，应避免 **「其它门店怎么样」** 等易被理解为跨店明示的问法；后续可统一改为 **「当前权限范围内」** 等中性表述。**不阻塞** D-11 冻结发布。

---

## 1. Replay 中 `userId` / `departmentId` / `scopeMode` 如何进入权限解析

### 1.1 入口：`POST /api/ai/harness/replay`

请求体 **`AiHarnessReplayRequest`** 主要字段：`userId`（必填）、`departmentId`、`distributerId`、`scopeMode`、`messages[]`、`replayMode`、`caseId` 等。

### 1.2 会话创建（首轮之前）

**实现**：`AiHarnessReplayService#replay`。

1. **`AiConversationScopeMode inferScopeMode(req)`**  
   - 若请求体 **`scopeMode` 非空**：`AiConversationScopeMode.fromApiString(req.getScopeMode())`。  
   - 否则：有 **`departmentId`** → **`STORE`**；仅有 **`distributerId`** → **`GROUP`**；否则抛错（创建会话失败）。

2. **`conversationCoreService.createNewConversationForAgentRun(departmentId, distributerId, mode, userId)`**  
   - 将会话锚点写入 **`gb_ai_conversation`**（与正式 Run 一致），后续同一会话内多轮共用该 **`conversationId`**。

### 1.3 每一轮 Run

**实现**：同一 `replay` 循环内构造 **`AiRunCreateRequest`**：

| 字段 | 来源 |
|------|------|
| `userId` | `req.getUserId()` |
| `departmentId` | `req.getDepartmentId()` |
| `distributerId` | `req.getDistributerId()` |
| `conversationId` | 上文新建的会话 id |
| `message` | 当前轮用户文案 |
| `scopeMode` | 若 **`req.getScopeMode()`** 有文本则 **`runReq.setScopeMode(...)`**（与 **`AiRunCreateRequest`** 注释一致：可与会话模式对齐） |

**分支**：

- **`replayMode == GRAPH_RUN`**：`AiRunService#executeBusinessGraphSyncForHarness(runReq, …)` — 全图执行，终态 **`AiRunState`** 含 **`resolvedQueryContext`**、**`permissionDenials`**、**`finalAnswerText`** 等。  
- **否则（Resolver-only）**：**`AiUserContextResolver#resolve(runReq)`** → **`AiResolvedQueryContextResolver#resolve(...)`**；身份仅依赖 **`userId`** 查表，与正式 **`POST /api/ai/runs`** 同源。

### 1.4 身份与权限（与 `departmentId` 的关系）

**`AiUserContextResolver#resolve(AiRunCreateRequest)`**（摘要）：

1. 用 **`req.getUserId()`** 作为 **`gb_department_user.gb_department_user_id`** 主键 **`getById`**。  
2. 读 **`gb_du_admin`** → **`AiRoleMapper.requireAdmin`** → **`roleCode` / `roleName` / 默认 `permissions`**。  
3. 读 **`gb_du_department_id` / `gb_du_distributer_id` / `gb_du_department_father_id`** → 写入 **`AiUserContext`**（含 **`allowedStoreIds`** 等）。  
4. **`normalizeToStoreRootDepartmentId`**：沿 **`gb_department`** 父链归一到 **门店根**（`father_id = 0` 的部门）。

**要点**：**`roleCode` 与「可见门店锚点」首先由库表用户行决定**；请求体 **`departmentId`** 参与 **Run 级组织求交**（**`AiRunScopeIntersectService`** 等），若与「该 userId 在表中的挂靠门店」不一致，会出现 **可见门店是请求部门子树** 与 **产品口头「某店长」** 对不上的情况 —— 即本次要消除的 **测试前提污染**。

---

## 2. D-11 Frozen Role Fixture — 约定总表

**已验收冻结快照**见文首 **Final Frozen Role Fixture**；本节为同类约定的展开（**仍须在你方目标库用 SQL 核对 `gb_department_user`**）。  
门店根占位与 **`docs/AI_HARNESS_REPLAY_CASES.md`** / 采购 Case 一致：**AAA → 门店根 `1`**，**汀兰餐厅 → 门店根 `3`**；**`distributerId` 占位常用 `2`**（与 API 文档一致；**若库中分销商 id 不同，整表仅换 `distributerId`，原则不变**）。

| 角色标签 | 建议 `userId` | 建议 `scopeMode` | 请求体 `departmentId` | 请求体 `distributerId` | 说明 |
|----------|---------------|-------------------|------------------------|-------------------------|------|
| **GROUP_MANAGER** | **3**（冻结后固定） | **`GROUP`** | **可选**；若传，多为**管理/挂靠部门 id**，**不得**误当「唯一查询门店」 | **必填**（集团会话） | 与 **`AI_HARNESS_REPLAY_CASES.md`** 一致：集团 case 建议 **`scopeMode: GROUP`**，否则仅传 `departmentId` 时 **`inferScopeMode` 会落成 `STORE`**，与集团预期不符。 |
| **PURCHASER（门店采购）** | **2** | **`STORE`** | **冻结：`3`（汀兰餐厅门店根）**；须与 **`gb_department_user`** 该行挂靠一致 | 与表一致 | **`scopeMode` 显式 `STORE`**。**Final fixture** 见文首总表；若库中 `userId=2` 非 **`STORE_PURCHASER`**，则 **不能** 当采购基线（见 §5）。 |
| **WAREHOUSE（库房）** | **1** | **`STORE`** 或会话与 Run 支持的库房锚点 | **`gb_department_user` 挂靠的部门 id**（库区或可归一到门店根后的锚点，以库表为准） | 与表一致 | **`AiOrgScope.scopeType`** 在链路上常呈现为 **`WAREHOUSE` / `DEPARTMENT`**（见 **`PERMISSION_MODEL.md` §4**），以 **`resolvedQueryContext.orgScope.scopeType`** 实算为准。 |
| **STORE_MANAGER** | **4** | **`STORE`** | **必须为 AAA 门店根（占位 `1`）** 或与该用户表行一致之锚点，**禁止**用 **`3`（汀兰）冒充 AAA 店长 | 与表一致 | 若 **口头约定「userId=4 = AAA 店长」**，则 **`gb_department_user.gb_DU_department_id` 归一后必须等于 AAA 门店根**；Replay **`departmentId`** 必须同锚点，否则会出现「店长 AAA、可见汀兰」的 **前提不一致**。 |

---

## 3. 分角色明细（冻结验收清单）

> **填表约定**：`expected visibleStores` / `visibleWarehouseIds` 以 Harness 摘要里的 **`resolvedQueryContext.orgScope.visibleStores`**、**`dataScope.visibleWarehouseIds`**（或等价探针键）为准；**库房列表依赖库内 `gb_department` 树**，下列 **库房 id 用「环境实测」占位**。  
> **allowed / denied tools**：与 **`docs/PERMISSION_MODEL.md` §3 / §6** 及 **`AiPermissionGuard`** 一致；`dish_profit_analysis` 另含 **角色拒答**（采购 / 库房 / 配送等），见 **`PERMISSION_MODEL.md`** **`evaluateDishProfitAnalysisInvocation`** 说明。

### 3.1 GROUP_MANAGER（建议 `userId=3`）

| 项 | 值 |
|----|-----|
| **userId** | `3`（冻结前在库中确认该行 **`gb_du_admin=0` → `GROUP_MANAGER`**） |
| **loginDepartmentId / 请求 `departmentId`** | **可不传**；若传，应为集团侧挂靠部门，**不以该 id 作为单店 SQL 唯一锚点** |
| **distributerId** | 与库一致（文档占位 **`2`**） |
| **roleName（映射）** | 集团管理端 → **`GROUP_MANAGER`** |
| **expected scopeType** | **`GROUP`** |
| **expected visibleStores** | 集团权限下多门店根（占位 **`[1,3]`** 等同 **`AI_HARNESS_REPLAY_CASES`** 采购 Case；以库为准） |
| **expected visibleWarehouseIds** | 依 **`dataScope` / 组织解析**环境实测 |
| **allowed tools** | **`VIEW_*`** 全量所映射的全部经营主线 Tool（含 **`revenue_query`**、**`dish_profit_analysis`**（在 Guard 通过前提下）等），见 **`PERMISSION_MODEL.md`** |
| **denied tools** | 默认无；若账号被刻意剥权则另议 |
| **禁止出现的话术（D-11 敏感）** | 在无拒答时：不应 **仅靠幻觉** 输出「营业额为 0」「可用数据是零」等 **未经验权工具支撑** 的结论；若未来刻意测拒答，以 **`permissionDenials`** + Composer 权限短文为准 |

### 3.2 PURCHASER — 门店采购（冻结 `userId=2`，角色须为 `STORE_PURCHASER`，**汀兰**）

| 项 | 值 |
|----|-----|
| **userId** | `2`（**必须**与 **`STORE_PURCHASER` + 汀兰挂靠** 同行） |
| **loginDepartmentId / 请求 `departmentId`** | **冻结：`3`**（汀兰餐厅门店根）；须等于该行 **`gb_DU_department_id`** 归一后的门店根 |
| **distributerId** | 与表一致 |
| **roleName** | 门店采购端 → **`STORE_PURCHASER`** |
| **expected scopeType** | **`STORE`** / **`PURCHASER`** / **`DEPARTMENT`**（以 **`orgScope.scopeType`** 实算为准， **`PERMISSION_MODEL.md` §4**） |
| **expected visibleStores** | **冻结**：通常 **`[3]`**（仅汀兰） |
| **expected visibleWarehouseIds** | 环境实测 |
| **allowed tools** | **`purchase_overview`**、**`stock_reduce_query`**（及库存相关在 **`VIEW_STOCK`** 内）；**不规划** **`revenue_query`** 作完整经营结论（**Historical removed**：`business_overview_query` / `purchase_query` 已删） |
| **denied tools** | **`revenue_query`**（无 **`VIEW_REVENUE`**）；**`dish_profit_analysis`**（采购拒答路径）；**`CostDiagnosisAgent`**（完整成本诊断，含内部毛利推导）；见 **`PERMISSION_MODEL.md` §7**（**Historical removed**：`gross_margin_calculator` Tool） |
| **禁止出现的话术** | 权限被拒场景：**营业额数值 / 「金额为 0」/ 「核对月份」/ 「核对门店归属」** 等冒充真实查询；应以 **权限提示 + 可问方向** 为准（**D-11 Composer 收口目标**） |

### 3.3 WAREHOUSE — 库房（冻结 `userId=1`，角色须为 `WAREHOUSE_MANAGER`，**AAA**）

| 项 | 值 |
|----|-----|
| **userId** | `1`（**必须**与 **`WAREHOUSE_APP` → `WAREHOUSE_MANAGER`** 同行；**勿与种子数据中店长行混淆**，见 §5） |
| **loginDepartmentId / 请求 `departmentId`** | **Replay 冻结：`1`（AAA 门店根或该行挂靠等价锚点）**；须与 **`gb_department_user`** 一致 |
| **distributerId** | 与表一致 |
| **roleName** | 库房端 → **`WAREHOUSE_MANAGER`** |
| **expected scopeType** | 常为 **`WAREHOUSE`** / **`DEPARTMENT`**（以 **`orgScope`** 为准） |
| **expected visibleStores** | **所属门店 / 库房可见范围**（通常窄于集团；**单店或少量门店根**，环境实测） |
| **expected visibleWarehouseIds** | **`orgScope.visibleWarehouses` / `dataScope.visibleWarehouseIds`** — **环境实测** |
| **allowed tools** | **`VIEW_STOCK`** / **`VIEW_PURCHASE`** 范围内：**库存快照**、**`purchase_overview`**、**`stock_reduce_query`** 等（见 **`PERMISSION_MODEL.md`**） |
| **denied tools** | **`revenue_query`**；**`dish_profit_analysis`**（库房拒答）；完整 **`CostDiagnosisAgent`** 等 |
| **禁止出现的话术** | 同采购：**无权限时禁止**假营业额 / 假「数据不足」式经营结论；诊断 path 上 **禁止**「全集团排名 / 综合经营更好」等 **越权经营口吻**（**D-11 诊断降级 Renderer** 目标） |

### 3.4 STORE_MANAGER — AAA 店长（建议 `userId=4`）

| 项 | 值 |
|----|-----|
| **userId** | `4`（**必须**满足 **`gb_du_admin=11` → `STORE_MANAGER`** 且 **`gb_DU_department_id` 归一后 = AAA 门店根 `1`**） |
| **loginDepartmentId / 请求 `departmentId`** | **`1`（AAA 门店根）** 或与表完全一致；**禁止**用 **`3`（汀兰）** 却仍标「AAA 店长」 |
| **distributerId** | 与表一致 |
| **roleName** | 门店管理端 → **`STORE_MANAGER`** |
| **expected scopeType** | **`STORE`** / **`DEPARTMENT`** |
| **expected visibleStores** | **`[1]`**（仅 AAA；占位） |
| **expected visibleWarehouseIds** | 环境实测 |
| **allowed tools** | **`VIEW_REVENUE`**、**`VIEW_COST`**、**`VIEW_PURCHASE`**、**`VIEW_STOCK`**、**`VIEW_DISH_SALES`** 所覆盖的经营 Tool + **`CostDiagnosisAgent`**（见 **`PERMISSION_MODEL.md`**） |
| **denied tools** | 无默认全拒；**`dish_profit_analysis`** 需 **双权限** **`VIEW_DISH_SALES`+`VIEW_COST`**（店长默认具备） |
| **禁止出现的话术** | **本角色不测「无营业额权限」短句**；应断言 **不出现** 无权限门店名、金额（**D-11 泄露门禁**） |

---

## 4. 与仓库内示例种子数据的差异（避免误读）

仓库 **`beData/ai_marketing.sql`** 中 **`gb_department_user`** 仅示例插入 **`gb_department_user_id ∈ {1,2,7}`**：

- **`userId=1`、`userId=2`**：示例均为 **`gb_DU_admin=11`（`STORE_MANAGER_APP`）**，**不是** **`GROUP_MANAGER` / `WAREHOUSE` / `STORE_PURCHASER`**。  
- **`userId=7`**：**`admin=1`（`STORE_PURCHASER_APP`）**，**`gb_DU_department_id=3`（汀兰）** —— 这才是「挂在汀兰」的**门店采购**示例行。

因此：**文档 §2–§3 中的 `userId=1/2/3/4` 为 D-11「目标冻结位」**；若本地仍用该 SQL，**不得**直接把 `userId=1` 当库房、`userId=2` 当采购、**不得**把 `userId=4` 与 **`departmentId=3`** 配对却称 AAA 店长。**冻结前请在目标库执行**：

```sql
SELECT gb_department_user_id, gb_DU_admin, gb_DU_department_id, gb_DU_distributer_id, gb_DU_department_father_id
FROM gb_department_user
WHERE gb_department_user_id IN (1,2,3,4,7);
```

并以结果 **重写** 本节各表的 **`userId` / `departmentId` 是否可用**。

---

## 5. 四个最小 Replay JSON 模板（本地验证用）

以下模板 **仅含最小字段**；**`messages` 请按用例替换**（营业额 / 菜品毛利 / 经营诊断 / 库房问法等）。**`frozenClockDate`** 建议固定，便于时间窗断言。  
**`strictStoreSqlMatch`**：库 id 与文档占位不一致时可 **`false`**（与 **`AI_HARNESS_REPLAY_CASES.md`** 一致）。

### 5.1 GROUP_MANAGER · `userId=3`

```json
{
  "userId": 3,
  "distributerId": 2,
  "scopeMode": "GROUP",
  "frozenClockDate": "2026-05-15",
  "strictStoreSqlMatch": false,
  "replayMode": "GRAPH_RUN",
  "messages": ["〈替换：集团经营/诊断类问句〉"]
}
```

### 5.2 STORE_PURCHASER · `userId=2`，**`departmentId=3`（汀兰）**

```json
{
  "userId": 2,
  "departmentId": 3,
  "distributerId": 2,
  "scopeMode": "STORE",
  "frozenClockDate": "2026-05-15",
  "strictStoreSqlMatch": false,
  "replayMode": "GRAPH_RUN",
  "messages": ["〈替换：采购/核销或越权营业额问句〉"]
}
```

> 若实表中 **`userId=2` 非采购**，请改用 **真实 `STORE_PURCHASER` 主键**，并保持 **`departmentId`** = 该行挂靠门店根（冻结基线为 **汀兰 `3`**）。

### 5.3 WAREHOUSE_MANAGER · `userId=1`（**须库表确认为库房行**）

```json
{
  "userId": 1,
  "departmentId": 1,
  "distributerId": 2,
  "scopeMode": "STORE",
  "frozenClockDate": "2026-05-15",
  "strictStoreSqlMatch": false,
  "replayMode": "GRAPH_RUN",
  "messages": ["〈替换：库存/采购入库或越权营业额问句〉"]
}
```

> **`departmentId`** 必须为 **该库房用户在表中的挂靠部门**；示例占位 **`1`** 仅作 JSON 形状参考。

### 5.4 STORE_MANAGER（AAA）· `userId=4`（**`departmentId` 必须与 AAA 店长表行一致，禁止滥用 `3`**）

```json
{
  "userId": 4,
  "departmentId": 1,
  "distributerId": 2,
  "scopeMode": "STORE",
  "frozenClockDate": "2026-05-15",
  "strictStoreSqlMatch": false,
  "replayMode": "GRAPH_RUN",
  "messages": ["〈替换：本店经营/营业额/诊断问句〉"]
}
```

---

## 6. 交叉引用

- **Replay 字段与门禁**：**`docs/AI_HARNESS_REPLAY_CASES.md`**（含 **`scopeMode` / `departmentId` 陷阱**、**D-11** 一句）。  
- **权限与 Tool**：**`docs/PERMISSION_MODEL.md`**。  
- **组织 / 门店根**：**`docs/DOMAIN_ORG_MODEL.md`**。

---

## 7. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-05-15 | 初版：梳理 Replay → `AiUserContextResolver` 路径；冻结四角色 Fixture 与最小 JSON 模板；声明与 **`beData/ai_marketing.sql`** 示例行差异及 **`userId=4` + `departmentId=3`** 反例。 |
| 2026-05-15 | **D-11 final spot check 收口**：文首 **Final Frozen Role Fixture**（`3/GROUP`、`2+3` 汀兰采购、`1+1` AAA 库房、`4+1` AAA 店长）；**最小门卫已通过**、**公共修复点**、**非阻塞 polish**；采购 Replay 模板 **`departmentId`** 改为 **`3`（汀兰）**。 |
