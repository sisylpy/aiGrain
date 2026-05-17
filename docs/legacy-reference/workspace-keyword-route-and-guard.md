# 工作台关键词路由与 `AiWorkspaceAccessGuard`（已移除）

**删除时间**：2026-05-17 起代码库已移除下列类（replay bundle 通过后清理）：

| 类 | 原职责 |
|----|--------|
| `BusinessWorkspaceRouteNode` | Graph 顶点；调用 `WorkspaceRouterService` 从话术匹配「报表 / 营销 / 任务」等关键词，设置 `workspaceMode` |
| `WorkspaceRouterService` | `q.contains(...)` 式关键词主路由 |
| `AiWorkspaceAccessGuard` | 校验 `ACCESS_MARKETING_WORKSPACE` 等；缺权时 SSE **`WORKSPACE_ACCESS_DENIED`** |
| `BusinessFollowUpIntentResolveNode` | 曾调用已删除类上的 `applyIfFollowUp`（曾长期 no-op）；追问由 **`AiResolvedQueryContextResolver` + conversation memory** 处理 |

**快照与 hint**：**`AiFollowUpIntentSnapshotSupport.snapshotFromCompletedState`**（**`AiRunService`**）、**`AiFollowUpHintSupport`**（**`AiResolvedQueryContextResolver`**）。旧 **`FollowUpIntentResolveService`**（含 Java 时间 regex 扩写）已删除。

**当前主链**：**`AiBusinessGraphConfig#businessAgentNodes`** 以 **`BusinessScopeIntersectNode`** 打头；无独立 Workspace 路由节点。权限以 **`AiPermissionGuard`**（Tool / 专线）为准。

**文档**：**`WORKSPACE_ACCESS_DENIED`** 的 JSON 示例仍保留在 **`docs/SSE_BACKEND_EVENT_CONTRACT.md`** 作契约参考，但运行时 **不再**由已删 Guard 产生。
