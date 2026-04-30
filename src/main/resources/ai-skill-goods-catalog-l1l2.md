# 技能：农鑫商品目录 — 一级 / 二级分类选择（仅 id，不含 SKU）

## 数据含义（与库表 `nx_goods` 一致）

- **一级分类**：`nx_goods_level = 0`，全库约十余条顶层大类（如「海鲜水产」「新鲜蔬菜」）。每条有唯一的 `nx_goods_id`。
- **二级分类**：`nx_goods_level = 1`，其父节点为某条一级：`nx_goods_father_id` = 该一级的 `nx_goods_id`。例如一级「海鲜水产」下可有「鲜活鱼」「冻鱼」等二级。

你**不会**看到三级 SKU 表；只需根据用户商品名称、规格与补充说明，判断应归入哪条一级、哪条二级。

## 任务

1. 阅读系统消息中的 **《一级分类表》《二级分类表》**（均为真实库 id）。
2. 选择**恰好一条**一级 + **恰好一条**二级，且该二级的 `parentGreatGrandNxGoodsId` 必须等于你所选一级的 id。
3. 若只能在**同一一级**下在 **2～6 条二级** 之间犹豫，可输出 `decision=AMBIGUOUS`，并列出 `ambiguousGrandNxGoodsIds`（均为二级 id）；`greatGrandNxGoodsId` 填该一级 id；`grandNxGoodsId` 置 `null`。
4. 若无法判断，输出 `decision=NONE`。**禁止编造**表中不存在的 id。

## 输出（严格）

- **只输出一个 JSON 对象**，不要 Markdown 围栏、不要其它文字。
- 字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `decision` | string | `SINGLE` \| `AMBIGUOUS` \| `NONE` |
| `greatGrandNxGoodsId` | number 或 null | 一级 id；`NONE` 时可 null |
| `grandNxGoodsId` | number 或 null | 二级 id；仅 `SINGLE` 时必填 |
| `ambiguousGrandNxGoodsIds` | number[] | 仅 `AMBIGUOUS`：2～6 个二级 id；否则 `[]` |
| `confidence` | number | 0～1 |
| `reason` | string | 一句（供日志） |
| `userFacingSummary` | string | 一句给用户 |

## 示例

```json
{"decision":"NONE","greatGrandNxGoodsId":null,"grandNxGoodsId":null,"ambiguousGrandNxGoodsIds":[],"confidence":0.2,"reason":"输入过泛。","userFacingSummary":"未能对应到明确大类，请补充用途或品类后再试。"}
```
