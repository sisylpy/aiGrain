# 店内公告栏 API（第一阶段）

Base path: `/api/store-announcements`（与现有接口一致，`server.servlet.context-path=/api` 时控制器映射为 `store-announcements`）。

**公共性**：公告为门店公共内容；列表/详情对同门店范围内**所有员工**可见（`StoreAnnouncementScopeGuard` 仅校验集团/门店 subtree 成员身份，**不**按 `publisherUserId` 过滤，**不**要求 WORK_RECORD 或 AI 对话权限）。发布仍校验发布人归属；删除仅允许**发布人本人**。

**快照原则**：展示字段来自发布时写入的快照（`textContent` / `cardSnapshotJson` / `cardsSnapshotJson`），不依赖原 WorkRecord、Pin、Message 是否仍存在。前端**不得**上传 card JSON 作为可信事实。

## 数据模型摘要

主表：`gb_store_announcement`（见 `sql/gb_store_announcement_mvp.sql`）

| 字段 | 说明 |
|------|------|
| `announcementType` | `TEXT` / `BUSINESS_CARD` / `AI_MESSAGE` |
| `sourceType` | `WORK_RECORD` / `WORK_PIN` / `WORK_NOTE` / `DIRECT`（第一阶段未实现 DIRECT 发布） |
| `sourceId` | 来源主键 |
| `textContent` | 文字公告或 AI 回答正文 |
| `cardType` + `cardSnapshotJson` | 单张业务卡片快照（`BUSINESS_CARD`） |
| `cardsSnapshotJson` | assistant `cards[]` 完整 JSON（`AI_MESSAGE`） |
| `sourceItemKey` | 行级 BUSINESS_CARD 来源 itemKey（如 `goodsId:101`） |
| `publishStatus` | `PUBLISHED` / `DELETED` |

### 类型区分

| announcementType | 何时使用 | 展示字段 |
|------------------|----------|----------|
| `TEXT` | 工作日志 `originType=MANUAL`，发布 `polishedContent`（fallback `rawContent`） | `textContent` |
| `BUSINESS_CARD` | 工作日志 `originType=BUSINESS_CARD`，发布来源卡片 | `cardType`, `cardSnapshotJson` |
| `AI_MESSAGE` | 从图钉或工作笔记发布 assistant 回答；有 cards 时附带完整 cards 数组 | `textContent`, 可选 `cardsSnapshotJson` |

图钉表 `gb_ai_work_pin` 的 `sourceTextSnapshot` 由后端在创建图钉时从 assistant `message.content` 写入；公告正文**优先**读 message.content，pin 快照仅作 message 缺失时的后备。cards 在发布时从 `gb_ai_message.gb_ai_message_cards_json` 读取。

## 1. 从工作日志发布

`POST /api/store-announcements/from-work-record/{recordId}`

Body（`userId` 必填）：

```json
{
  "userId": 13,
  "departmentId": 48,
  "distributerId": 10,
  "title": "可选覆盖标题"
}
```

服务端逻辑：

1. 校验记录归属当前用户且门店 scope 一致（`WorkRecordOwnership` + `StoreAnnouncementScopeGuard`）。
2. 若同门店已有同 `sourceType` + `sourceId` 的公告（`PUBLISHED` 或曾 `DELETED`），**按当前来源重新生成快照并更新该条记录**（刷新 `publishedAt`，不新增第二条）；`announcementId` 保持不变。
3. `originType=MANUAL` → `TEXT`，正文取 `polishedContent` → fallback `rawContent`。
4. `originType=BUSINESS_CARD` → `BUSINESS_CARD`：必须同时校验 `bizMessageId`、`bizRunId`、`bizCardType`、`bizItemKey`，在 message cards 中按 cardType 定位卡片并用 itemKey 校验行级来源，保存**完整卡片 JSON** 到 `cardSnapshotJson`，`sourceItemKey` 写入公告。
5. 写入追溯字段 `sourceConversationId` / `sourceMessageId` / `sourceRunId`。
6. `publishStatus=PUBLISHED`，`publishedAt=now`。

响应 `data`：`StoreAnnouncementResponse`（见第 5 节）。

## 2. 从图钉发布

`POST /api/store-announcements/from-pin/{pinId}`

Body 同第 1 节。

服务端逻辑：

1. 校验 pin 归属当前用户且会话归属一致。
2. 若同门店已有同 `sourceType` + `sourceId` 的公告，**更新快照**（规则同工作日志发布）。
3. 正文优先 assistant `message.content`；message 不可用时使用 pin 已落库的 `sourceTextSnapshot`。
4. 若 pin 关联 `messageId`，读取 assistant message 的 `cards_json`（非空则原样写入 `cardsSnapshotJson`）。
5. 统一 `announcementType=AI_MESSAGE`。
6. 门店 scope 优先取 body；缺省从 pin 所属 `gb_ai_conversation` 推导。

## 3. 从工作笔记发布

`POST /api/store-announcements/from-note/{noteId}`

Body 同第 1 节。

服务端逻辑：

1. 校验 note 归属当前用户；若有 `conversationId` / `primaryConversationId` 则校验会话归属。
2. 若同门店已有同 `sourceType` + `sourceId` 的公告，**更新快照**（规则同工作日志发布）。
3. 正文优先 `contentMd`（用户可编辑），其次 `sourceTextSnapshot`；message.content 仅作后备。
4. 若 note 关联 `primaryMessageId`，读取 assistant message 的 `cards_json`（非空则写入 `cardsSnapshotJson`），`announcementType=AI_MESSAGE`；纯手动笔记为 `TEXT`。
5. 门店 scope 优先取 body；缺省从 note 所属会话推导；无会话的手动笔记需在 body 传 `departmentId` + `distributerId`。

## 4. 列表查询

`GET /api/store-announcements?userId=&departmentId=&distributerId=&page=1&pageSize=50`

过滤（**不含** `publisherUserId`）：

- `gb_sa_department_id` = 门店根
- `gb_sa_distributer_id` = 当前集团
- `gb_sa_publish_status = PUBLISHED`

排序：`publishedAt DESC`。

## 5. 详情与删除

`GET /api/store-announcements/{id}?userId=&departmentId=&distributerId=`

`DELETE /api/store-announcements/{id}?userId=&departmentId=&distributerId=`

- 详情：同门店 scope 内已发布公告。
- 删除：软删为 `DELETED`；**仅 `publisherUserId` 可操作**；不影响原 WorkRecord / Pin / Note / Message。

## 6. 响应结构 `StoreAnnouncementResponse`

```json
{
  "announcementId": 1,
  "distributerId": 10,
  "departmentId": 48,
  "publisherUserId": 13,
  "announcementType": "BUSINESS_CARD",
  "sourceType": "WORK_RECORD",
  "sourceId": 100,
  "title": "库存风险：西红柿",
  "textContent": null,
  "cardType": "WAREHOUSE_INVENTORY_RISK_LIST_CARD",
  "cardSnapshotJson": "{...}",
  "cardsSnapshotJson": null,
  "sourceConversationId": 123,
  "sourceMessageId": 456,
  "sourceRunId": 789,
  "sourceItemKey": "goodsId:101",
  "publisherName": "张店长",
  "storeName": "朝阳门店",
  "publishStatus": "PUBLISHED",
  "publishedAt": "2026-06-11T10:00:00",
  "createdAt": "2026-06-11T10:00:00"
}
```

### 前端渲染指引

| announcementType | 渲染方式 |
|------------------|----------|
| `TEXT` | 展示 `title` + `textContent` |
| `BUSINESS_CARD` | 解析 `cardSnapshotJson`，复用现有聊天卡片 renderer（与 message cards 同 wire） |
| `AI_MESSAGE` | 展示 `textContent`；若 `cardsSnapshotJson` 非空，按 message cards 列表渲染 |

## 7. 错误码

| errorCode | 场景 |
|-----------|------|
| `SOURCE_NOT_FOUND` | WorkRecord / Pin / Note 不存在或已软删 |
| `SOURCE_CARD_NOT_FOUND` | 业务卡片来源 message / cardType 不可用 |
| `CONTENT_EMPTY` | 无可发布正文 |
| `ANNOUNCEMENT_NOT_FOUND` | 公告不存在或已删除 |

## 8. 本阶段未实现

- `sourceType=DIRECT` 手工发布公告
- 草稿、审批、定时发布、置顶
- 已读、评论、点赞
- 多门店批量发布

**已实现**：同门店同来源重复发布会**更新已有公告快照**并刷新 `publishedAt`（`announcementId` 不变）；若原公告为 `DELETED` 则恢复为 `PUBLISHED`。

## 9. 与来源对象的关系

| 场景 | 公告是否仍可展示 |
|------|------------------|
| 原 WorkRecord 软删 | 是（快照已保存） |
| 原 WorkRecord 正文修改 | 是（不自动同步） |
| 原 Pin 软删 | 是 |
| 原 Note 软删 | 是 |
| 原 Message / cards 删除 | 是 |
| 公告软删 | 否（列表不可见，详情按 `DELETED` 处理） |
