# 顾问会话 API（D-Advisor-09B，已实现）

本文档描述 **已实现** 的「业务顾问页」会话恢复与消息体系，便于前台接入与运维执行 DDL。不写设计假设。

**全局前缀**：应用配置 `server.servlet.context-path=/api`（见 `src/main/resources/application.properties`）。下文接口路径均需此前缀。

---

## 一、功能目标

**D-Advisor-09B** 解决的核心问题：**业务顾问页**上的自然语言对话必须落在正式的 `conversation` / `message` 体系里，前台不再依赖 `localStorage` 维护「advisorId → conversationId」，关闭/重开应用后可稳定恢复顾问对话。

应纳入同一会话线程的典型场景包括：

- 用户手工提问  
- 顾问普通回复  
- 用户点击「让顾问解读工作流结果」而 **自动拼装的用户提问**  
- 顾问对该工作流结果的 **文字解释回复**  

**数据来源分工**：

- **工作流结果卡片**：结构化数据来自工作流运行表（本项目实体 `GbAiWorkflowRunEntity`，表 **`gb_ai_workflow_run`**），按需通过「最近工作流运行」等接口恢复展示。  
- **顾问自然语言对话**：会话锚点在 **`gb_ai_conversation`**，消息在 **`gb_ai_message`**，通过 **`GET .../conversation`** 与后续 **`POST /api/ai/runs`**（带同一 `conversationId`）保持一致。

---

## 二、概念边界

### 1. Advisor（顾问）

业务侧定义的顾问角色实例，存储在 **`gb_ai_advisor`**（如老板经营顾问、采购分析顾问、门店督导顾问）。列表与详情由既有顾问接口提供；本会话 API 会先校验 **`enabled = 1`**。

### 2. Advisor Conversation（顾问会话线程）

在某个 **`userId`**、某个 **`advisorId`**、以及确定的 **统计范围（scope）+ 门店/集团锚点（departmentId / distributerId）** 下的 **一条稳定长期线程**。

后端识别方式：

- **`gb_ai_conversation.gb_ai_conversation_advisor_id`** = 顾问主键  
- **`gb_ai_conversation.gb_ai_conversation_thread_kind`** = **`'ADVISOR'`**（常量见 `AiAdvisorConversationConstants.THREAD_KIND_ADVISOR`）

### 3. Message（消息）

用户/顾问的自然语言条目，存储在 **`gb_ai_message`**。顾问解释工作流结果产出的助手回复仍是普通的 **assistant message**，与手工问答无表结构区分（本轮不提供 `messagePurpose` / metadata）。

### 4. WorkflowRun（工作流运行）

一次工作流的结构化运行记录，存储在 **`gb_ai_workflow_run`**。UI 上以「卡片」展示，**不替代** `gb_ai_message`；顾问针对卡片的口头解读仍记入 message。

### 5. AiRun / runId

一次 Harness 管线执行：`POST /api/ai/runs` 创建。实现上可向当前 **`conversationId`** 写入 user/assistant **message**，并在 DTO 中暴露 **`runId`**（消息粒度的运行关联以实现有则填）。

---

## 三、接口契约

### `GET /api/ai/advisors/{advisorId}/conversation`

**完整 URL 形态**：`{origin}/api/ai/advisors/{advisorId}/conversation`

### Query 参数

| 参数 | 必填 | 说明 |
|------|------|------|
| `userId` | 是 | 与会话归属一致（`gb_ai_conversation_user_id`，一般为 `gb_department_user` 主键语义，与既有 Run 约定一致）。 |
| `departmentId` | 否 | 单店：**STORE** 场景下的门店父部门 id。 |
| `distributerId` | 否 | 集团：**GROUP** 场景下的分销商/集团 dis id。 |
| `scopeMode` | 否 | **`STORE`** 或 **`GROUP`**（见 `AiConversationScopeMode.fromApiString`）。 |

### scopeMode 推断规则（已实现）

对应 `AiAdvisorConversationServiceImpl#inferScope`：

1. **显式传 `scopeMode`**：优先使用该值解析为 `AiConversationScopeMode`。  
2. **未传 `scopeMode` 且提供了 `departmentId`**：视为 **STORE**。  
3. **未传 `scopeMode` 且提供了 `distributerId`**：视为 **GROUP**。  
4. **均未提供**：抛出 **`IllegalArgumentException`** → 接口返回 **400**（提示需 `scopeMode` 或 `departmentId`/`distributerId`）。

> `AiConversationCoreService#getOrCreateAdvisorConversation` 在 **STORE** 下要求 `departmentId`，**GROUP** 下要求 `distributerId`；并按门店反推或使用传入的 distributer。

### 行为步骤（已实现）

1. **`gb_ai_advisor`** 存在且 **`gb_ai_advisor_enabled = 1`**，否则 **400**（`advisor not found or disabled`）。  
2. 按 **`userId` + `advisorId` + `threadKind = ADVISOR` + scope + department/distributer** 查询 **`gb_ai_conversation`**（取最新一条 `UPDATE_TIME`）。  
3. 不存在则 **INSERT**，写入：  
   - `gb_ai_conversation_advisor_id = advisorId`  
   - `gb_ai_conversation_thread_kind = 'ADVISOR'`  
   - 以及与 Run 对齐的 scope / user / title 等字段（见 `AiConversationCoreService#getOrCreateAdvisorConversation`）。  
4. 调用 **`AiConversationHistoryService#listMessages(conversationId, userId)`**，内部会 **`requireConversationOwnedByUser`** 并拼装 **`AiConversationMessageDTO`**。  
5. 返回 **`AiAdvisorConversationBootstrapDTO`**：`conversationId`、`advisorId`、`conversationType`（字符串展示用，固定 `ADVISOR`）、`threadKind`、`title`、`messages`。

**控制器**：`AiAdvisorController#advisorConversation`。

---

## 四、返回 JSON 示例

`R` 为 `HashMap` 封装；成功时 **`code` 默认为 0**。本接口当前实现为 `R.ok().put("data", ...)`，**不强制写入 `msg` 字段**（`R` 使用 `@JsonInclude(NON_NULL)`，未 put 的键不出现在 JSON 中）。下面示例中 **`msg` 为可选用法**（前端可只依赖 `code` + `data`）。

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "advisorId": 1,
    "conversationId": 10042,
    "conversationType": "ADVISOR",
    "threadKind": "ADVISOR",
    "title": "老板经营顾问 · 顾问对话",
    "messages": [
      {
        "messageId": 9001,
        "role": "user",
        "content": "本月营业额?",
        "status": "COMPLETED",
        "runId": 555,
        "createdAt": "2026-05-16 10:01:02",
        "updatedAt": "2026-05-16 10:01:02",
        "createTime": "2026-05-16 10:01:02",
        "updateTime": "2026-05-16 10:01:02",
        "pinned": false,
        "pinId": null,
        "noted": false,
        "noteId": null
      }
    ]
  }
}
```

**说明**：

- **`messages`** 类型为 **`AiConversationMessageDTO`**（与历史会话消息接口同源）。  
- **`createdAt` / `updatedAt`** 与 **`createTime` / `updateTime`** 同源（同一格式化的创建/更新时间字符串）。  

---

## 五、`POST /api/ai/runs` 调用约定

前台在 **`GET .../conversation`** 拿到 **`conversationId`** 后，顾问页后续任意自然语言往返（含「解读工作流结果」）均应携带 **`conversationId`** 与 **`advisorId`**，与请求体 **`AiRunCreateRequest`** 字段对齐。

示例请求体：

```json
{
  "userId": 3,
  "departmentId": 1,
  "distributerId": 2,
  "scopeMode": "GROUP",
  "advisorId": 5,
  "conversationId": 10042,
  "message": "这个月门店情况怎么样？"
}
```

**约定说明**：

- **`conversationId`**：复用 **同一顾问会话线程**（见第二节）；勿在本地捏造，应来自服务端 `GET .../conversation` 或由 Run 首轮返回后继续带回。  
- **`advisorId`**：Run 归因与诊断；**不参与**语义与安全边界（见 `AiRunCreateRequest` 注释）。  
- **消息落库**：继续走 **`AiRunService`** 编排与 **`AiRunMessagePersistenceService`**。  
- **「解读工作流结果」**：同样 **`POST /api/ai/runs`**，同一 **`conversationId` + advisorId**，仅 **`message`** 内容为前端拼好的用户提问即可。

DTO 源码：`com.nongxinle.ai.platform.dto.AiRunCreateRequest`。

---

## 六、数据库变更

**脚本路径**：`sql/gb_ai_conversation_advisor_thread.sql`

**作用**（幂等）：

- 增加 **`gb_ai_conversation.gb_ai_conversation_advisor_id`**（`BIGINT NULL`）  
- 增加 **`gb_ai_conversation.gb_ai_conversation_thread_kind`**（`VARCHAR(32) NULL`）  

**语义**：

- **`thread_kind = 'ADVISOR'`**：顾问长期会话线程。  
- **`advisor_id`**：绑定 **`gb_ai_advisor`**。

**种子说明**：`sql/gb_ai_advisor_workflow_seed.sql` 头部已注明：顾问会话恢复依赖上述列，**需先执行** `gb_ai_conversation_advisor_thread.sql`。

**建议执行顺序**：

1. 保证 **`gb_ai_conversation`** 表已存在并完成既有会话扩展（如 `sql/gb_ai_conversation_history_extensions.sql` 等环境内既定脚本）。  
2. 再执行 **`sql/gb_ai_conversation_advisor_thread.sql`**。  
3. 再按需执行 **`gb_ai_advisor_workflow_mvp.sql` / seed**。

---

## 七、代码改动清单（已实现）

| 模块 | 文件 | 说明 |
|------|------|------|
| Controller | `src/main/java/com/nongxinle/controller/AiAdvisorController.java` | 新增 **`GET /{advisorId}/conversation`**；注入 **`AiAdvisorConversationService`**。 |
| 顾问会话编排 | `src/main/java/com/nongxinle/ai/advisor/AiAdvisorConversationService.java` | 接口 `getOrBootstrap(...)`。 |
| 顾问会话编排 | `src/main/java/com/nongxinle/ai/advisor/AiAdvisorConversationServiceImpl.java` | 启用校验、scope 推断、调用 `AiConversationCoreService.getOrCreateAdvisorConversation` 与 `listMessages`。 |
| 常量 | `src/main/java/com/nongxinle/ai/advisor/AiAdvisorConversationConstants.java` | `THREAD_KIND_ADVISOR = "ADVISOR"`。 |
| 响应 DTO | `src/main/java/com/nongxinle/ai/history/dto/AiAdvisorConversationBootstrapDTO.java` | `conversationId`、`advisorId`、`conversationType`（字符串，`ADVISOR`）、`threadKind`、`title`、`messages`。 |
| 会话核心 | `src/main/java/com/nongxinle/ai/conversation/AiConversationCoreService.java` | 声明 **`getOrCreateAdvisorConversation`** 及 Run / 历史共用会话方法。 |
| 会话核心 | `src/main/java/com/nongxinle/ai/conversation/AiConversationCoreServiceImpl.java` | 幂等查询 + 插入顾问线程；`List#get(0)` 兼容 Java 17。 |
| 实体 | `src/main/java/com/nongxinle/entity/GbAiConversationEntity.java` | `gbAiConversationAdvisorId`、`gbAiConversationThreadKind`。 |
| 历史消息 | `src/main/java/com/nongxinle/service/impl/AiConversationHistoryServiceImpl.java` | 构造 **`AiConversationMessageDTO`** 时补全 **`createTime` / `updateTime`**（与 created/updated 同源）。 |
| 消息 DTO | `src/main/java/com/nongxinle/ai/history/dto/AiConversationMessageDTO.java` | 含 **`createTime` / `updateTime`** 字段。 |
| MyBatis XML | `src/main/resources/mapper/GbAiConversationMapper.xml` | **`BaseResultMap`** 增加 **`gb_ai_conversation_advisor_id`、`gb_ai_conversation_thread_kind`**，避免 XML **`SELECT *`** 映射丢字段。 |

（消息列表接口定义见 **`AiConversationHistoryService#listMessages`**；Run 侧见 **`AiRunService`**、**`AiRunMessagePersistenceService`**。）

---

## 八、前端接入流程

1. 用户进入 **业务顾问页**。  
2. 用户选择某个 **`advisorId`**（来自顾问列表）。  
3. 调用：**`GET /api/ai/advisors/{advisorId}/conversation?userId=...`**，并按环境补充 **`departmentId`（单店）** 或 **`distributerId` + `scopeMode=GROUP`（集团）**。  
4. 将响应中的 **`conversationId`** 存到 **当前页/当前 advisor 会话状态**（可用内存状态管理；无需再塞 localStorage 作为唯一真源）。  
5. 用 **`messages`** 渲染历史气泡列表。  
6. 用户发送普通消息：**`POST /api/ai/runs`**，带 **`advisorId` + `conversationId` + message** 等与 scope 对齐字段。  
7. 用户点击「让顾问解读工作流结果」：**同样 `POST /api/ai/runs`**，同一 **`conversationId` + advisorId**，**`message`** 为拼接好的解读请求文案。  
8. 关闭/重开应用：再次 **`GET`** 本会话接口即可恢复 **`gb_ai_message`** 历史。  
9. **工作流结果卡片** 仍通过 **workflow run** 列表/详情恢复，**不要**把卡片内容与 `messages` 混在同一列表数据源。

---

## 九、图钉 / 笔记关系

- **顾问普通回复**与**顾问解读工作流结果的回复**在存储上均为 **assistant **`gb_ai_message`**，均有 **`messageId`**。  
- 可 **复用现有图钉 / 笔记 API**（以 `conversationId`、`messageId` 等为锚点）；**不需要**为 `workflow_explanation` 单独再造一套图钉/笔记模型。  
- **工作流结果卡片**若未来需要图钉/笔记，建议在 **`WORKFLOW_RUN`** 维度单独设计 **`source_type=WORKFLOW_RUN`** 等——**不属于 D-Advisor-09B 范围**。  

---

## 十、curl 验收命令

```bash
BASE=http://localhost:8090/api
```

**单店（STORE，`departmentId` 推断 STORE）**

```bash
curl -sS "$BASE/ai/advisors/1/conversation?userId=3&departmentId=1" | python3 -m json.tool
```

**集团（显式 GROUP）**

```bash
curl -sS "$BASE/ai/advisors/1/conversation?userId=3&distributerId=2&scopeMode=GROUP" | python3 -m json.tool
```

**示例：创建 Run（需在库中确有该 `conversationId`）**

```bash
curl -sS -X POST "$BASE/ai/runs" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 3,
    "distributerId": 2,
    "scopeMode": "GROUP",
    "advisorId": 1,
    "conversationId": 10042,
    "message": "这个月经营怎么样？"
  }' | python3 -m json.tool
```

---

## 十一、验收标准

1. **同一 `userId` + `advisorId` + 同一 scope/锚点**，多次 **`GET .../conversation`** 返回 **同一 `conversationId`**。  
2. **不同 `advisorId`** 在无冲突键的情况下应对应 **不同的顾问会话行**（不同 `conversationId`）。  
3. **`POST /api/ai/runs` 带上 `conversationId`** 后，产生的 user / assistant **message** 归属于 **该 `conversationId`**（与既有落库链路一致）。  
4. **关闭并重开前台**后，再次 **GET**，能恢复 **`gb_ai_message`** 历史。  
5. 恢复出的 assistant **message** 含 **`messageId`**，可作为图钉/笔记接口入参。  
6. **工作流结果卡片**仍从 **`gb_ai_workflow_run`**（及现有查询 API）恢复，**不与 messages 混淆**。  

---

## 十二、已知限制 / 后续 D-Advisor-09C（未实现）

**本轮不做**：

- **`messagePurpose` 语义字段**  
- **`workflowRunId` / source / explain** 等与解读链路的 **message 级 metadata**  
- **工作流结果卡片**层面的图钉/笔记  
- 按 **`advisorId` 分页列出多个历史顾问会话列表**  

**后续 D-Advisor-09C 可增强（示例方向）**：

- **`gb_ai_message.metadata_json`（或等价扩展）**  
- **`messagePurpose = workflow_explanation`**  
- **`workflowRunId`、`sourceRunId`、`explainRunId`** 等可追溯字段  

（具体表结构以后再定，不在本文档范围。）

---

## 十三、编译说明

若本机 **`mvn compile`** 报错 **`无效的标记: --release`**：说明 **`javac`/JAVA_HOME 过旧**，不识别 Maven Compiler 的 **`--release`**。本项目 **`pom.xml`** 指定 **Java 17**。请将 Maven 指向 **JDK 17+** 后再编译；**这与 D-Advisor-09B 业务逻辑改动无关**。  

---

**文档路径**：`docs/ai/advisor-conversation-api.md`  

**是否按当前实现撰写**：是；接口、表名、`thread_kind`、`AiRunCreateRequest` 字段、类路径均与仓库代码一致（非方案稿）。  

**前台阶段**：在满足 DDL 执行与上述验收标准后，**可以进入 D-Advisor-10 前台接入**。  
