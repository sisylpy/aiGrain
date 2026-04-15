# 迁移检查报告

## 检查时间
2026-04-11

## 检查范围
1. `depGetApplyAiFather` 接口（GbDepartmentOrdersController）
2. `getPurchaseGoodsGbWithTabCount` 接口（GbDistributerPurchaseGoodsController）

---

## 一、depGetApplyAiFather 接口检查

### 1. Controller 层对比

| 检查项 | 老项目 | 新项目 | 状态 |
|--------|--------|--------|------|
| 接口路径 | `/depGetApplyAiFather/{depFatherId}` | `/depGetApplyAiFather/{depFatherId}` | ✅ 一致 |
| 请求方法 | GET | GET | ✅ 一致 |
| 参数 | `@PathVariable Integer depFatherId` | `@PathVariable Integer depFatherId` | ✅ 一致 |
| 返回结构 | `{arr: [...]}` | `{arr: [...]}` | ✅ 一致 |

### 2. 业务逻辑对比

**老项目逻辑**：
```java
// 1. 查询子部门
List<GbDepartmentEntity> entities = gbDepartmentService.querySubDepartments(depFatherId);

// 2. 有子部门时，遍历每个子部门
for (GbDepartmentEntity dep : entities) {
    Map<String, Object> map1 = new HashMap<>();
    map1.put("status", 3);
    map1.put("depId", dep.getGbDepartmentId());
    map1.put("orderTypeNotEqual", 9);
    List<GbDistributerFatherGoodsEntity> gbDistributerFatherGoodsEntities = 
        gbDepartmentOrdersService.queryGrandGoodsOrder(map1);
    mapDep.put("depOrders", gbDistributerFatherGoodsEntities);
}

// 3. 无子部门时
Map<String, Object> map = new HashMap<>();
map.put("status", 3);
map.put("depFatherId", depFatherId);
map.put("orderTypeNotEqual", 9);
List<GbDistributerFatherGoodsEntity> gbDistributerFatherGoodsEntities = 
    gbDepartmentOrdersService.queryGrandGoodsOrder(map);
```

**新项目逻辑**：与老项目基本一致 ✅

### 3. Mapper 层对比（关键差异）

**老项目 `queryGrandGoodsOrder` SQL**：
- ResultMap: `grandGoodsForAi` 包含嵌套 collection `gbDistributerGoodsEntities`
- 查询字段：包含 `dor.*, ndg.*, ngdf.*, ngds.*` 等所有字段
- 关联表：包含 `gb_department` 等更多关联
- 返回：嵌套结构，包含商品列表

**新项目 `queryGrandGoodsOrder` SQL**：
- ResultMap: `grandGoodsForAi` **缺少**嵌套 collection
- 查询字段：只查询了 `grand` 和 `greatGrand` 的基本字段
- 关联表：缺少 `gb_department` 关联
- 返回：扁平结构

**⚠️ 问题**：新项目的 SQL 是简化版，缺少嵌套 collection `gbDistributerGoodsEntities`，如果前端依赖这个嵌套数据会出问题。

---

## 二、getPurchaseGoodsGbWithTabCount 接口检查

### 1. Controller 层对比

| 检查项 | 老项目 | 新项目 | 状态 |
|--------|--------|--------|------|
| 接口路径 | `/getPurchaseGoodsGbWithTabCount/{disId}` | `/getPurchaseGoodsGbWithTabCount/{disId}` | ✅ 一致 |
| 请求方法 | 未指定（默认 GET） | 未指定（默认 GET） | ✅ 一致 |
| 参数 | `@PathVariable Integer disId` | `@PathVariable Integer disId` | ✅ 一致 |
| 返回结构 | `{arr, orderAmount, wxAmount, disInfo}` | `{arr, orderAmount, wxAmount, disInfo}` | ✅ 一致 |

### 2. 业务逻辑对比

**老项目逻辑**：
```java
// 1. 查询采购商品
Map<String, Object> map4 = new HashMap<>();
map4.put("disId", disId);
map4.put("orderStatus", 3);
map4.put("orderEqualBuyStatus", 0);
map4.put("supplierBuy", -1);
map4.put("purType", 0);
List<GbDistributerPurchaseGoodsEntity> purchaseToday = gbDpgService.querySimplePurGoods(map4);

// 2. 查询订单数量
Map<String, Object> map1 = new HashMap<>();
map1.put("disId", disId);
map1.put("status", 3);
map1.put("equalBuyStatus", 0);
map1.put("notEqualOrderType", 9);
int purCount = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(map1);

// 3. 查询另一个数量
map1.put("equalBuyStatus", null);
map1.put("dayuBuyStatus", 0);
map1.put("dayuStatus", -2);
int purCountOne = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(map1);

// 4. 返回
map3.put("disInfo", gbDistributerService.queryDistributerInfo(disId));
```

**新项目逻辑**：
- 参数 map4 的 key 与老项目一致 ✅
- 参数 map1 的 key 与老项目一致 ✅
- **差异**：新项目使用了 `queryDistributerWithAllDepartments` 替代 `queryDistributerInfo`
  - 这是一个**优化**，因为老项目的 `queryDistributerInfo` 每次都查询6次SQL
  - 新项目拆分为基础查询和完整查询，按需使用

### 3. Mapper 层对比

**老项目 `querySimplePurGoods` SQL**：
- ResultMap: `purchaseGoodsSimple` 包含 `disGoods` 和 `depOrders`
- 查询字段：包含 `gdpg.*, dg.*, gdo.*` 以及 grand/greatGrand 的字段
- 关联表：包含 `gb_department`（ds, df）

**新项目 `querySimplePurGoods` SQL**：
- 与老项目的 SQL 基本一致 ✅
- ResultMap 定义一致 ✅

### 4. Service 层对比

**老项目**：使用 `gbDistributerService.queryDistributerInfo(disId)`
**新项目**：使用 `gbDistributerService.queryDistributerWithAllDepartments(disId)`

**说明**：这是有意为之的优化，因为老项目的 `queryDistributerInfo` 存在性能问题（循环查询6次）。新项目拆分为两个方法，这个接口需要完整部门信息，所以使用 `queryDistributerWithAllDepartments`。

---

## 三、问题汇总

### 🔴 严重问题

1. **`depGetApplyAiFather` 的 `queryGrandGoodsOrder` SQL 不完整**
   - 缺少嵌套 collection `gbDistributerGoodsEntities`
   - 如果前端依赖这个嵌套数据，会导致功能异常
   - **建议**：对比老项目的完整 SQL，补充嵌套查询

### 🟡 优化项（已确认）

1. **`getPurchaseGoodsGbWithTabCount` 使用 `queryDistributerWithAllDepartments`**
   - 这是性能优化，非问题
   - 已确认返回数据结构一致

---

## 四、建议修复

### 需要补充的代码

`GbDepartmentOrdersMapper.xml` 中的 `queryGrandGoodsOrder` 需要补充：

1. 完整的 ResultMap，包含嵌套 collection
2. 完整的查询字段（包括 `dor.*, ndg.*` 等）
3. 完整的关联表查询

建议从老项目的 `GbDepartmentOrdersDao.xml` 中复制完整的 `queryGrandGoodsOrder` 定义。
