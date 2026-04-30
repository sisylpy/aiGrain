# 技能：农鑫商品目录 — 在「已限定分支」的 SKU 候选表内选 `nx_goods_id`

## 背景

服务端已根据 **一级 + 二级分类** 从数据库筛出若干条 **三级 SKU**（`nx_goods_level = 3`、未隐藏）。你收到的 **《候选 SKU 表》** 中的 `nxGoodsId` 均为真实可校验 id。

用户还提供了商品名称、规格与可选补充说明。用户写法常与库里的「名称 / 目录规格」**不完全一致**（俗称、简称、少字多字）。

## 任务

- 在候选表中选 **语义或品类上最接近** 用户输入的一条：名称不必逐字相同（例如用户写「青鱼」，表中可能是「活青鱼」「青鱼段」等）；**优先** `decision=SINGLE` 填 `pickedNxGoodsId`（必须来自候选表）。
- 若有 **2～8 个**都较接近、难以取舍，输出 `decision=AMBIGUOUS`，在 `ambiguousNxGoodsIds` 中列出这些 id（均须来自候选表）。
- **仅当**候选整体与商品明显无关（例如品类完全不对）时输出 `decision=NONE`。**禁止编造**表中不存在的 id。

## 输出要求（严格）

- **只输出一个 JSON 对象**，不要 Markdown 围栏、不要前后解释文字。
- 字段与取值如下（**全部必填**，无值用 `null` 或空数组）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `decision` | string | `SINGLE` \| `AMBIGUOUS` \| `NONE` |
| `pickedNxGoodsId` | number 或 null | 仅当 `decision=SINGLE` 时为正整数 id |
| `ambiguousNxGoodsIds` | number[] | 仅当 `decision=AMBIGUOUS` 时为 2～8 个正整数；否则 `[]` |
| `confidence` | number | 0～1 |
| `reason` | string | 一句简评（供日志） |
| `userFacingSummary` | string | 一句给用户看的自然语言 |

## JSON 示例

```json
{"decision":"NONE","pickedNxGoodsId":null,"ambiguousNxGoodsIds":[],"confidence":0.2,"reason":"候选中无与用户输入一致的 SKU。","userFacingSummary":"当前分类下列表中没有合适项，可换临时商品或补充说明后再试。"}
```
