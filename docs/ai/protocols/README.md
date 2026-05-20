# Protocols（轻量协议索引）

本目录存放 **多轮追问 / 锚点 / Harness 观测** 等与路由相关的约定文档，对标「Claude Code 风格」里的稳定契约，而非配置平台。

| 文档 | 说明 |
|------|------|
| [d13-1-supplier-drilldown-closure.md](./d13-1-supplier-drilldown-closure.md) | **D-13.1** 供货商排行 → 商品/单价下钻链路封版说明 |
| [../follow-up-action-protocol.md](../follow-up-action-protocol.md) | `followUpAction`、`detailWanted`、Resolver 判定与 Replay case |
| [../follow-up-drilldown-matrix.md](../follow-up-drilldown-matrix.md) | **D-13 Follow-up Drilldown Matrix**：(1) 新增下钻前先查 **`targetEntityType` + `detailWanted`**；(2) **优先复用**现有 Agent / Tool / AnswerPlan；(3) **仅当矩阵明确为 NEED_TOOL** 时再新增 Tool；(4) 已覆盖 **D-13.1** 供应商下钻、**D-13.2** 门店问题下钻、**D-13.3B** 菜品原料成本下钻。 |
| [../result-anchor-protocol.md](../result-anchor-protocol.md) | `AiResultAnchor`、`resultAnchors`、TurnMemory 持久化前缀 |

扩展门店 / 菜品 / 其它实体下钻时：**新增协议小节或独立 md**，并在本 README 登记。
