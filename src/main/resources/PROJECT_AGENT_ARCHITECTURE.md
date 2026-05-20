下面这份可以直接复制给 Cursor，作为项目初始化/重构总文档使用。

# 餐饮集团 AI 多智能体平台项目开发说明

## 0. 项目背景

当前项目原来是一个测试版 AI 聊天系统，核心逻辑集中在 `GbAiChatServiceImpl` 中，里面包含：

- 用户输入处理
- 用户/门店/部门范围解析
- Skill 选择
- DeepSeek 调用
- 数据查询
- Prompt 拼接
- 最终回答生成
- skill_handoff 后处理
- 会话记忆提取
- SSE 流式输出

现在不要求兼容旧版架构。旧代码仅作为业务逻辑和数据访问参考。

本次目标是重新设计并实现一个面向餐饮集团、连锁门店、生鲜配送、餐饮办公场景的 **AI 多智能体平台**。

项目不再是简单聊天机器人，而是：

> 餐饮集团 AI 经营办公平台

它需要支持：

1. 经营分析
2. 报表生成
3. 营销增长
4. 办公助手
5. 企业知识库问答
6. 任务督办
7. AI 运行审核
8. 夜间复盘学习
9. 文件导出下载

---

# 1. 总体产品定位

本系统面向以下用户：

- 集团老板 / 董事长
- 总经理 / 运营总监
- 区域经理
- 门店店长
- 采购经理
- 财务人员
- 厨师长 / 后厨主管
- 市场营销人员
- 行政办公人员
- 加盟店管理人员

不同角色看到的数据范围不同，AI 回答内容也不同。

例如同一句话：

> 帮我看一下这个月成本怎么样？

不同角色的含义不同：

- 老板：看集团整体成本
- 区域经理：看负责区域成本
- 店长：看本店成本
- 采购经理：看采购成本和供应商价格
- 财务：看成本、费用、利润、应付
- 厨师长：看菜品配料、出库、损耗、出成率

因此系统必须从一开始支持：

- 角色识别
- 组织范围解析
- 权限控制
- 数据字段权限
- Agent 工作空间路由

---

# 2. 核心架构思想

不要把系统做成一个大 Service。

应该采用：

```text
Workspace Router
    ↓
Agent Graph
    ↓
Supervisor Agent
    ↓
Specialist Agents
    ↓
Business Tools
    ↓
Structured Output
    ↓
Outcome Review
    ↓
Export / Task / Memory
```

### 核心技术关键词

- Agent Graph
- Supervisor Agent
- Workspace Router
- Structured Output
- Tool Registry
- Permission Guard
- SSE Process Events
- Trace Log
- Outcome Review
- Dreaming Memory
- Export Center
- Async Job

## 3. 顶层工作空间设计

系统分为 6 个主要 AI 工作空间：

AI 工作台
├── 1. AI 经营顾问 Business
├── 2. AI 报表中心 Report
├── 3. AI 营销增长 Marketing
├── 4. AI 办公助手 Office
├── 5. AI 制度知识库 Knowledge
└── 6. AI 任务督办 Task

新增枚举：

public enum AiWorkspaceMode {
    BUSINESS_CHAT,       // 经营分析
    REPORT_GENERATION,   // 报表生成
    MARKETING_GROWTH,    // 营销增长、优惠券、套餐、活动
    OFFICE_ASSISTANT,    // 办公助手
    KNOWLEDGE_QA,        // 制度知识库
    TASK_MANAGEMENT,     // 任务督办
    AUTO_SCAN            // 自动日报、夜间扫描
}

## 4. 总流程

用户输入可以来自：

聊天框
语音输入
文件上传
报表按钮
小程序页面快捷入口
定时任务
系统自动扫描

统一进入：

用户输入
  ↓
AiGatewayController
  ↓
AiRunService
  ↓
WorkspaceRouterAgent
  ↓
对应 Workspace Graph
  ↓
Agent 执行
  ↓
OutcomeReviewAgent 审核
  ↓
返回结果 / 生成文件 / 创建任务 / 写入记忆
## 5. 核心状态对象 AiRunState

不要到处传字符串、Map、Prompt。

所有 Agent / Node 都通过 AiRunState 读写状态。

@Data
@Builder
public class AiRunState {

    private Long runId;
    private Long conversationId;
    private Long userId;

    private String rawUserInput;
    private String normalizedUserInput;

    private AiWorkspaceMode workspaceMode;

    private String userRole;
    private String orgLevel;

    private AiQueryScope scope;
    private AiIntent intent;
    private AiTimeWindow timeWindow;

    private List<String> selectedAgents;
    private List<AiDataRequest> dataRequests;

    private Map<String, Object> toolResults;

    private List<AgentStep> steps;
    private List<AgentObservation> observations;

    private List<SpecialistReport> specialistReports;
    private AiReport aiReport;
    private AiDocument aiDocument;
    private AiCampaignPlan campaignPlan;

    private FinalAnswer finalAnswer;

    private boolean needClarification;
    private String clarificationQuestion;

    private boolean needHumanConfirm;
    private String humanConfirmReason;

    private List<AiAttachment> attachments;
}

## 6. Agent Graph 基础接口

第一版自己实现轻量 Agent Graph，不要一开始引入复杂外部框架。

public interface AgentNode {

    String name();

    boolean shouldRun(AiRunState state);

    AiRunState run(AiRunState state);
}

图执行器：

@Component
public class AiGraphRunner {

    private final List<AgentNode> nodes;

    public AiRunState run(AiRunState state) {
        for (AgentNode node : nodes) {
            if (!node.shouldRun(state)) {
                continue;
            }

            // 记录 step started
            // SSE 推送 agent_started

            try {
                state = node.run(state);

                // 记录 step finished
                // SSE 推送 agent_finished
            } catch (Exception e) {
                // 记录 step failed
                // SSE 推送 error
                throw e;
            }
        }
        return state;
    }
}

## 7. 推荐项目包结构
com.nongxinle.ai
├── core
│   ├── AiRunState.java
│   ├── AiRunContext.java
│   ├── AiRunResult.java
│   ├── AiWorkspaceMode.java
│   ├── AiGraphRunner.java
│   ├── AgentNode.java
│   └── AgentObservation.java
│
├── gateway
│   ├── LlmGateway.java
│   ├── DeepSeekLlmGateway.java
│   ├── QwenLlmGateway.java
│   ├── OpenAiLlmGateway.java
│   ├── LlmRequest.java
│   ├── LlmResponse.java
│   └── LlmTraceService.java
│
├── security
│   ├── AiPermissionGuard.java
│   ├── AiOrgScopeResolver.java
│   ├── AiFieldPermissionService.java
│   └── AiQueryScope.java
│
├── workspace
│   ├── WorkspaceRouterAgent.java
│   ├── business
│   ├── report
│   ├── marketing
│   ├── office
│   ├── knowledge
│   └── task
│
├── tool
│   ├── ToolRegistry.java
│   ├── AiTool.java
│   ├── ToolRequest.java
│   ├── ToolResult.java
│   ├── RevenueQueryTool.java
│   ├── PurchaseOverviewTool.java
│   ├── WarehouseStockOverviewTool.java
│   ├── StockReduceQueryTool.java
│   ├── DishProfitAnalysisTool.java
│   ├── DishIngredientCostBreakdownTool.java
│   ├── ~~PurchaseQueryTool.java~~（**Historical removed**：D-CLEAN-PURCHASE-QUERY-P2）
│   ├── ~~StockQueryTool.java~~（**Historical removed**：D-CLEAN-STOCK-QUERY-P2；库存现量见 **`warehouse_stock_overview`**）
│   ├── ~~DishSalesQueryTool.java~~（**Historical removed**：D-CLEAN-DISH-SALES-P2；D-8 **`DISH_SALES_QUERY` / `dish_sales_query_path`** 执行 **`DishProfitAnalysisTool`**）
│   ├── ~~GrossMarginCalculatorTool.java~~（**Historical removed**：D-CLEAN-GROSS-MARGIN-P2B；毛利见 **`CostMarginDerivation`**）
│   ├── ~~BusinessOverviewQueryTool.java~~（**Historical removed**：D-CLEAN-BOV-TOOL-DELETE；经营概览收入见 **`revenue_query`** MULTI 四域）
│   ├── ~~DishRecipeQueryTool.java~~（**Draft removed**：未实现，勿当作现网 Tool）
│   ├── CouponCreateTool.java
│   ├── ComboCreateTool.java
│   └── KnowledgeSearchTool.java
│
├── orchestration
│   ├── MultiAgentOrchestrator.java
│   ├── LeadAgent.java
│   ├── SubAgentTask.java
│   └── AgentTaskScheduler.java
│
├── outcome
│   ├── OutcomeReviewAgent.java
│   ├── OutcomeRubric.java
│   ├── OutcomeReviewResult.java
│   ├── OutcomeRubricLoader.java
│   └── RevisionLoopService.java
│
├── memory
│   ├── DreamingMemoryJob.java
│   ├── MemoryCandidate.java
│   ├── MemoryCuratorAgent.java
│   ├── MemoryApprovalService.java
│   └── AgentLearningService.java
│
├── export
│   ├── AiExportService.java
│   ├── DefaultAiExportService.java
│   ├── ExportRequest.java
│   ├── ExportResult.java
│   ├── ExportFormat.java
│   ├── ExportAgent.java
│   ├── report
│   ├── document
│   ├── template
│   ├── storage
│   └── async
│
├── async
│   ├── AiAsyncJob.java
│   ├── AiAsyncJobService.java
│   ├── AiJobQueue.java
│   ├── AiWebhookController.java
│   └── AiNotificationService.java
│
└── trace
    ├── AiRunTraceService.java
    ├── AiStepLogger.java
    ├── AiSseEventPublisher.java
    └── AiTraceEntity.java
## 8. LLM 调用层设计

所有模型调用必须统一通过 LlmGateway。

不要让每个 Agent 自己写 OkHttp。

public interface LlmGateway {

    LlmResponse chat(LlmRequest request);

    Flux<LlmDelta> stream(LlmRequest request);

    <T> T structuredChat(
        LlmRequest request,
        Class<T> outputClass
    );
}

每次 LLM 调用都必须记录：

runId
agentName
modelProvider
modelName
prompt
rawRequest
rawResponse
parsedJson
durationMs
tokenUsage
errorMessage

支持模型：

DeepSeek
Qwen
OpenAI
Claude
本地模型

当前优先实现 DeepSeek，后续可扩展。

## 9. Prompt 文件管理

不要继续把 Prompt 大量拼在 Java 字符串里。

Prompt 应该放在：

resources/ai/agents/
├── workspace-router-agent.md
├── answer-composer-agent.md
│
├── business/
│   ├── business-supervisor-agent.md
│   ├── cost-diagnosis-agent.md
│   ├── dish-profit-agent.md
│   ├── procurement-agent.md
│   ├── revenue-boost-agent.md
│   └── store-compare-agent.md
│
├── report/
│   ├── report-supervisor-agent.md
│   ├── monthly-report-agent.md
│   ├── daily-report-agent.md
│   └── purchase-report-agent.md
│
├── marketing/
│   ├── marketing-supervisor-agent.md
│   ├── coupon-strategy-agent.md
│   ├── combo-package-agent.md
│   ├── campaign-planner-agent.md
│   ├── wechat-article-writer-agent.md
│   └── marketing-risk-agent.md
│
├── office/
│   ├── notice-writer-agent.md
│   ├── meeting-minutes-agent.md
│   └── inspection-report-agent.md
│
├── knowledge/
│   ├── policy-qa-agent.md
│   └── citation-answer-agent.md
│
└── task/
    ├── task-extractor-agent.md
    └── task-follow-up-agent.md

每个 Agent Prompt 必须包含：

# 角色
# 任务范围
# 不能做什么
# 输入字段
# 可用工具
# 分析规则
# 输出 JSON Schema
# 示例
## 10. Workspace Router Agent

第一步判断用户意图属于哪个工作空间。

输入示例：

帮我生成本月采购分析报表

输出：

{
  "workspaceMode": "REPORT_GENERATION",
  "confidence": 0.94,
  "reason": "用户明确要求生成采购分析报表"
}

示例路由规则：

经营分析：
- 成本怎么样
- 营业额下降原因
- 哪道菜不赚钱
- 哪个门店异常
- 采购有没有问题

报表中心：
- 生成日报
- 生成月报
- 导出 Excel
- 下载 PDF
- 门店对比报表

营销增长：
- 生成优惠券
- 设计套餐
- 做活动
- 写公众号
- 促销方案
- 活动复盘

办公助手：
- 写通知
- 会议纪要
- 巡店报告
- 工作总结
- 培训材料

知识库：
- 查制度
- SOP 怎么规定
- 报损流程
- 加盟店制度

任务督办：
- 创建整改任务
- 跟进任务
- 提醒负责人
- 上次整改完成了吗
## 11. BusinessGraph 经营分析
### 11.1 经营分析流程
BusinessGraph
├── ScopeNode
├── IntentRouterNode
├── TimeWindowNode
├── DataPlannerNode
├── ToolExecutionNode
├── MultiAgentOrchestrator
├── AnswerComposerNode
├── OutcomeReviewNode
└── MemoryExtractorNode
### 11.2 经营分析 Agent
BusinessSupervisorAgent        经营总控
CostDiagnosisAgent             成本诊断
RevenueBoostAgent              营业额提升
DishProfitAgent                菜品毛利
ProcurementAgent               采购结构
InventoryLossAgent             库存损耗
SupplierAgent                  供应商分析
StoreCompareAgent              门店对比
RegionOperationAgent           区域经营
ExceptionWarningAgent          异常预警
### 11.3 示例：菜品毛利 Agent 输出
{
  "agentName": "DishProfitAgent",
  "dishName": "水煮鱼",
  "mainProblem": "草鱼实际用量高于理论用量",
  "riskLevel": "warning",
  "metrics": {
    "salesQty": 120,
    "theoreticalCost": 1860,
    "actualCost": 2230,
    "grossMarginRate": 0.42
  },
  "findings": [
    "草鱼成本占比最高",
    "辣椒用量正常",
    "豆芽成本占比低，不是主要问题"
  ],
  "suggestions": [
    "先检查草鱼称重和出成率",
    "把每份草鱼标准量固定到后厨操作卡"
  ]
}

## 12. ReportGraph 报表中心
### 12.1 报表类型
日报
周报
月报
经营分析报表
采购分析报表
供应商报表
库存损耗报表
菜品销售报表
菜品毛利报表
门店对比报表
区域经营报表
活动复盘报表
财务利润报表
### 12.2 报表 Agent
ReportSupervisorAgent
DailyReportAgent
WeeklyReportAgent
MonthlyReportAgent
ProfitReportAgent
PurchaseReportAgent
SupplierReportAgent
InventoryReportAgent
DishSalesReportAgent
StoreCompareReportAgent
ExportAgent
### 12.3 报表流程
用户要求生成报表
  ↓
识别报表类型
  ↓
确认组织范围
  ↓
确认时间范围
  ↓
确认权限
  ↓
查询真实数据
  ↓
生成结构化 AiReport
  ↓
AI 生成总结和建议
  ↓
OutcomeReviewAgent 审核
  ↓
在线预览 / 导出 PDF / 导出 Excel / 导出 Word
### 12.4 AiReport 结构
@Data
public class AiReport {
    private String reportType;
    private String title;
    private AiTimeWindow timeWindow;
    private AiQueryScope orgScope;

    private List<ReportMetricCard> metricCards;
    private List<ReportSection> sections;
    private List<ReportTable> tables;
    private List<ReportChart> charts;

    private String aiSummary;
    private List<String> risks;
    private List<String> suggestions;
}

## 13. MarketingGraph 营销增长

这是本项目的重要模块。

它不是普通文案生成，而是根据真实经营数据：

菜品销量
菜品毛利
库存压力
采购成本
客单价
订单数
老客复购
历史活动效果

自动设计：

套餐
优惠券
促销活动
公众号文案
朋友圈文案
小程序活动页
活动复盘
### 13.1 MarketingGraph 流程
MarketingGraph
├── MarketingIntentRouterNode
├── MarketingScopeNode
├── MarketingDataPlannerNode
├── MarketingDataQueryNode
├── CustomerSegmentNode
├── CouponStrategyAgent
├── ComboPackageAgent
├── MarketingRiskAgent
├── CampaignPlannerAgent
├── CopywritingAgent
├── OutcomeReviewAgent
└── CreateDraftNode
### 13.2 营销 Agent
MarketingSupervisorAgent        营销总控
CouponStrategyAgent             优惠券策略 Agent
ComboPackageAgent               套餐组合 Agent
DishPromotionAgent              菜品促销 Agent
CustomerSegmentAgent            客群分层 Agent
CampaignPlannerAgent            活动策划 Agent
WechatArticleWriterAgent        公众号文案 Agent
MiniProgramPageCopyAgent        小程序活动页文案 Agent
MomentsCopyAgent                朋友圈文案 Agent
MarketingRiskAgent              毛利风险校验 Agent
CampaignReviewAgent             活动复盘 Agent
### 13.3 套餐组合逻辑

套餐不能随机组合。

组合原则：

套餐 = 引流菜 + 利润菜 + 搭配菜 + 可选加购

考虑因素：

高销量菜：负责吸引用户
高毛利菜：负责赚钱
库存压力菜：负责带动消耗
互补菜品：主菜 + 小菜 + 饮品 + 主食
价格带：39 / 59 / 88 / 99 / 128
适用场景：单人餐 / 双人餐 / 家庭餐 / 午市套餐 / 晚市套餐
### 13.4 AiComboPlan
@Data
public class AiComboPlan {
    private String packageName;
    private String targetScene;
    private BigDecimal originalPrice;
    private BigDecimal packagePrice;
    private BigDecimal estimatedGrossMarginRate;

    private List<AiComboItem> items;

    private String campaignGoal;
    private String riskNote;
    private String suggestion;
}

### 13.5 优惠券类型

必须支持：

满减券
折扣券
单品券
套餐券
第二份半价券
满赠券
储值赠券
新人券
老客复购券
沉睡客户唤醒券
午市引流券
晚市拉客券
外卖专用券
会员日券
节假日券
### 13.6 AiCouponPlan
@Data
public class AiCouponPlan {
    private String couponName;
    private String couponType;

    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountRate;

    private List<Long> applicableDishIds;
    private List<Long> applicablePackageIds;

    private String targetCustomerType;
    private String validStartDate;
    private String validEndDate;
    private String validTimeRange;

    private Integer totalQuantity;
    private Integer perUserLimit;

    private String campaignGoal;
    private String riskNote;
    private String displayCopy;
}

### 13.7 MarketingRiskAgent

必须校验：

套餐价是否低于成本
优惠力度是否过大
是否伤害原价菜品
是否和现有优惠叠加导致亏损
是否库存不足
是否供应商价格波动太大
是否只拉销量不拉利润

输出：

{
  "riskLevel": "warning",
  "warnings": [
    "水煮鱼套餐价 89 元时，预计毛利率低于 35%",
    "如果叠加满100减20，可能接近亏损"
  ],
  "safePriceRange": {
    "minPackagePrice": 96,
    "recommendedPrice": 99
  },
  "allowedCouponStacking": false
}

### 13.8 公众号文案输出
@Data
public class AiMarketingCopy {
    private String articleTitle;
    private String summary;
    private List<ArticleSection> sections;
    private String posterCopy;
    private String momentsCopy;
    private String miniProgramTitle;
    private String miniProgramButtonText;
}

## 14. OfficeGraph 办公助手

办公助手负责：

写通知
写会议纪要
写工作总结
写巡店报告
写培训材料
写整改通知
写审批说明
整理表格
生成内部公告

Agent：

OfficeSupervisorAgent
NoticeWriterAgent
MeetingMinutesAgent
DocumentWriterAgent
TrainingMaterialAgent
InspectionReportAgent
ApprovalAssistantAgent

会议纪要结构：

会议主题
参会人员
核心问题
决策事项
待办任务
责任人
截止时间
风险提醒

巡店报告结构：

门店名称
巡店日期
检查人
发现问题
问题分类
整改建议
责任人
截止时间
复查时间
## 15. KnowledgeGraph 企业知识库

支持集团内部资料问答：

公司制度
采购流程
报损流程
门店 SOP
菜品标准卡
培训手册
供应商合同
加盟商管理制度
营销方案
财务报销制度

流程：

文档上传
  ↓
切块 chunk
  ↓
向量 / ES 混合检索
  ↓
权限过滤
  ↓
来源重排
  ↓
带引用回答

Agent：

KnowledgeRouterAgent
RetrievalAgent
PolicyQaAgent
SopQaAgent
ContractQaAgent
TrainingQaAgent
CitationAnswerAgent

知识库回答必须带来源，例如：

根据《门店报损管理制度》第 3.2 条……
## 16. TaskGraph 任务督办

任务系统要和经营分析、营销、办公打通。

AI 发现问题后，可以生成任务：

通州店草鱼损耗异常
  ↓
是否创建整改任务给通州店店长？
  ↓
生成任务
  ↓
提醒负责人
  ↓
跟进完成情况

Agent：

TaskExtractorAgent
TaskCreateTool
AssigneeResolverNode
DeadlineParserNode
ReminderAgent
FollowUpAgent

任务对象：

@Data
public class AiTaskPlan {
    private String title;
    private String description;
    private String assigneeRole;
    private Long assigneeUserId;
    private String deadline;
    private String priority;
    private String sourceType;
    private Long sourceRunId;
}

## 17. Outcome Review 结果审核机制

必须实现 OutcomeReviewAgent。

所有重要 Agent 输出后，不直接返回用户，先审核。

尤其这些场景必须审核：

经营分析
月报
采购报表
优惠券方案
套餐方案
公众号文案
会议纪要
巡店报告
任务提取
### 17.1 审核流程
业务 Agent 生成结果
  ↓
OutcomeReviewAgent 按 rubric 审核
  ↓
合格 → 返回用户
不合格 → 生成 revisionInstruction
  ↓
原 Agent 返工
  ↓
最多返工 2 次
### 17.2 OutcomeReviewResult
@Data
public class OutcomeReviewResult {
    private boolean passed;
    private Integer score;
    private List<String> problems;
    private String revisionInstruction;
}

### 17.3 Rubric 文件目录
resources/ai/outcomes/
├── business-diagnosis-rubric.md
├── monthly-report-rubric.md
├── coupon-plan-rubric.md
├── combo-package-rubric.md
├── wechat-article-rubric.md
├── meeting-minutes-rubric.md
├── inspection-report-rubric.md
└── task-extraction-rubric.md

优惠券审核示例：

# 优惠券方案审核标准

满分 100 分，低于 85 分必须返工。

## 数据依据 25 分
- 是否使用菜品销量
- 是否使用毛利数据
- 是否考虑库存压力
- 是否考虑客单价

## 活动可落地性 25 分
- 是否有券名称
- 是否有门槛
- 是否有优惠金额
- 是否有有效期
- 是否有人群限制
- 是否有发放数量

## 利润安全 25 分
- 是否校验毛利
- 是否说明叠加风险
- 是否给出安全价格区间

## 文案完整度 25 分
- 是否有小程序标题
- 是否有活动页文案
- 是否有公众号标题
- 是否有朋友圈文案
## 18. Dreaming Memory 夜间复盘

实现一个类似“做梦”的机制，但产品里不要叫做梦，可以叫：

夜间复盘
AI 自我学习
经营记忆沉淀
Agent 经验总结
### 18.1 执行时间

每天凌晨执行。

每天 02:00
读取当天 AI Run / Agent Step / 用户反馈 / 人工修改记录
生成记忆候选
等待审核或自动写入长期记忆
### 18.2 复盘内容
哪些 Agent 经常失败
哪些回答被用户追问很多次
哪些报表被用户导出
哪些优惠券方案被采纳
哪些套餐方案被否决
哪些字段经常缺失
老板喜欢什么回答风格
某集团月报是否经常要求区分直营/加盟
某门店是否长期有损耗问题
### 18.3 记忆候选表
CREATE TABLE gb_ai_memory_candidate (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  target_type VARCHAR(50),
  target_id BIGINT,
  agent_name VARCHAR(100),
  memory_key VARCHAR(100),
  memory_value TEXT,
  evidence_json JSON,
  confidence DECIMAL(5,2),
  status VARCHAR(30),
  created_at DATETIME
);

注意：

涉及价格、优惠券规则、财务口径、权限规则的记忆，不允许自动生效，必须进入人工审核。

## 19. MultiAgentOrchestrator 多智能体并行编排

复杂任务不能由一个 Agent 完成。

例如：

生成本月华北区经营月报，重点看成本异常门店，并设计下月促销方案。

应拆成：

LeadAgent
  ├── StoreCompareAgent
  ├── CostDiagnosisAgent
  ├── ProcurementAgent
  ├── DishProfitAgent
  ├── MarketingAgent
  ├── ReportAgent
  └── OutcomeReviewAgent

并行执行后合并。

@Data
public class SubAgentTask {
    private String taskId;
    private String agentName;
    private String instruction;
    private Map<String, Object> input;
    private String status;
    private Object output;
}

## 20. SSE 流式过程展示

不要只流式返回最终回答。

要把 Agent 执行过程展示给用户。

SSE 事件：

run_started
agent_started
agent_finished
tool_started
tool_finished
agent_observation
answer_delta
review_started
review_finished
export_started
export_finished
run_finished
error

前端展示文案示例：

钱多多老师正在理解你的问题...
正在确认统计时间：本月到今天
正在读取菜品销量...
正在核对水煮鱼配方...
正在比较理论用量和实际出库...
正在生成诊断建议...
正在审核结果完整性...

SSE 示例：

{
  "event": "tool_finished",
  "tool": "dish_profit_analysis",
  "displayText": "已读取本月水煮鱼销量 120 份"
}

## 21. Trace 日志与数据库表

必须有 Agent 运行日志，方便调试、审计、优化。

### 21.1 gb_ai_agent_run
CREATE TABLE gb_ai_agent_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT,
  user_id BIGINT,
  department_id BIGINT,
  distributer_id BIGINT,
  workspace_mode VARCHAR(50),
  user_input TEXT,
  normalized_input TEXT,
  intent VARCHAR(100),
  status VARCHAR(50),
  start_time DATETIME,
  end_time DATETIME,
  total_duration_ms INT,
  model_provider VARCHAR(50),
  model_name VARCHAR(100),
  created_at DATETIME
);
### 21.2 gb_ai_agent_step
CREATE TABLE gb_ai_agent_step (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id BIGINT,
  step_order INT,
  step_type VARCHAR(50),
  step_name VARCHAR(100),
  input_json JSON,
  output_json JSON,
  status VARCHAR(50),
  duration_ms INT,
  error_message TEXT,
  created_at DATETIME
);
### 21.3 gb_ai_agent_observation
CREATE TABLE gb_ai_agent_observation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id BIGINT,
  agent_name VARCHAR(100),
  title VARCHAR(200),
  content TEXT,
  severity VARCHAR(30),
  metric_json JSON,
  created_at DATETIME
);
## 22. Export Center 文件导出中心

文件导出是平台级基础能力。

Agent 不能直接生成 PDF、Excel、Word。

正确关系：

Agent 生成结构化结果
  ↓
AiReport / AiDocument / AiCampaignPlan
  ↓
ExportAgent
  ↓
AiExportService
  ↓
PdfExporter / ExcelExporter / WordExporter / PptExporter
  ↓
生成文件
  ↓
上传服务器 / OSS
  ↓
返回 downloadUrl
### 22.1 支持文件
经营日报 PDF
月度经营报表 PDF
采购分析 Excel
门店对比 Excel
菜品毛利 Excel
优惠券方案 PDF
套餐活动方案 PDF
公众号文案 Word
巡店报告 Word / PDF
会议纪要 Word / PDF
PPT 汇报
### 22.2 接口设计
public interface AiExportService {

    ExportResult exportReport(AiReport report, ExportRequest request);

    ExportResult exportDocument(AiDocument document, ExportRequest request);

    ExportResult exportTable(AiTable table, ExportRequest request);
}
@Data
public class ExportRequest {

    private Long userId;
    private Long departmentId;
    private Long runId;

    private ExportFormat exportFormat;

    private String fileName;

    private Boolean includeAiSummary;
    private Boolean includeCharts;
    private Boolean includeRawData;
}
public enum ExportFormat {
    HTML,
    PDF,
    EXCEL,
    WORD,
    PPT
}
@Data
public class ExportResult {

    private Boolean success;

    private String fileName;
    private String fileType;
    private String fileUrl;
    private Long fileSize;

    private Long exportRecordId;
}

### 22.3 导出记录表
CREATE TABLE gb_ai_export_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id BIGINT,
  conversation_id BIGINT,
  user_id BIGINT,
  department_id BIGINT,

  export_type VARCHAR(50),
  business_type VARCHAR(100),
  file_name VARCHAR(255),
  file_url VARCHAR(500),
  file_size BIGINT,

  status VARCHAR(50),
  error_message TEXT,

  created_at DATETIME,
  updated_at DATETIME
);
### 22.4 PDF 生成方式

优先使用：

结构化数据
  ↓
HTML 模板
  ↓
HTML 转 PDF

模板目录：

resources/templates/export/
├── monthly-report.html
├── daily-report.html
├── purchase-report.html
├── coupon-plan.html
├── campaign-plan.html
├── inspection-report.html
└── meeting-minutes.html
### 22.5 Excel 生成方式

Excel 适合明细数据。

推荐使用：

EasyExcel
或 Apache POI

Excel 要支持：

多 Sheet
表头固定
金额格式
百分比格式
合计行
异常数据标记

例如采购分析 Excel：

Sheet1：汇总
Sheet2：商品采购明细
Sheet3：供应商分析
Sheet4：价格波动异常
Sheet5：AI 建议
### 22.6 下载权限

不要直接暴露真实文件地址。

下载接口：

GET /api/ai/export/download/{exportRecordId}

下载前检查：

是否文件创建人
是否有组织权限
是否有报表权限
是否有字段权限
文件是否过期
## 23. Async Job / Webhook

长任务必须异步：

集团月报 PDF
所有门店对比 Excel
PPT 汇报
活动复盘报表
夜间经营扫描
知识库重建索引

流程：

用户点击生成
  ↓
创建 async job
  ↓
后台执行
  ↓
完成后更新状态
  ↓
SSE / 站内通知 / 小程序订阅消息通知用户

相关类：

AiAsyncJob
AiAsyncJobService
AiJobQueue
AiJobWorker
AiWebhookController
AiNotificationService
## 24. 权限系统

权限是多智能体系统基础能力。

每个 Tool 执行前必须经过权限检查。

boolean allowed = permissionGuard.canAccess(
    userId,
    toolName,
    requestedScope,
    requestedFields
);

权限包括：

组织权限：
- 集团
- 区域
- 门店
- 部门
- 加盟店

功能权限：
- 是否能看报表
- 是否能看采购
- 是否能看利润
- 是否能生成优惠券
- 是否能创建任务

字段权限：
- 营业额
- 毛利
- 工资
- 采购价
- 供应商欠款
- 加盟店利润

同一份报表，不同角色可能显示不同字段。

## 25. Tool Registry 工具注册中心

所有查库、计算、创建对象的功能，都作为 Tool。

不要让 Agent 直接查库。

public interface AiTool {

    String name();

    ToolResult execute(ToolRequest request);
}

工具注册：

@Component
public class ToolRegistry {

    private final Map<String, AiTool> toolMap;

    public AiTool getTool(String name) {
        return toolMap.get(name);
    }
}

### 25.1 现网 Business Tool（`com.nongxinle.ai.tool.business`，与 `AiBusinessToolIds` 对齐）

| Tool id | Java 类 | 典型 path / 用途 |
|---------|---------|------------------|
| `revenue_query` | `RevenueQueryTool` | `revenue_overview_path`；经营 MULTI 四域收入侧 |
| `purchase_overview` | `PurchaseOverviewTool` | `purchase_overview_path`；**采购主线**与**成本链第 2 步**采购快照 |
| `warehouse_stock_overview` | `WarehouseStockOverviewTool` | `warehouse_stock_overview_path`；**库存现量/库房概览**（语义 wire `"STOCK_QUERY"` 亦映射到此 Tool） |
| `stock_reduce_query` | `StockReduceQueryTool` | `stock_reduce_query_path`；**出库/核销**专线 |
| `dish_profit_analysis` | `DishProfitAnalysisTool` | `dish_profit_path`；**D-8** `DISH_SALES_QUERY` / `dish_sales_query_path`；**成本链第 4 步**（标价收入读 `businessInsightSummary`） |
| `dish_ingredient_cost_breakdown` | `DishIngredientCostBreakdownTool` | 单菜配方/原料成本明细（非主链四域） |

**成本链（`cost_diagnosis_path`）**：上表四 Tool 顺序固定 — `revenue_query` → `purchase_overview` → `stock_reduce_query` → `dish_profit_analysis` — 后接 **`CostDiagnosisAgentNode`**（`StubOutcomeReviewNode`），门店粗估毛利率由 **`CostMarginDerivation`** 内部推导（**不**写回 `toolResults`）。**无** `gross_margin_calculator`；**无** classic business overview。

**经营诊断（`business_diagnosis_path`）**：`BusinessDataPlannerNode#applyBusinessDiagnosisBranch`（权限裁剪，常含四 Tool）→ 各域 `*AnswerPlan` → **`DiagnosisPlanBuilder`** + **`BusinessDiagnosisAgentV1.enrich`** → **`DiagnosisDeterministicRenderer`** / Composer。**Historical removed**：`BusinessDiagnosisPlan` / `BusinessDiagnosisPlanBuilder`（见 `docs/legacy-reference/business-diagnosis-plan-removed.md`）。

**Composite 诊断（旁路）**：`BusinessDiagnosisComposite*` — **HARNESS_ONLY** / **SHADOW** 观测与对照；**不写**用户 `finalAnswerText`；**PRIMARY 未接**。

**经营概览（`business_overview_path`）**：现网 **MULTI_AGENT 四域** 同上四 Tool id（**非** classic `business_overview_query` 六工具链）；正文见 `answer_delta.data.text`。

**库存边界**：**现量/库房** → `warehouse_stock_overview`；**出库/核销** → `stock_reduce_query`；**禁止**混用已删 `stock_query`。

### 25.2 Historical removed（勿恢复为现网 Tool）

| 已删 Tool id / 类 | 替代 |
|-------------------|------|
| `purchase_query` / `PurchaseQueryTool` | `purchase_overview` |
| `stock_query` / `StockQueryTool` | `warehouse_stock_overview` |
| `dish_sales_query` / `DishSalesQueryTool` | `dish_profit_analysis`（保留 intent/path `DISH_SALES_QUERY` / `dish_sales_query_path`） |
| `gross_margin_calculator` / `GrossMarginCalculatorTool` | `CostMarginDerivation` + `CostDiagnosisAgentNode` |
| `business_overview_query` / `BusinessOverviewQueryTool` | MULTI 四域 + `revenue_query` |
| `DishGrossMarginTool` | **Draft removed**（从未注册；毛利见上表成本链） |

索引：`docs/legacy-reference/*-removed.md`。

### 25.3 其它 Workspace Tool（规划中，未在 `src/main/java` 注册）

Report / Marketing / Office / Knowledge / Task 等：`ReportDataTool`、`CouponCreateTool`、`ComboCreateTool`、`KnowledgeSearchTool`、`ExportTool` 等 — **不得**与 §25.1 现网 Business Tool 混写为「已上线」。
## 26. 重要原则
### 26.1 AI 负责分析，不负责直接改数据

涉及以下操作必须用户确认：

创建优惠券
发布活动
创建套餐
发送公众号
创建任务
发送通知
修改报表口径
修改权限
### 26.2 查库和计算尽量确定性

不要让大模型算金额、毛利率、百分比。

大模型可以解释结果，但数字应由 Java 代码计算。

### 26.3 专家 Agent 必须结构化输出

不要让专家 Agent 直接输出自然语言。

统一输出：

{
  "agentName": "CostDiagnosisAgent",
  "summary": "本月成本偏高，主要由食材采购和损耗拉动。",
  "riskLevel": "warning",
  "keyMetrics": [],
  "findings": [],
  "recommendations": [],
  "needMoreData": false,
  "questions": []
}

最后由 AnswerComposerAgent 合成用户能看懂的话。

### 26.4 所有重要输出都要审核

尤其：

报表
优惠券
套餐
公众号文案
巡店报告
会议纪要
任务
经营诊断
### 26.5 前端要展示过程

让用户感觉 AI 在认真查账：

正在读取数据
正在分析异常
正在生成方案
正在审核结果
正在导出文件
## 27. 第一阶段开发目标

第一阶段不要一次性实现所有 Agent。

先把基础架构搭好：

core
gateway
security
tool
trace
workspace router
business graph
report graph
marketing graph
outcome review
export center

第一阶段必须完成：

1. AiRunState
2. AgentNode
3. AiGraphRunner
4. LlmGateway
5. ToolRegistry
6. AiPermissionGuard
7. AiRunTraceService
8. AiSseEventPublisher
9. WorkspaceRouterAgent
10. BusinessGraph 基础流程
11. ReportGraph 基础流程
12. MarketingGraph 基础流程
13. OutcomeReviewAgent 基础版
14. AiExportService 基础版
## 28. 第二阶段核心 Agent

优先实现这些：

Business:
- CostDiagnosisAgent
- DishProfitAgent
- ProcurementAgent
- StoreCompareAgent

Report:
- MonthlyReportAgent
- PurchaseReportAgent
- StoreCompareReportAgent

Marketing:
- CouponStrategyAgent
- ComboPackageAgent
- MarketingRiskAgent
- WechatArticleWriterAgent

Office:
- NoticeWriterAgent
- MeetingMinutesAgent

Task:
- TaskExtractorAgent
## 29. 第三阶段高级能力
1. MultiAgentOrchestrator 并行任务
2. DreamingMemoryJob 夜间复盘
3. CampaignReviewAgent 活动复盘
4. DailyBusinessScanAgent 自动日报
5. KnowledgeGraph 企业知识库
6. PPT 导出
7. 企业微信 / 小程序消息通知
## 30. 示例完整任务流程
用户输入
生成一份本月华北区经营月报，重点看成本异常门店，并设计下月促销方案。
系统执行
WorkspaceRouterAgent
  → REPORT_GENERATION

ReportGraph
  → 识别为区域经营月报
  → 解析时间：本月
  → 解析范围：华北区
  → 权限检查
  → 查询营业额、采购、库存损耗、菜品毛利、门店排行

MultiAgentOrchestrator
  ├── StoreCompareAgent
  ├── CostDiagnosisAgent
  ├── ProcurementAgent
  ├── DishProfitAgent
  └── MarketingSupervisorAgent

ReportBuilderAgent
  → 生成 AiReport

MarketingGraph
  → 生成下月促销方案
  → 生成套餐
  → 生成优惠券
  → MarketingRiskAgent 校验

OutcomeReviewAgent
  → 审核报表完整性
  → 审核营销方案毛利风险

ExportAgent
  → 生成 PDF
  → 生成 Excel

AnswerComposerAgent
  → 返回总结、下载链接、后续操作按钮
用户看到
《华北区 5 月经营月报》已生成。

核心结论：
华北区本月营业额 326 万，整体成本率 43.8%。
通州店、回龙观店成本率偏高。

异常原因：
1. 通州店草鱼损耗异常
2. 回龙观店牛肉采购价上涨
3. 部分门店套餐毛利偏低

下月促销建议：
1. 推出 99 元双人套餐
2. 搭配高毛利凉菜
3. 优惠券不允许和满减叠加

附件：
- 华北区5月经营月报.pdf
- 华北区5月经营明细.xlsx

可操作：
- 创建整改任务
- 生成公众号文案
- 查看通州店明细
- 导出 PPT
## 31. 给 Cursor 的执行要求

请按照以上架构开始创建项目骨架。

要求：

不要把所有逻辑写在一个 Service 中。
不要让 Agent 直接查数据库。
不要让 Agent 直接生成 PDF / Excel。
不要让大模型直接计算财务数字。
所有重要 Agent 输出必须是结构化 JSON。
所有 Tool 执行前必须走权限检查。
所有 Agent / Tool 执行必须写入 Trace。
SSE 要支持过程事件，不只是最终回答。
报表、优惠券、套餐、文案必须经过 OutcomeReviewAgent 审核。
文件导出必须统一走 Export Center。
长任务必须支持异步执行。
夜间复盘作为后续高级能力预留接口。
旧版 GbAiChatServiceImpl 只作为业务参考，不作为新架构中心。

本项目最终目标不是聊天机器人，而是：

餐饮集团 AI 经营办公平台。