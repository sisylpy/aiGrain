# 店长工作记录 API（第一阶段）

Base path: `/api/ai/work-records`（与现有 AI 接口一致，带 `server.servlet.context-path=/api` 时控制器映射为 `ai/work-records`）。

**隐私**：WORK_RECORD 为店长个人工作日记。列表与重试均强制 `recorderUserId = 当前 userId`；集团/区域/门店管理权限不代表可查看他人记录。

**sourceRunId**：响应字段表示该记录**最近一次** AI 处理对应的 Run ID；retry 成功后更新为最新 Run，历史 Run 仍在 `gb_ai_agent_run`。

## 1. 获取/创建长期会话

`GET /api/ai/work-records/conversation`

| 参数 | 必填 | 说明 |
|------|------|------|
| userId | 是 | gb_department_user 主键 |
| departmentId | 是 | 门店锚点（可传子部门，服务端归一到门店根） |
| distributerId | 否 | 缺省从部门/用户推导 |

响应 `data`：

```json
{
  "conversationId": 123,
  "conversationType": "WORK_RECORD",
  "threadKind": "WORK_RECORD",
  "title": "店长工作记录"
}
```

## 2. 新增工作记录

`POST /api/ai/work-records`

Body：

```json
{
  "userId": 1,
  "departmentId": 10,
  "distributerId": 2,
  "inputType": "TEXT",
  "content": "今天张三迟到半小时，已口头提醒。",
  "recordedAt": "2026-06-10T15:30:00"
}
```

- `inputType`: `TEXT` | `VOICE_TRANSCRIPT`（语音转写结果由前端传入）
- `recordedAt` 可选，缺省为提交时刻

响应 `data` 为完整 `WorkRecordResponse`（含 `aiStatus`）。

## 3. 列表查询

`GET /api/ai/work-records?userId=&departmentId=&startDate=2026-06-01&endDate=2026-06-10&categoryId=&page=1&pageSize=50`

服务端过滤条件（同时满足）：

- `gb_wr_recorder_user_id = userId`（仅本人日记）
- `gb_wr_department_id = 门店根`
- `gb_wr_distributer_id = 当前集团`

按 `recordedAt` 倒序。前端按日期分组展示。

## 4. 分类列表

`GET /api/ai/work-records/categories?userId=&distributerId=`

仅返回 ACTIVE 分类。

## 5. 从业务卡片记到工作记录（P1）

`POST /api/ai/work-records/from-business-card`

前端**只传业务来源引用**，不得上传库存数量、采购金额、供应商名称等可信事实；事实由服务端从 assistant message 的 `gb_ai_message_cards_json` 快照解析。

Body：

```json
{
  "userId": 13,
  "departmentId": 48,
  "distributerId": 10,
  "sourceConversationId": 123,
  "sourceMessageId": 456,
  "sourceRunId": 1781077045578,
  "sourceCardType": "WAREHOUSE_INVENTORY_RISK_LIST_CARD",
  "sourceItemKey": "goodsId:101",
  "sourceAnswerPlanType": "WAREHOUSE_LOW_STOCK_RISK"
}
```

`sourceAnswerPlanType` 可选，仅辅助校验，不作为事实选择主权。

### P1 支持的 cardType 与 itemKey

| sourceCardType | sourceItemKey | 说明 |
|----------------|---------------|------|
| `WAREHOUSE_INVENTORY_RISK_LIST_CARD` | `goodsId:{id}` | 库存风险列表中的单商品行（`payload.riskItems[]`） |
| `PURCHASE_GOODS_AMOUNT_RANKING_CARD` | `disGoodsId:{id}` | 商品采购金额排行中的单商品行 |
| `PURCHASE_GOODS_COUNT_RANKING_CARD` | `disGoodsId:{id}` | 商品采购次数排行中的单商品行 |
| `PURCHASE_GOODS_QUANTITY_RANKING_CARD` | `disGoodsId:{id}` | 商品采购数量排行中的单商品行 |
| `PURCHASE_GOODS_BUSINESS_ANALYSIS_CARD` | `__CARD__` | 单商品采购经营分析整卡 |

不支持 `rowIndex`；无稳定业务 ID 的卡片本阶段不可用。

### 来源校验链（任一步失败返回 400 + errorCode）

1. `sourceConversationId` 属于当前 `userId`
2. `sourceMessageId` 属于该 Conversation
3. Message `role` 必须是 `assistant`
4. Message `runId` 必须等于 `sourceRunId`
5. `cards_json` 中存在 `sourceCardType`
6. 卡片中存在 `sourceItemKey` 对应实体或行
7. 当前用户仍具有对应门店范围权限

### 错误码（`errorCode` 字段）

| errorCode | 含义 |
|-----------|------|
| `BUSINESS_SOURCE_CONVERSATION_NOT_OWNED` | 业务会话不属于当前用户 |
| `BUSINESS_SOURCE_MESSAGE_NOT_FOUND` | 消息不存在或不是 assistant |
| `BUSINESS_SOURCE_RUN_MISMATCH` | `sourceRunId` 与消息 runId 不一致 |
| `BUSINESS_SOURCE_CARDS_MISSING` | assistant 消息无有效 `cards_json` |
| `BUSINESS_SOURCE_CARD_NOT_FOUND` | `cards_json` 中无该 `sourceCardType` |
| `BUSINESS_SOURCE_ITEM_NOT_FOUND` | 卡片内找不到 `sourceItemKey` 对应行 |
| `BUSINESS_SOURCE_CARD_UNSUPPORTED` | 该 cardType 本阶段未支持 |
| `BUSINESS_SOURCE_ALREADY_RECORDED` | 并发写入冲突且无法定位已有记录 |

幂等：同一 `recorderUserId + bizMessageId + bizCardType + bizItemKey` 重复点击时，直接返回已创建的记录（不重复调用 LLM）。

响应 `data` 为 `WorkRecordResponse`，额外字段：

- `originType`: `BUSINESS_CARD`
- `inputType`: `BUSINESS_CARD`
- `bizConversationId` / `bizMessageId` / `bizRunId` / `bizCardType` / `bizItemKey`：业务来源
- `conversationId` / `sourceMessageId` / `sourceRunId`：仍指**私人 WORK_RECORD 链路**

手动创建路径：`originType=MANUAL`，`inputType=TEXT|VOICE_TRANSCRIPT`。

数据库迁移：`sql/gb_work_record_business_origin.sql`。

## 6. 读取来源业务卡片（只读）

`GET /api/ai/work-records/{recordId}/source-card?userId=`

用户在工作记录中点击「来源」时调用。从后台消息库重建当时卡片，**不调用 LLM**，不信任前端传入的卡片数据。

### 校验

1. 记录存在且未软删
2. `recorderUserId = userId`（店长私人记录）
3. 门店范围与记录一致
4. `originType = BUSINESS_CARD`（手动记录不可查来源卡）

### 响应 `data`

```json
{
  "recordId": 12,
  "conversationId": 123,
  "messageId": 456,
  "runId": 1781077045578,
  "cardType": "WAREHOUSE_INVENTORY_RISK_LIST_CARD",
  "itemKey": "goodsId:101",
  "payload": { "goodsId": 101, "goodsName": "鲜三黄鸡", "restWeight": 20 },
  "rawFactText": "商品：鲜三黄鸡\n当前库存：20斤\n...",
  "timestamp": "2026-06-10T15:30:00",
  "scopeLabel": "XX门店",
  "sourceAnswerPlanType": "WAREHOUSE_LOW_STOCK_RISK",
  "cardTitle": "库存风险关注列表",
  "cardSubtitle": "截至2026-06-10",
  "chartType": "TABLE"
}
```

- `conversationId` / `messageId` / `runId`：业务 advisor 来源（对应 `gb_wr_biz_*`）
- `payload`：行级卡为匹配行；整卡（`itemKey=__CARD__`）为完整 card payload
- `rawFactText`：创建时保存的事实文本（`gb_wr_raw_content`）
- `cardTitle` / `cardSubtitle` / `chartType`：辅助前端按聊天卡片壳渲染

### 错误码

| errorCode | 含义 |
|-----------|------|
| `WORK_RECORD_NOT_FOUND` | 记录不存在或不属于当前用户 |
| `NOT_BUSINESS_CARD` | 非业务卡片来源（`originType≠BUSINESS_CARD`） |
| `BUSINESS_CARD_NOT_FOUND` | 来源消息/卡片/行在 `cards_json` 中无法重建 |

幂等：重复请求返回相同内容。

## 7. 编辑工作记录

`PUT /api/ai/work-records/{recordId}?userId=`

仅修改 AI 整理后的展示正文 `polishedContent`。**不**调用 LLM、不重新分类、不写入 Message/Run。

Body（二选一）：

JSON：

```json
{
  "content": "用户修改后的工作记录内容"
}
```

或 `application/x-www-form-urlencoded` 表单字段 `content=...`（微信小程序 `wx.request` 默认表单提交兼容）。

- `content` 去首尾空格后不能为空
- 仅更新 `gb_wr_polished_content`、`gb_wr_updated_at`
- 不修改 `rawContent`、分类、来源字段、`recordedAt`、Conversation/Message/Run 等
- 手动记录与 `originType=BUSINESS_CARD` 记录均只改 `polishedContent`
- `userId` 查询参数必填（店长私人记录归属校验，与 `retry-ai` 一致）

响应 `data`：完整 `WorkRecordResponse`。

### 错误码

| errorCode | 含义 |
|-----------|------|
| `WORK_RECORD_NOT_FOUND` | 记录不存在或不属于当前用户 |
| `WORK_RECORD_CONTENT_EMPTY` | `content` 为空 |
| `WORK_RECORD_UPDATE_FAILED` | 条件更新未命中（并发或状态异常） |

## 8. 删除工作记录

`DELETE /api/ai/work-records/{recordId}?userId=`

- 物理删除 `gb_work_record` 单行
- **不**删除业务 Conversation、WORK_RECORD Conversation、`gb_ai_message`、Run/Step、分类、其他记录
- `originType=BUSINESS_CARD` 删除后，业务来源唯一约束释放，可再次从原卡片创建新记录
- `userId` 查询参数必填（归属校验）

响应 `data`：

```json
{
  "recordId": 12,
  "deleted": true
}
```

### 错误码

| errorCode | 含义 |
|-----------|------|
| `WORK_RECORD_NOT_FOUND` | 记录不存在或不属于当前用户 |
| `WORK_RECORD_DELETE_FAILED` | 物理删除未命中 |

## 9. 重试 AI（可选）

`POST /api/ai/work-records/{id}/retry-ai?userId=`

- 仅 `aiStatus=FAILED` 且 `recorderUserId=userId` 的记录可重试
- 使用原子条件更新 `FAILED → PROCESSING`；并发第二个请求返回「正在处理或当前状态不可重试」
- 合法重试创建新 Run，**不**重复插入 user message；追加新 assistant message
- 成功 assistant 回执格式：

```text
已记录 · {categoryName}
{polishedContent}
```

失败 assistant 回执：`记录已保存，但 AI 暂时未完成整理和分类。`

## 10. 前端页面建议（小程序）

路径建议：`subPackage/pages/workRecord/workRecord`

- 进入页：调用 `conversation` + `categories` + `list`
- 输入区：多行文本 + 语音按钮（复用现有 STT，将文字填入输入框，`inputType=VOICE_TRANSCRIPT`）
- 提交：POST 创建；`aiStatus=PROCESSING` 时展示 loading；`FAILED` 展示「记录已保存，AI 整理失败」+ 重试按钮
- 列表：按今天/昨天/更早分组；每条显示时间、分类标签、整理内容；原始内容折叠
- 顶部分类 chips 筛选 `categoryId`

语音：本仓库无 STT 后端，小程序侧录音→转写→`content` 文本提交即可。
