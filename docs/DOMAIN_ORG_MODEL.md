> 所有涉及 AI 多智能体、经营分析、采购、库存、菜品毛利、权限、组织范围、时间范围、多轮追问的改动，必须先阅读 `docs/AI_AGENT_DEVELOPMENT_GUIDE.md`。

# 组织与部门领域模型说明

## 1. 核心结论

本项目里不要把 `departmentId` 简单理解成「门店 ID」。

本项目的组织关系是：

```text
distributerId = 集团 / 配送商 / 组织主体 ID

gb_department 表里：
- gbDepartmentFatherId = 0 的记录，表示一个门店
- 每个门店下面固定有一个子部门
- 子部门的 gbDepartmentFatherId = 门店的 gbDepartmentId
```

也就是说：

```text
distributerId
  └── 门店 Department（gbDepartmentFatherId = 0）
        └── 子部门 Department（gbDepartmentFatherId = 门店 gbDepartmentId）
```

## 2. 重要字段含义

### distributerId

```text
distributerId 是集团 / 配送商 / 组织主体 ID。
```

在集团经营概览、集团权限、集团门店列表查询时，不能把 `departmentId` 当成集团 ID。

集团范围应该优先从：

```text
distributerId
```

向下找到该集团下面的门店。

### departmentId

```text
departmentId 是 gb_department 表里的记录 ID。
```

它可能是：

```text
1. 门店 ID
2. 门店下面的子部门 ID
```

不能不判断 fatherId 就直接当成门店。

### gbDepartmentFatherId

```text
gbDepartmentFatherId = 0
```

表示这条 department 是门店。

```text
gbDepartmentFatherId = 某个门店 departmentId
```

表示这条 department 是该门店下面的子部门。

## 3. 判断一条 department 是不是门店

判断规则：

```text
gbDepartmentFatherId == 0
```

这条就是门店。

伪代码：

```java
boolean isStoreDepartment(GbDepartmentEntity dep) {
    return dep.getGbDepartmentFatherId() != null
        && dep.getGbDepartmentFatherId() == 0;
}
```

## 4. 判断一条 department 所属门店

如果当前 department 的 `gbDepartmentFatherId = 0`：

```text
当前 department 本身就是门店
storeDepartmentId = departmentId
```

如果当前 department 的 `gbDepartmentFatherId != 0`：

```text
当前 department 是子部门
storeDepartmentId = gbDepartmentFatherId
```

伪代码：

```java
Long resolveStoreDepartmentId(GbDepartmentEntity dep) {
    if (dep.getGbDepartmentFatherId() == 0) {
        return dep.getGbDepartmentId();
    }
    return dep.getGbDepartmentFatherId();
}
```

## 5. 查询集团下所有门店

集团 / 配送商下面的所有门店，应该按 `distributerId` 查：

```text
查询 gb_department
where gbDepartmentDistributerId = distributerId
and gbDepartmentFatherId = 0
```

这些记录才是门店列表。

不要只根据某个 `departmentId` 的子树来判断集团所有门店。

## 6. 集团经营概览的正确逻辑

集团管理端问：

```text
这个月经营怎么样？
```

正确流程应该是：

```text
1. 根据 userId 解析出 distributerId
2. 根据 distributerId 查询所有门店：
   gbDepartmentFatherId = 0
3. 得到 visibleStores
4. 对每个门店找到用于日营收 / 经营看板统计的口径
5. 汇总有数据门店
6. 没有日营收的门店进入 dataMissingStores
```

不能只做：

```text
拿当前 departmentId 去查一个子树
```

否则集团下面有多个门店时，会漏门店。

## 7. 门店店长经营概览的正确逻辑

门店店长问：

```text
这个月经营怎么样？
```

应该只查本人所属门店。

如果登录用户当前 departmentId 是门店：

```text
storeDepartmentId = departmentId
```

如果登录用户当前 departmentId 是子部门：

```text
storeDepartmentId = gbDepartmentFatherId
```

然后只查这个门店，不查其他门店。

## 8. 门店与子部门的关系

每个门店下面固定有一个子部门。

例如：

```text
门店 A：
departmentId = 10
gbDepartmentFatherId = 0

门店 A 的子部门：
departmentId = 11
gbDepartmentFatherId = 10
```

所以当接口传入 `departmentId = 11` 时，它不代表另一个门店，而是门店 A 的子部门。

系统应该自动归一化到：

```text
storeDepartmentId = 10
```

## 9. coveredStores / dataMissingStores 的口径

集团经营概览里需要分清：

### visibleStores

```text
集团 / 当前权限范围内所有应该可见的门店
来源：distributerId + gbDepartmentFatherId = 0
```

### coveredStores

```text
本次有日营收数据、参与汇总的门店
```

### dataMissingStores

```text
visibleStores 中没有日营收数据的门店
```

所以：

```text
visibleStores = coveredStores + dataMissingStores
```

不能把：

```text
有日营收的门店数量
```

当成：

```text
集团门店总数
```

## 10. 典型错误

### 错误 1：把 distributerId 当成 departmentId

错误：

```text
departmentId = distributerId
```

这是错的。

正确：

```text
distributerId 是集团 / 配送商 ID
departmentId 是部门表 ID
```

### 错误 2：集团查询只查当前 departmentId 子树

错误：

```text
GROUP_MANAGER 查询经营概览 → 只用 request.departmentId 找子树
```

正确：

```text
GROUP_MANAGER 查询经营概览 → 根据 distributerId 查询所有 gbDepartmentFatherId = 0 的门店
```

### 错误 3：把子部门当门店

错误：

```text
departmentId 不是 0 fatherId，也当成一个门店
```

正确：

```text
如果 fatherId != 0，它是子部门，所属门店是 fatherId 指向的 department
```

### 错误 4：只显示有日营收门店数量

错误：

```text
本期识别到 1 家门店有日营收数据，暂无缺失门店
```

如果集团下实际有 2 家门店，其中 1 家没有日营收，这句话就是错的。

正确：

```text
集团范围内共 2 家门店，其中 1 家有日营收数据，1 家暂无日营收记录。
```

## 11. 给 AI Agent 的强制规则

以后任何代码涉及以下内容，必须先阅读本文件：

```text
组织范围
门店列表
集团经营概览
门店经营概览
权限范围
ScopeIntersect
AiOrgScope
RevenueQueryTool（现网 MULTI 经营收入域；Historical removed：BusinessOverviewQueryTool）
coveredStores
dataMissingStores
attentionStores
```

强制规则：

```text
1. distributerId 是集团 / 配送商主体 ID
2. gbDepartmentFatherId = 0 的 department 是门店
3. fatherId != 0 的 department 是子部门
4. 子部门要归一化到所属门店
5. 集团范围门店列表必须从 distributerId 查 gbDepartmentFatherId = 0
6. visibleStores 不能等于 coveredStores
7. dataMissingStores 必须来自 visibleStores - coveredStores

8. 集团用户的 departmentId 通常是管理部门，不是门店。所有集团范围查询（经营概览、菜品毛利、库存、采购、报表）都必须优先根据 distributerId 找集团下 gbDepartmentFatherId=0 的门店列表再汇总；不允许直接把集团用户的 departmentId 当作门店 ID。
```

## 12. 集团用户在库存 / 毛利 / 经营 / 采购 / 报表场景的统一口径（强制）

集团用户查询库存、菜品毛利、经营、采购、报表时，都不能使用当前 `departmentId` 当作门店 ID。必须优先根据 `distributerId` 找出集团下 `gbDepartmentFatherId = 0` 的门店及相关库房范围，再做汇总。只有门店角色和库房角色才默认收敛到本人所属门店或库房。

库存开放式问法（例如「现在库存怎么样？」）在集团管理账号下必须默认给出集团范围汇总（门店根逐店聚合），禁止苏格拉底式反问「您指的是哪家门店」「想看哪些品类」，除非用户是普通门店角色，或集团用户点名某一门店/品类。
