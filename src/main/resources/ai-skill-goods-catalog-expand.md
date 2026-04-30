# 技能：在已确认的二级分类下，扩充农鑫目录（新增「三级品名」+「四级 SKU」）

## 与库表 `nx_goods` 的对应关系（业务口语 → 库字段）

用户已确认 **一级**（`nx_goods_level=0`）与 **二级**（`nx_goods_level=1`）。接下来要在该二级下**新增两条库记录**：

| 业务说法 | `nx_goods_level` | 含义 |
|----------|-------------------|------|
| 三级（品名父节点） | **2** | 挂在二级下的品类/品名节点（如「青鱼」），尚无具体下单规格 |
| 四级（可下单 SKU） | **3** | 挂在上述品名节点下，带 `level4StandardName`（目录规格，如「斤」） |

## 任务

根据用户填写的 **商品名称**、**规格**、可选 **补充说明**，以及系统给出的 **一级名称、二级名称**，设计：

1. **level3Name**：新增品名父节点显示名（简短、与农批习惯一致，勿含规格单位）。
2. **level4DisplayName**：新增 SKU 的商品名（可与 level3Name 相同或更具体，如「活青鱼」）。
3. **level4StandardName**：目录规格，**必须与用户规格语义一致**（用户写「斤」则填「斤」；勿擅自改成其它单位）。
4. **level4Detail**：可选，一句备注或用途（无则输出空字符串）。

**禁止**输出表中已存在的 `nxGoodsId`；只输出命名与规格文本。

## 输出（严格）

- **只输出一个 JSON 对象**，不要 Markdown 围栏、不要其它文字。
- 字段：

| 字段 | 类型 | 必填 |
|------|------|------|
| `level3Name` | string | 是 |
| `level4DisplayName` | string | 是 |
| `level4StandardName` | string | 是 |
| `level4Detail` | string | 否，可无或 `""` |

## 示例

```json
{"level3Name":"青鱼","level4DisplayName":"青鱼","level4StandardName":"斤","level4Detail":""}
```
