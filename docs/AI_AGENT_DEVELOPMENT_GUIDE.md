# AI 多智能体开发总控文档

## 1. 文档目的

本项目正在从原来的单体式 AI 编排器，逐步改造为多智能体系统。

以前核心逻辑集中在 `GbAiChatServiceImpl` 里，包括：

```text
1. 范围解析
2. Skill 选择
3. 查库
4. 构建 System Prompt
5. 调 DeepSeek 主回答
6. 后处理
```

现在要改成多智能体架构，包括：

```text
经营概览 Agent
成本分析 Agent
采购 Agent
库存 Agent
菜品毛利 Agent
报表 Agent
营销优惠券 Agent
审核 / Review Agent
文档导出 Agent
```

但是所有 Agent 必须遵守统一规则，不能各自重复解析用户、部门、门店、集团、时间和权限。

---

# 2. 当前最重要的问题

当前已经多次出现同类错误：

```text
1. 集团用户被当成门店用户
2. 集团用户的 departmentId 被错误当成门店 ID
3. 库存 Agent、菜品毛利 Agent、经营概览 Agent 各自写范围逻辑
4. “这个月呢？”无法继承上一轮意图
5. admin=1 采购员问经营时，应该收敛成采购视角
6. admin=3 库管员问经营时，应该收敛成库存视角
7. 集团查询时没有显示覆盖的门店
8. 菜品毛利查询时只查到一个门店
9. 菜品名称没有正确关联，出现“菜品一、菜品二”
10. 库存查询没有按集团范围汇总
```

所以必须先建立公共查询上下文，再让各业务 Agent 使用。

---

# 3. 核心领域模型

必须先阅读：

```text
docs/DOMAIN_ORG_MODEL.md
```

本项目的组织关系不是普通的部门树，必须按以下规则理解。

## 3.1 distributerId

```text
distributerId = 集团 / 配送商 / 组织主体 ID
```

集团用户查询经营、库存、菜品毛利、采购、报表、营销时，不能把 `departmentId` 当集团 ID。

集团范围应该优先根据：

```text
distributerId
```

去查询集团下属门店。

## 3.2 gb_department 表

```text
gb_department.gbDepartmentFatherId = 0
```

表示这条 department 是门店。

```text
gb_department.gbDepartmentFatherId = 某个门店 departmentId
```

表示这条 department 是该门店下面的子部门。

结构为：

```text
distributerId
  └── 门店 Department（gbDepartmentFatherId = 0）
        └── 子部门 Department（gbDepartmentFatherId = 门店 departmentId）
```

## 3.3 集团用户的 departmentId

集团用户的 `departmentId` 通常是管理部门，不是门店 ID。

所以：

```text
GROUP_MANAGER_APP 查询任何集团范围数据时
不能直接使用当前 departmentId 当门店
必须根据 distributerId 查询所有 gbDepartmentFatherId = 0 的门店
```

## 3.4 门店用户

门店用户如果当前 `departmentId` 是门店：

```text
storeDepartmentId = departmentId
```

如果当前 `departmentId` 是子部门：

```text
storeDepartmentId = gbDepartmentFatherId
```

---

# 4. 必须抽取公共查询上下文

不要再让每个 Agent 自己解析范围。

错误做法：

```text
BusinessOverviewTool 自己查集团门店
DishProfitTool 自己查集团门店
WarehouseStockTool 自己查集团门店
PurchaseTool 自己查当前门店
每个 Tool 自己解析“这个月”
每个 Tool 自己处理“这个月呢”
```

正确做法：

```text
用户输入
  ↓
AiUserContextResolver
  ↓
AiResolvedQueryContextResolver
  ↓
AiResolvedQueryContext
  ↓
各业务 Agent / Tool
```

`AiResolvedQueryContext` 应在 Run 生命周期早期生成，并挂载到 `AiRunState#resolvedQueryContext`（当前由 `AiRunService#startRun` 在进 Graph 前写入）；业务 Agent / Tool 后续只读该对象，与现有 `aiUserContext` / `aiOrgScope` 并存并逐步收敛。

---

# 5. AiResolvedQueryContext 设计

正式代码位置：

`src/main/java/com/nongxinle/ai/context/AiResolvedQueryContext.java`

统一解析入口（第一版）：

`src/main/java/com/nongxinle/ai/resolver/AiResolvedQueryContextResolver.java`

职责：

统一承载一次 AI Run 的查询上下文，包括用户身份、组织范围、时间范围、查询意图、数据对象范围与多轮追问占位信息。

核心字段：

- runId、userId
- userContext
- orgScope、timeWindow、queryIntent、dataScope
- followUp、originalQuestion、normalizedQuestion
- queryScopeBanner、timeWindowLabel、answerBoundaryNote

它解决四个核心问题：

```text
谁问的？
能查哪里？
查什么？
查多久？
```

---

# 6. AiUserContext：谁在问

正式代码位置：

`src/main/java/com/nongxinle/ai/context/AiUserContext.java`

装配入口：`src/main/java/com/nongxinle/ai/context/AiUserContextResolver.java`

说明：设计稿中的字段是子集；以 Java 类为准（含 `departmentFatherId`、`groupId`、`regionId`、`storeId`、`allowedStoreIds` 等）。

核心字段（与解析强相关）：

- userId、sourceAdminRole、roleCode、roleName
- distributerId、departmentId、departmentFatherId
- permissions、allowedStoreIds

admin 角色规则：

```text
0  = GROUP_MANAGER_APP       集团管理端
1  = STORE_PURCHASER_APP     门店采购端
2  = GROUP_PURCHASER_APP     集采 / 采购端
3  = WAREHOUSE_APP           库房端
4  = CENTRAL_KITCHEN_APP     中央厨房端
5  = DELIVERY_SUPPLIER_APP   配送商端
6  = DELIVERY_DRIVER_APP     配送员端
7  = COUPON_APP              优惠券端
11 = STORE_MANAGER_APP       门店管理端
12 = STORE_ORDER_APP         门店订货端
13 = WINDOW_ORDER_APP        窗口订货端
51 = REGION_MANAGER_APP      区域管理端
52 = REGION_PURCHASER_APP    区域采购端
53 = REGION_WAREHOUSE_APP    区域库房端
```

---

# 7. AiResolvedOrgScope：能查哪里

正式代码位置：

`src/main/java/com/nongxinle/ai/context/AiResolvedOrgScope.java`

门店 / 部门轻量 DTO：

- `src/main/java/com/nongxinle/ai/context/AiStoreScopeDTO.java`
- `src/main/java/com/nongxinle/ai/context/AiDepartmentScopeDTO.java`

职责：

表达权限内应可见的组织边界（`visibleStores` 等为「应可见」，非「当日有数据」）。

核心字段：

- scopeType（常量见类上 `SCOPE_*`）
- distributerId、requestDepartmentId
- currentStoreDepartmentId、currentDepartmentId
- visibleStores、visibleWarehouses、visibleDepartments
- scopeName、queryScopeBanner、coverageDetail

关键规则：

```text
visibleStores = 当前用户权限范围内应该可见的门店
不是有数据的门店
```

业务 Tool 再根据数据情况拆分：

```text
coveredStores = 有数据、参与统计的门店
dataMissingStores = visibleStores 中无数据的门店
attentionStores = 异常门店
```

---

# 8. AiResolvedTimeWindow：查多久

正式代码位置：

`src/main/java/com/nongxinle/ai/context/AiResolvedTimeWindow.java`

职责：

从用户口语解析统计起止日（第一版规则，不含 LLM）；`timeLabel` 常量见类中 `TODAY` / `THIS_MONTH` / `ROLLING_7` 等。

核心字段：

- timeLabel、startDate、endDate、displayText、inheritedFromPreviousTurn

时间规则：

```text
本月 = 当月 1 号到今天
上个月 = 上月 1 号到上月最后一天
今天 = 今日
昨天 = 昨日
最近 7 天 = 今天往前 7 天
```

---

# 9. AiResolvedQueryIntent：查什么

正式代码位置：

`src/main/java/com/nongxinle/ai/context/AiResolvedQueryIntent.java`

职责：

关键词规则解析意图与 pathCode（常量 `INTENT_*`、`PATH_*`）；多轮继承后续由 FollowUp 接入。

核心字段：

- intentCode、pathCode、topic
- inheritedFromPreviousTurn、inheritedFromIntentCode

例子：

```text
“这个月经营怎么样” → BUSINESS_OVERVIEW
“现在库存怎么样” → WAREHOUSE_STOCK_OVERVIEW
“上个月菜品利润怎么样” → DISH_PROFIT
“这个月呢？” → 继承上一轮 intent，只改时间
```

---

# 10. AiResolvedDataScope：查哪个对象

正式代码位置：

`src/main/java/com/nongxinle/ai/context/AiResolvedDataScope.java`

职责：

由 `AiResolvedOrgScope` 推导当前 Run 的目标门店/库房等（第一版）；菜品名、商品名等后续由 NER/词槽补全。

核心字段：

- targetStoreIds、targetWarehouseIds、targetDepartmentIds
- targetDishIds、targetDishNames、targetGoodsIds、targetGoodsNames、targetSupplierIds
- allVisibleStores、allVisibleWarehouses

例子：

```text
集团用户问“现在库存怎么样”
→ allVisibleStores = true
→ targetStoreIds = orgScope.visibleStores

用户问“水煮鱼毛利怎么样”
→ targetDishNames = 水煮鱼
```

---

# 11. FollowUpResolver：多轮追问

必须支持：

```text
上个月菜品利润怎么样？
这个月呢？
```

第二句应该被理解为：

```text
这个月菜品利润怎么样？
```

而不是重新反问用户“你想看什么指标”。

需要保存上一轮：

```java
public class AiConversationTurnMemory {
    private String lastIntentCode;
    private String lastPathCode;
    private String lastTopic;
    private AiResolvedOrgScope lastOrgScope;
    private AiResolvedTimeWindow lastTimeWindow;
    private AiResolvedDataScope lastDataScope;
}
```

追问规则：

```text
这个月呢？
本月呢？
那上个月呢？
换成本月
那个菜呢？
这个店呢？
```

如果没有新主题，就继承上一轮：

```text
intent 继承
scope 继承
dataScope 继承
timeWindow 替换
```

---

# 12. 各业务 Agent 的职责

## 12.1 经营概览 business_overview_path

只负责：

```text
读取 context.orgScope.visibleStores
读取 context.timeWindow
查询日营收 / 经营看板
生成经营分析
```

不能自己解析集团门店。

集团管理端回答必须包含：

```text
集团范围
可见门店数量
有数据门店
缺数据门店
参与统计门店
营业额
日均营业额
订单数
客单价
优惠券/平台费
退款
外卖营业额
```

## 12.2 采购 purchase_overview_path

admin=1 门店采购员问：

```text
这个月经营怎么样？
```

应该收敛成采购视角：

```text
你当前账号是门店采购角色，不能查看完整经营数据。下面按你的权限，为你分析本月采购情况。
```

只返回：

```text
采购金额
采购笔数
采购重量
采购品类
入库情况
采购异常
```

不能返回：

```text
营业额
订单数
客单价
利润
毛利
完整经营情况
```

## 12.3 库存 warehouse_stock_overview_path

admin=3 库管员问：

```text
这个月经营怎么样？
现在库存怎么样？
```

应该收敛成库存视角。

库管员只看本人所在库房 / 所属部门库存。

集团用户问：

```text
现在库存怎么样？
我现在库存有多少？
```

应该按集团下属门店 / 库房库存汇总，不要反问“哪家门店”。

库存回答必须包含：

```text
库存商品数
库存总金额
库存总重量
入库
出库 / 核销
损耗
报损
退货
低库存商品
积压商品
长期未动销商品
需要盘点的商品
```

如果没有库存数据，也不能报“AI 服务异常”，要返回空结构和可读说明。

## 12.4 菜品毛利 dish_profit_path

用户问：

```text
上个月菜品利润怎么样？
菜品毛利怎么样？
哪些菜赚钱？
哪些菜不赚钱？
水煮鱼毛利怎么样？
```

应该走 `dish_profit_path`。

集团用户必须按 `distributerId` 查询集团下所有 `gbDepartmentFatherId = 0` 的门店，再汇总菜品销售和菜品成本。

不能只查集团用户当前 `departmentId`。

菜品名称必须正确关联，不能显示：

```text
菜品一
菜品二
菜品三
```

必须显示真实菜名，例如：

```text
烩菜
香煎青鱼
干锅娃娃菜
椒麻鸡
酸奶碗
```

菜品毛利回答应包含：

```text
菜品数量
菜品销售额
理论成本
实际成本
毛利额
毛利率
毛利较好菜品
低毛利菜品
异常菜品
建议
```

---

# 13. 权限收敛规则

用户问了超出权限的问题，不要直接 500，也不要空泛拒绝，要收敛到他的权限范围。

## 13.1 admin=1 门店采购员

问经营：

```text
这个月经营怎么样？
```

收敛成采购：

```text
你当前账号只能查看采购相关数据，下面按采购视角为你分析。
```

## 13.2 admin=3 库管员

问经营：

```text
这个月经营怎么样？
```

收敛成库存：

```text
你当前账号只能查看所在库房库存相关数据，下面按库房库存视角为你分析。
```

## 13.3 集团管理端

可以看集团范围经营、库存、菜品毛利等，但必须按 distributerId 下属门店汇总。

## 13.4 门店店长

只能看本门店，不看其他门店。

---

# 14. 门店列表规则

集团查询时必须区分：

```text
visibleStores = 权限范围内所有门店
coveredStores = 有数据、参与本次统计的门店
dataMissingStores = 应该有但缺数据的门店
attentionStores = 经营异常门店
```

不能把：

```text
有日营收的门店数量
```

当成：

```text
集团门店总数
```

如果集团下有 2 家门店，其中 1 家有数据，1 家没数据，应该回答：

```text
集团范围内共识别到 2 家门店，其中 1 家有数据，1 家暂无数据。
```

不能回答：

```text
本期识别到 1 家门店，暂无缺失门店。
```

---

# 15. 回答边界规则

不要暴露内部字段：

```text
toolResults
dataPlanTools
workspaceMode
resolvedDepartmentIds
rollupMeta
fallbackSingleAnchorOnly
rawStats
```

不要出现技术口径：

```text
父级网点
节点
主体
登记口径
```

应该转成老板能听懂的话：

```text
集团范围
本门店
本次参与统计的门店
暂无日营收记录的门店
需要优先关注的门店
```

---

# 16. 数字格式规则

金额不能出现科学计数法：

错误：

```text
3E+1 元
2E+1 元
```

正确：

```text
30 元
20 元
854 元
85.4 元
```

Java 中 BigDecimal 输出金额时，优先使用 `BigDecimal#toPlainString()`，或项目内统一金额格式化方法。

---

# 17. Cursor Agent 开发规则

## 17.1 每个 Agent 开始前必须读

```text
docs/AI_AGENT_DEVELOPMENT_GUIDE.md
docs/DOMAIN_ORG_MODEL.md
docs/TODO_MULTI_AGENT.md
docs/API_INTEGRATION.md
docs/PERMISSION_MODEL.md
docs/LEGACY_AI_ANSWER_ASSETS.md
```

## 17.2 不要同时改多个业务链路

一个 Agent 只负责一条线：

```text
经营概览
采购概览
库存概览
菜品毛利
报表导出
营销优惠券
```

不要一个 Agent 同时改经营、库存、菜品毛利。

## 17.3 不要重复改公共范围逻辑

涉及用户、部门、门店、集团、时间、追问，优先改公共：

```text
AiResolvedQueryContext
AiResolvedOrgScope
AiResolvedTimeWindow
AiResolvedQueryIntent
AiResolvedDataScope
FollowUpResolver
```

不要在各 Tool 内私自解析。

## 17.4 阶段收口须同步 API 文档

某条业务链路在 **`docs/TODO_MULTI_AGENT.md`** 勾选 **阶段收口**（或产品认可「该链路可交付」）时，**同一变更集或紧随其后的提交** 须更新 **`docs/API_INTEGRATION.md`**：`answer_delta.data.*` 稳定契约、相关端点与验收说明；字段名以 Java DTO 与 Fastjson 序列化为准。

---

# 18. 关于测试

从现在开始，Cursor Agent 每次改完代码后，不需要强制跑单测。

也就是说，不要每次都要求：

```bash
mvn test
```

原因：

```text
当前开发阶段主要由我做真机业务测试。
单测容易因为本地 JDK、环境、数据库连接等问题打断开发节奏。
```

Cursor 每次完成后只需要说明：

```text
1. 改了哪些文件
2. 改了哪些逻辑
3. 影响哪些链路
4. 我应该用哪个用户 / admin 测试
5. 我应该问什么问题
6. 预期应该返回什么
```

我来做真机测试。

如果 Cursor 自己愿意跑编译或单测，可以跑，但不要把“单测未跑 / 单测失败”作为阻塞项，除非是明显代码语法错误。

---

# 19. 每次交付格式

每次 Cursor Agent 完成后，请按以下格式回复：

```text
本轮完成内容：
1. xxx
2. xxx

涉及文件：
- xxx.java
- xxx.md

影响链路：
- business_overview_path
- dish_profit_path
- warehouse_stock_overview_path

没有改动：
- SSE 发送层
- 权限大框架
- 前端页面

需要我测试：
用户角色：
admin = x

测试问题：
xxx

预期结果：
xxx
```

---

# 20. 当前优先级

当前不要继续在各个 Agent 里零散修范围。

优先级如下：

```text
1. 建立公共查询上下文 AiResolvedQueryContext
2. 统一组织范围解析
3. 统一时间范围解析
4. 支持“这个月呢 / 上个月呢”的多轮追问
5. 经营概览迁移到公共 context
6. 菜品毛利迁移到公共 context
7. 库存概览迁移到公共 context
8. 采购概览迁移到公共 context
9. 再继续做 ReportGraph / MarketingGraph
```

---

# 21. 重要强制规则

以后所有 Agent 必须记住：

```text
1. distributerId 是集团 / 配送商主体 ID
2. gbDepartmentFatherId = 0 的 department 才是门店
3. 子部门必须归一化到所属门店
4. 集团用户的 departmentId 通常是管理部门，不是门店
5. 集团查询必须按 distributerId 找所有 fatherId=0 门店
6. 业务 Tool 不允许自己解析组织范围
7. 时间范围必须统一解析
8. “这个月呢”必须继承上一轮意图
9. 采购员问经营，要收敛成采购
10. 库管员问经营，要收敛成库存
11. 集团用户问库存，不要反问哪家门店
12. 菜品毛利必须显示真实菜名
13. 回答不能暴露内部字段
14. 改完不强制跑单测，由我真机测试
```

以上规则优先级高于单个 Agent 自己的临时判断。
