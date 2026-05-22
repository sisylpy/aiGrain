> 所有涉及 AI 多智能体、经营分析、采购、库存、菜品毛利、权限、组织范围、时间范围、多轮追问的改动，必须先阅读 `docs/AI_AGENT_DEVELOPMENT_GUIDE.md`。

> 任何涉及组织范围、门店、部门、集团汇总、权限范围、经营看板查询的改动，必须先阅读 `docs/DOMAIN_ORG_MODEL.md`。本项目中 `distributerId` 是集团/配送商主体 ID；`gb_department.gbDepartmentFatherId = 0` 的记录才是门店；子部门需要归一化到所属门店。

# AI Run 权限模型（第一版 · 代码配置）

> **事实来源**：业务用户主数据表 **`gb_department_user`**，字段 **`gb_du_admin`**（与 `GbConstants.DepartmentUserRole` 常量一致）。  
> **AI 层原则**：数字 `admin` **不**在 Tool/日志/文档中直接当主键使用；统一经 **`com.nongxinle.ai.mapping.AiRoleMapper`** 映射为可读 **`roleCode`**，再绑定 **`AiPermissions`**，最后由 **`AiPermissionGuard`** 在 Tool / 专线 Agent 前校验。旧 **`AiWorkspaceAccessGuard`**（关键词工作台路由）已删除；现网用 **`AiUserContextResolver`** + **`AiPermissionGuard`**。  
> **`AiUserContext`**：`sourceAdminRole` 存原始 **`admin`**，`roleCode` / `roleName` 为映射结果；`departmentId`/`distributerId`/`departmentFatherId` 来自 `gb_department_user` 挂靠列。

---

## 1. admin 原始值与业务中文含义

| admin | 常量（`GbConstants.DepartmentUserRole`） | 业务含义（简称） |
|------|------------------------------------------|------------------|
| 0 | `GROUP_MANAGER_APP` | 集团管理端 |
| 1 | `STORE_PURCHASER_APP` | 门店采购端 |
| 2 | `GROUP_PURCHASER_APP` | 集团集采 |
| 3 | `WAREHOUSE_APP` | 库房端 |
| 4 | `CENTRAL_KITCHEN_APP` | 中央厨房端 |
| 5 | `DELIVERY_SUPPLIER_APP` | 配送商端 |
| 6 | `DELIVERY_DRIVER_APP` | 配送员端 |
| 7 | `COUPON_APP` | 优惠券 / 营销运营端 |
| 11 | `STORE_MANAGER_APP` | 门店管理端 |
| 12 | `STORE_ORDER_APP` | 门店订货端 |
| 13 | `WINDOW_ORDER_APP` | 窗口订货端 |
| 31 | `WAREHOUSE_PURCHASER` | 库房采购员 |
| 41 | `CENTRAL_KITCHEN_PURCHASER` | 中央厨房采购员 |
| 51 | `REGION_MANAGER_APP` | 区域经理 |
| 52 | `REGION_PURCHASER_APP` | 区域采购 |
| 53 | `REGION_WAREHOUSE_APP` | 区域库房 |

未列出的 **`admin`**：`AiUserContextResolver` 会抛 **`IllegalArgumentException`**，需在 **`AiRoleMapper.BY_ADMIN`** 补行。

---

## 2. admin → AI `roleCode`（`AiRoleCodes`）

与实现类 **`AiRoleMapper`** 静态表一致；中文展示名见同表 **`roleNameChinese`**。

| admin | `roleCode` |
|------|------------|
| 0 | `GROUP_MANAGER` |
| 1 | `STORE_PURCHASER` |
| 2 | `GROUP_PURCHASER` |
| 3 | `WAREHOUSE_MANAGER` |
| 4 | `CENTRAL_KITCHEN_MANAGER` |
| 5 | `DELIVERY_SUPPLIER` |
| 6 | `DELIVERY_DRIVER` |
| 7 | `COUPON_OPERATOR` |
| 11 | `STORE_MANAGER` |
| 12 | `STORE_ORDER` |
| 13 | `WINDOW_ORDER` |
| 31 | `WAREHOUSE_PURCHASER` |
| 41 | `CENTRAL_KITCHEN_PURCHASER` |
| 51 | `REGION_MANAGER` |
| 52 | `REGION_PURCHASER` |
| 53 | `REGION_WAREHOUSE` |

**过渡期 / 单测合成**（**不**读取 `gb_du_admin`）：请求体显式传 `roleCode` 为 **`FINANCE_MANAGER`**、**`MARKETING_MANAGER`** 时走合成路径（见 **`AiUserContextResolver`**）。生产联调应移除对这两者的依赖，改为真实 `admin`。

**历史别名**：`GROUP_BOSS` 在代码中已 **`@Deprecated`**，等同 **`GROUP_MANAGER`**，新文档只写后者。

---

## 3. `roleCode` → 默认 `AiPermissions`（代码表）

实现位置：**`AiRoleMapper.ROLE_PERMISSION_VIEW`**。下列为与产品表一致的主干；若与业务后台有出入，以代码为准并同步改本文档。

| roleCode | 默认 permissions |
|----------|------------------|
| `GROUP_MANAGER` | `VIEW_*` 全量（五类数据）+ `EXPORT_REPORT` + `ACCESS_BUSINESS_WORKSPACE` + `ACCESS_REPORT_WORKSPACE` + `ACCESS_MARKETING_WORKSPACE` |
| `STORE_MANAGER` | `VIEW_REVENUE`、`VIEW_COST`、`VIEW_PURCHASE`、`VIEW_STOCK`、`VIEW_DISH_SALES`、`ACCESS_BUSINESS_WORKSPACE` |
| `STORE_PURCHASER` | `VIEW_PURCHASE`、`VIEW_STOCK`、`ACCESS_BUSINESS_WORKSPACE` |
| `GROUP_PURCHASER` | `VIEW_PURCHASE`、`VIEW_STOCK`、`VIEW_SUPPLIER`、`EXPORT_REPORT`、`ACCESS_BUSINESS_WORKSPACE`、`ACCESS_REPORT_WORKSPACE` |
| `WAREHOUSE_MANAGER` | `VIEW_STOCK`、`VIEW_PURCHASE`、`ACCESS_BUSINESS_WORKSPACE` |
| `WAREHOUSE_PURCHASER` | `VIEW_PURCHASE`、`VIEW_STOCK`、`ACCESS_BUSINESS_WORKSPACE` |
| `CENTRAL_KITCHEN_MANAGER` | `VIEW_STOCK`、`VIEW_COST`、`VIEW_DISH_SALES`、`ACCESS_BUSINESS_WORKSPACE` |
| `CENTRAL_KITCHEN_PURCHASER` | `VIEW_PURCHASE`、`VIEW_STOCK`、`VIEW_COST`、`ACCESS_BUSINESS_WORKSPACE` |
| `COUPON_OPERATOR` | `VIEW_DISH_SALES`、`MANAGE_MARKETING`、`ACCESS_MARKETING_WORKSPACE` |
| `STORE_ORDER` / `WINDOW_ORDER` | `VIEW_PURCHASE`、`VIEW_STOCK`、`ACCESS_BUSINESS_WORKSPACE` |
| `DELIVERY_SUPPLIER` | `VIEW_PURCHASE`、`VIEW_STOCK`、`ACCESS_BUSINESS_WORKSPACE` |
| `DELIVERY_DRIVER` | `VIEW_STOCK`、`ACCESS_BUSINESS_WORKSPACE` |
| `REGION_MANAGER` | 五类 `VIEW_*` + `EXPORT_REPORT` + `ACCESS_BUSINESS_WORKSPACE` + `ACCESS_REPORT_WORKSPACE` |
| `REGION_PURCHASER` | `VIEW_PURCHASE`、`VIEW_STOCK`、`VIEW_SUPPLIER`、`ACCESS_BUSINESS_WORKSPACE`、`ACCESS_REPORT_WORKSPACE` |
| `REGION_WAREHOUSE` | `VIEW_STOCK`、`VIEW_PURCHASE`、`ACCESS_BUSINESS_WORKSPACE`、`ACCESS_REPORT_WORKSPACE` |

**占位类 permission**（暂无独立 Tool 挂载，预留给后续链路）：`VIEW_SUPPLIER`、`EXPORT_REPORT`、`MANAGE_MARKETING`、`ACCESS_REPORT_WORKSPACE`。`ACCESS_BUSINESS_WORKSPACE` 为默认经营分析工作台能力（与 `BUSINESS_CHAT` 图一致）。

---

## 4. 默认组织范围（`AiOrgScopeResolver`）

锚点列来自 **`gb_department_user`**：`gb_du_department_id`、`gb_du_distributer_id`、`gb_du_department_father_id`（父级 ID 已写入 **`AiUserContext.departmentFatherId`**，供后续与组织树策略扩展）。

| roleCode（归类） | `scopeType`（`AiOrgScope`） | 说明 |
|------------------|----------------------------|------|
| `GROUP_MANAGER` | `GROUP` | 集团向；**不按**单店锚点做强校验（`AiPermissionGuard` 对集团敞开）。 |
| `REGION_MANAGER` / `REGION_PURCHASER` / `REGION_WAREHOUSE` | `REGION` | `regionId` ≈ 挂靠部门锚点；与请求部门求交由 **`AiRunScopeIntersectService`**。 |
| `STORE_MANAGER` / `STORE_PURCHASER` / `STORE_ORDER` / `WINDOW_ORDER` | `STORE`/`DEPARTMENT` | 挂靠门店/部门为锚点。 |
| `GROUP_PURCHASER` | `DEPARTMENT` | 集采组织节点为锚点。 |
| `WAREHOUSE_*` / `CENTRAL_KITCHEN_*` | `DEPARTMENT` | 库区/中央厨房部门锚点。 |
| `COUPON_OPERATOR` | `DEPARTMENT` | 营销挂靠部门。 |
| `DELIVERY_SUPPLIER` / `DELIVERY_DRIVER` | `DISTRIBUTER` | 分销商维度 + 部门锚点（实现见 **`AiOrgScope`** 字段组合）。 |
| 合成：`FINANCE_MANAGER`、`MARKETING_MANAGER` | `DEPARTMENT` | 使用请求体 **`departmentId`**；无表数据。 |

更细的子树扩张（门店父级 / 片区根）：与会话侧 **`AiDepartmentUserExpansionResolver`**（按 `admin` 走路径）及 Run 侧 **`AiRunScopeIntersectService`** 叠加使用。

---

## 5. Workspace 入口（权限码保留 · 关键词路由已删）

**`ACCESS_MARKETING_WORKSPACE`**、**`MARKETING_GROWTH`** 等仍可作为 **`AiPermissions` / `AiUserContext`** 的一部分用于未来产品入口，但 **Runtime 已不再通过 `WorkspaceRouterService` 从用户话术解析 `workspaceMode`**（该类与 **`AiWorkspaceAccessGuard`** 已删除）。历史 **`WORKSPACE_ACCESS_DENIED`** 信封见 **`docs/SSE_BACKEND_EVENT_CONTRACT.md`** §6（Runtime 已不再从用户话术解析 `workspaceMode`）。

---

## 6. Tool ↔ 所需 permission（成本 / 经营主线）

| Tool id | permission |
|---------|------------|
| `revenue_query` | `VIEW_REVENUE` |
| `purchase_overview` | `VIEW_PURCHASE`（Planner **`requiredPermissionForTool`** + 专用 **`evaluateToolInvocation`**） |
| `warehouse_stock_overview` | `VIEW_STOCK` |
| `stock_reduce_query` | `VIEW_STOCK` |
| `dish_profit_analysis` | **`VIEW_DISH_SALES`** 且 **`VIEW_COST`**（专用 **`evaluateDishProfitAnalysisInvocation`**，非 `requiredPermissionForTool` OR 语义）。**D-8** `dish_sales_query_path` 与 **成本链**（`cost_diagnosis_path`）第 4 步均执行本品；标价收入读 **`businessInsightSummary.totalListPriceRevenue`**。另有 **角色拒答**：采购类（**`CostInsightIntentConvergence#isProcurementCostConvergenceRole`**）→ **`forDishProfitPurchaserDenied`**；**`WAREHOUSE_MANAGER` / `REGION_WAREHOUSE`** → **`forDishProfitWarehouseDenied`**；**`DELIVERY_SUPPLIER` / `DELIVERY_DRIVER` / `COUPON_OPERATOR`** → **`forDishProfitUnsupportedRoleDenied`** |
> **Historical removed（D-CLEAN-GROSS-MARGIN-P2B）**：`gross_margin_calculator` / **`GrossMarginCalculatorTool`** 已删除；毛利权限收敛到 **`CostDiagnosisAgent`**（`VIEW_COST`）+ **`CostMarginDerivation`** 内部推导，**无**独立 Tool 权限表项。

> **Historical removed（D-CLEAN-BOV-TOOL-DELETE）**：`business_overview_query` / **`BusinessOverviewQueryTool`** 已删除，**不再**有活跃 Tool 权限表项。现网 **`BUSINESS_OVERVIEW` / `business_overview_path`** 仅 **MULTI_AGENT 四域**：**`revenue_query` + `purchase_overview` + `stock_reduce_query` + `dish_profit_analysis`** → **`BusinessOverviewAnswerPlan.MULTI_AGENT_V1`** → **`StubAnswerComposerNode` 确定性 Composer**（前端以 **`answer_delta.data.text`** 为准）。classic 六工具链见 `docs/AI_MAINLINE_INDEX.md`。

> **Historical removed（D-CLEAN-STOCK-QUERY-P2）**：`stock_query` / **`StockQueryTool`** 已删除；库存/库房执行 Tool 权限仅 **`warehouse_stock_overview` → `VIEW_STOCK`**（上表）。语义 wire **`"STOCK_QUERY"`** 仍映射到 **`WAREHOUSE_STOCK_OVERVIEW`**，**不**恢复独立 Tool 权限项。

> **Historical removed（D-CLEAN-PURCHASE-QUERY-P2）**：`purchase_query` / **`PurchaseQueryTool`** 已删除；采购快照与成本链第 2 步统一 **`purchase_overview` → `VIEW_PURCHASE`**（上表）。

**`CostDiagnosisAgent`**：`VIEW_COST`。  
**`DishProfitAgent`**：无额外 Guard；其上游 **`dish_profit_analysis`** 已做鉴权。

实现：**`AiPermissionGuard.requiredPermissionForTool`** / **`evaluateCostDiagnosisAgent`** / **`evaluateDishProfitAnalysisInvocation`**。

---

## 7. 成本问句意图收敛（同一话术、按 `roleCode` 分支）

典型问法：**「本月成本怎么样？」**（及含 **成本 / 毛利 / 采购 / 核销 / 出库…** 的同类表达，仍以 **`BusinessDataPlannerNode.looksLikeCostInsight`** 命中为准）。

| 层级 | 行为 |
|------|------|
| **原始意图** | `COST_ANALYSIS`（成本/经营链路入口） |
| **收敛意图** | 视角色变为 **`FULL_COST_DIAGNOSIS`**（全链）｜ **`PURCHASE_COST_ANALYSIS`**（仅采购+库存核销视角）｜ **`COST_BLOCKED_MARKETING`**（拒答并提示权限） |

| `roleCode` / 条件 | 数据范围（与 §4 一致） | Tool / Agent | 说明 |
|-------------------|------------------------|--------------|------|
| `GROUP_MANAGER`（`GROUP_MANAGER_APP`） | `GROUP`，可查集团口径 | **4 Tool + `CostDiagnosisAgent`**（第 4 步 **`dish_profit_analysis`**；毛利 **不** 再编排 **`gross_margin_calculator`**） | 需 **`VIEW_COST`** 等与 §3 所列全链权限；执行 **`dish_profit_analysis`** 另需 **`VIEW_DISH_SALES`**（BTEN **`evaluateDishProfitAnalysisInvocation`**） |
| `STORE_MANAGER` 等门店锚点账号 | `STORE` / `DEPARTMENT`，仅本门店/挂靠部门 | 同上 **4 Tool + CostDiagnosis** | 权限含 **`VIEW_COST`、`VIEW_REVENUE`、`VIEW_PURCHASE`/`VIEW_STOCK`、`VIEW_DISH_SALES`**；问句中出现 **「集团」+成本类词** 时 **不扩展为集团查询**，仅在答复前追加 **【查询范围】** 说明：*你当前账号只能查看本门店数据。下面是本门店本月成本情况。* |
| **`STORE_PURCHASER`、`GROUP_PURCHASER`、`WAREHOUSE_PURCHASER`、`CENTRAL_KITCHEN_PURCHASER`、`REGION_PURCHASER`** | 各自的采购/库区锚点（§4） | **`purchase_overview` + `stock_reduce_query`（权限允许时）**；**不调** **`revenue_query`、不把 `CostDiagnosisAgent`** | 前置 **【意图说明】**：采购角色不可看完整经营成本/毛利，仅从采购视角分析 |
| `COUPON_OPERATOR`（`COUPON_APP`） | 营销挂靠部门 | **不拉取 Tool** | **`AiAnswerBoundary.forCouponOperatorCostInsight()`**（无成本权限提示，引导营销话术） |

**实现类**：**`CostInsightIntentConvergence`**（规则表）、**`BusinessDataPlannerNode`**（写 **`AiRunState`：`costInsightPath` / `purchaseCostInsightPath` / `couponCostInsightBlocked` / `costIntentConvergenceNote`**）、**`StubAnswerComposerNode`**（范围前缀 / 意图说明 / 权限提示 / 采购摘要）。

---

## 8. Run API 请求约定

- **`POST /api/ai/runs`**：**`userId`** 必须为 **`gb_department_user.gb_department_user_id`** 的有效主键；服务端 **`GbDepartmentUserService#getById`** 取行，读 **`gb_du_admin`**。  
- **`roleCode`**：**默认忽略**（以库表为准）。仅 **`FINANCE_MANAGER`**、**`MARKETING_MANAGER`** 为过渡期合成角色，跳过 DB。  
- **`departmentId` / `distributerId`**：请求锚点；与身份子树求交由 **`AiRunScopeIntersectService`**。

---

## 9. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-05-20 | **D-CLEAN-GROSS-MARGIN-P2B**：删除 **`GrossMarginCalculatorTool`** / **`gross_margin_calculator`**；毛利仅 **`CostDiagnosisAgentNode` + `CostMarginDerivation`**。 |
| 2026-05-20 | **D-CLEAN-GROSS-MARGIN-P2A**：**`DEFAULT_COST_INSIGHT_TOOLS`** 移除 **`gross_margin_calculator`**（四步链）；毛利由 **`CostDiagnosisAgentNode` + `CostMarginDerivation`** 内部推导。 |
| 2026-05-20 | **D-CLEAN-COST-P1**：**`DEFAULT_COST_INSIGHT_TOOLS`** 第 4 步 **`dish_sales_query` → `dish_profit_analysis`**。 |
| 2026-05-20 | **D-CLEAN-DISH-SALES-P2**：**`DishSalesQueryTool`** / Tool id **`dish_sales_query`** 已删除；**`AiResolvedQueryIntent.DISH_SALES_QUERY`** / **`PATH_DISH_SALES_QUERY`** 保留；D-8 与成本链均执行 **`dish_profit_analysis`**。 |
| 2026-05-10 | **`dish_profit_analysis`**：双权限 **`VIEW_DISH_SALES`+`VIEW_COST`** + 采购/库房/配送/优惠券角色拒答话术；Planner **`dish_profit_path`** 先于泛泛「毛利」成本意图。**`AnswerComposer`** / **`answer_delta.data.dishProfitOverview`**。关联文档：**`API_INTEGRATION`、`AI_MAINLINE_INDEX`、`TODO`**。 |
| 2026-05-10 | 首版：文档化 `admin` → `roleCode` → `AiPermissions` → `AiOrgScope`；与 `AiRoleMapper` / `AiUserContextResolver` 对齐。 |
| 2026-05-10 | 「本月成本怎么样」：**成本意图收敛**（集团门店/采购/优惠券）；**`STORE_MANAGER`** 增补 **`VIEW_PURCHASE`**（与 Tool 链一致）。 |
