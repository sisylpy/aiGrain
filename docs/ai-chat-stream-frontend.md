# AI 对话 SSE 流式接口（前端对接说明）

本文说明 **打字机 / 流式展示** 如何对接后端 `POST /ai/chat/stream`。可与非流式 `POST /ai/chat/send` 二选一；流式接口在服务端会尽量 **按模型输出增量** 推送「用户可见正文」，特殊情况下会推送 **整段替换**。

---

## 1. 接口概览

| 项目 | 说明 |
|------|------|
| **Method** | `POST` |
| **Path** | `{baseUrl}/ai/chat/stream`（与项目全局 context-path 拼接，如 `/api/ai/chat/stream`） |
| **Query** | `conversationId`（Long，必填） |
| **Content-Type** | `application/json` |
| **Accept** | 可写 `text/event-stream` 或 `*/*`（以服务端响应头为准） |
| **响应** | **SSE**；**必须**按 **UTF-8** 解码（见 **§4**） |

**响应头**：服务端声明为 `Content-Type: text/event-stream;charset=UTF-8`。若缺少 `charset`，部分环境会把正文按 Latin-1 解，导致中文变成 `é'±`、`å¤šå¤š` 等乱码。

**请求体 JSON**（与 `/send` 相同）：

```json
{
  "message": "用户输入的文本",
  "userId": 123,
  "sourceTopicId": "可选，话题卡片埋点用"
}
```

- `message`：必填（业务上与现网一致）。
- `userId`：建议与创建会话时一致；若省略，服务端会尝试用会话上的用户兜底（与 `/send` 行为一致）。
- `sourceTopicId`：可选，仅日志/埋点。

**创建会话**仍使用：`POST /ai/chat/conversation`（返回值中带 `gbAiConversationId` 等，用作 `conversationId`）。

---

## 2. 为何不能只用 `EventSource`

标准 **`EventSource` 只支持 GET**，无法携带本接口的 **POST + JSON body**。请使用：

- **浏览器 / H5**：`fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(...) })`，再 **`response.body.getReader()`** 按字节/文本解析 SSE；或可用封装库（如支持 POST SSE 的客户端）。
- **微信小程序**：使用 `wx.request` **`enableChunked: true`**（若基础库支持）或项目内已封装的流式请求能力，按 chunk 解析下文格式。

---

## 3. SSE 事件约定

服务端通过 **命名事件** 推送（Spring `SseEmitter`：`event` name + `data`）。

| 事件名 (`event`) | `data` 含义 | 前端处理 |
|------------------|-------------|----------|
| **`delta`** | 当前轮 **用户可见正文** 的 **新增片段**（UTF-8 文本） | 将本轮助手气泡文案 **追加** `data`（在已有字符串后面拼接） |
| **`message`** | **完整**用户可见正文 | **不要追加**：用 `data` **整段替换**当前助手气泡内容 |
| **`done`** | 固定为字符串 `[DONE]`（语义化结束标记） | 结束加载态；可与历史列表刷新策略配合（服务端仍会照常落库助手消息） |
| **`error`** | 简短错误文案（字符串） | 提示用户；结束加载态 |

### 3.1 为何会有 `delta` 又有 `message`

- 正常流式：`delta` 多次，每一段都是「截去机器块之后、相对上一可见串多出来的后缀」，前端 **只追加** 即可得到与后端一致的可见正文。
- **整段替换**发生在例如：可见串因规则变化 **不能**再继续当作「前缀延伸」（如截掉「数据完整性」声明段后变短），或 **技能 handoff** 后服务端又做了一轮修订，最终正文与首轮流式拼接结果 **不一致**。此时会发 **`message`**，应用 **`message` 覆盖**当前展示，不要再保留之前拼的 `delta`。

**推荐状态机（简化）：**

1. 用户发消息后，新建「本轮助手气泡」，内容置空，`assistantBuffer = ''`。
2. 收到 **`delta`**：`assistantBuffer += data`，刷新 UI。
3. 收到 **`message`**：`assistantBuffer = data`（覆盖），刷新 UI。
4. 收到 **`done`**：收尾（节流 flush、关 loading）。
5. 收到 **`error`**：提示 + 关 loading。

同一轮里：**`message` 优先级高于继续拼 `delta`**（收到 `message` 后以最新 `message` 为准）。

### 3.2 固定成本门禁等非模型流式路径

少数场景不走主模型流式，服务端可能 **只发一条 `message` + `done`**，无 `delta`。前端按上面状态机仍可兼容。

---

## 4. 编码与多行 `data:`（乱码、多行必看）

### 4.1 编码（乱码排查）

- **现象**：`delta` / `message` 里中文变成类似 `é'±`、`å¤šå¤š`、`â€"`（全角破折号被错解）等，多半是 **UTF-8 被当成 ISO-8859-1** 读了。
- **后端**：已使用 `Content-Type: text/event-stream;charset=UTF-8`。联调时在开发者工具 **Network → 该请求 → Response Headers** 确认带 `charset=UTF-8`。
- **前端**：读流式 body 时 **按 UTF-8 解码**（例如 `TextDecoder('utf-8')` 累加 `Uint8Array`；微信小程序对 chunk 拼接后切勿按「二进制默认 Latin-1」当字符串用）。

### 4.2 同一条事件里的多行 `data:`

SSE 规定：一个事件里可以有多行 `data:`，语义上是 **同一条 payload**，行与行之间要用 **一个换行符 `\n`** 拼成最终字符串（再以空行结束该事件）。

Spring 在 `data` 字符串里含有 **`\n`** 时，会拆成多行 `data:` 写出。例如 `message` 事件下连续很多行 `data:` **属于同一个 `message`**，前端解析器必须 **先合并** 再整段替换 UI，不能把每一行 `data:` 当成一次独立的「增量」。

---

## 5. SSE 原始帧格式（自解析时参考）

典型片段如下（`\n\n` 分隔事件）：

```http
event:delta
data:钱多多老师

event:delta
data:直接看数据。

event:message
data:钱多多老师……（整段修订稿）

event:done
data:[DONE]
```

解析时注意：

- `data:` 后整行即负载；**同一事件内**若有多行 `data:`，必须先 **用 `\n` 拼成一条** 再交给 UI（见 §4.2）；**`message` 含换行时很常见**。
- 忽略不识别的 `event` 时可记录日志，避免静默丢事件。

---

## 6. 微信小程序与性能

- **`setData` 过频会卡**：可对 `assistantBuffer` 做 **节流**，例如每 **50～100ms** 合并多次 `delta` 再 `setData`；收到 **`message`** 或 **`done`** 时 **立即 flush**。
- 长文注意 **单次 `setData` 体积**；可按需只 `setData` 当前在视口中的摘要（视产品而定）。

---

## 7. 网关与运维

- 反代（Nginx 等）需 **禁用对 SSE 的长响应缓冲**，否则前端很晚才收到 chunk。
- 服务端单连接超时（如 `SseEmitter` 约 **120s**）内应能完成一轮；弱网需前端超时与重试策略（与产品确认是否允许重复发送同一条用户消息）。

---

## 8. 与 `/ai/chat/send` 的差异

| | `/send` | `/stream` |
|---|---------|-----------|
| 响应 | 单次 JSON（如 `R` 包一层 `data` 为消息实体） | SSE 流 |
| 打字机 | 只能前端假打字（全文到达后本地动画） | 可用 **真增量**（`delta`） |
| 参数 | 相同 `conversationId` + body | 相同 |

历史记录仍用：`GET /ai/chat/history/{conversationId}`，与哪种发送方式无关。

---

## 9. 联调检查清单

- [ ] `POST`，Query 带 `conversationId`，Body 为 JSON。
- [ ] 响应头为 **`Content-Type: text/event-stream;charset=UTF-8`**，中文无乱码。
- [ ] 解析 `event`：`delta` 追加、`message` 覆盖（含 **多行 `data:` 合并**）、`done`/`error` 收尾。
- [ ] handoff 场景下收到 **`message`** 后能替换首轮拼好的内容。
- [ ] 小程序节流 + `done` 立刻 flush。
- [ ] 网关不缓冲 SSE。

---

*文档与后端 `GbAiChatController` / `GbAiChatServiceImpl#callDeepSeekSSE` 行为对齐；若后端协议变更，以 Swagger / 代码注释为准。*
