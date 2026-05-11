# 集团连锁组织与 AI 对话范围

## 部门类型（`gb_department.gb_department_type`）

与 `com.nongxinle.utils.GbConstants.DepartmentType` 对齐；连锁扩展见 `sql/gb_department_region_type_and_hierarchy.sql`。

| type | 常量 | 说明 |
|------|------|------|
| 0 | GROUP_OFFICE | 总部管理部门 |
| 1 | STORE | 门店（直营） |
| 2 | GROUP_PURCHASE | 集采/采购（集团采购与区域采购共用类型，**靠父部门区分**） |
| 3 | WAREHOUSE | 库房（集团库房与区域库房共用类型，**靠父部门区分**） |
| 4 | CENTRAL_KITCHEN | 中央厨房 |
| 5 | DELIVERY_SUPPLIER | 配送商 |
| 11 | FRANCHISE | 加盟门店 |
| 12 | REGION | 片区 / 区域管理单元（组织根节点，非经营门店） |

## 用户端角色（`gb_department_user` 等）

`GbConstants.DepartmentUserRole` 在原集团/门店/库房等基础上增加：`REGION_MANAGER_APP`(51)、`REGION_PURCHASER_APP`(52)、`REGION_WAREHOUSE_APP`(53)。入库前须与现有后台枚举约定一致。

## AI 会话范围

- **单店 / 片区 subtree**：`scopeMode=STORE`，`departmentId`=门店父部门 ID 或**片区根部门 ID**；统计范围为该节点子树 ∩ 用户权限子树。
- **集团全量**：`scopeMode=GROUP`，`distributerId`= disId；统计范围为该 dis 下全部部门 ID ∩ 用户权限子树。
- **门店级营收/排行/集团毛利**：事实查询在「解析后的部门列表」上**过滤直营+加盟门店**（`STORE`、`FRANCHISE`），避免库房/厨房等节点混入排行。

## 集团经营诊断口径（当前实现）

- **门店净营收**：`堂食+外卖−平台抽成`，按 `gb_ai_daily_revenue` 汇总。
- **集团零售毛利率（估算）**：在门店父部门集合上，分子 = 上述净营收 − 出库成本小计（`gb_department_goods_stock_reduce` 四类 type 金额之和，与现有 reduce 统计一致），分母 = 净营收。与财务完整毛利可能不一致，回答中须提示「估算/需财务确认」。
- **记忆与画像锚点**：使用登录用户主部门 ID（`userMemoryAnchorDepartmentId`），不再在集团会话下使用「resolved 列表首部门」。
