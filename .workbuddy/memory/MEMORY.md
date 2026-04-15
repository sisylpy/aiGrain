# Java 项目迁移(复制)规范

## 迁移背景

将原有的 Java 后端项目（nongxinle-master）中带Gb 开头的实体类、接口、工具类等 项目的批次有用的方法复制至新项目（aigrain），规范接口、工具类和业务逻辑。

## 迁移核心原则

### 1. 保留的核心（必须严格遵守）

- **所有公共工具类和方法必须保持一致**，不可删除或重命名
- **接口定义包括返回的数据内容结构必须保留原项目**，输入输出参数和逻辑必须一致
- **统一包结构和类命名**，确保项目结构清晰
- **数据库表结构和字段必须保持一致**

### 2. 允许优化范围

- 允许重构包结构和类的依赖关系，但**必须保持接口契约不变**
- 允许改进数据库查询语句，但**必须保留所有必要条件和数据完整性**

### 3. 行动步骤

1. 每次迁移前，确保所有工具类和接口未被破坏
2. 复核每个业务逻辑，确保核心流程一致
3. 每次改动前后都提交审查，由用户确认，确保符合整体设计要求

## 迁移检查清单

### Controller 层检查

- [ ] 接口路径（`@RequestMapping`）与原项目完全一致
- [ ] 请求方法（GET/POST）与原项目完全一致
- [ ] 参数名称和类型与原项目完全一致
- [ ] 返回数据结构与原项目完全一致
- [ ] 业务逻辑流程与原项目一致

### Service 层检查

- [ ] 方法签名与原项目完全一致
- [ ] 方法命名与原项目完全一致
- [ ] 返回值类型与原项目完全一致

### Mapper/Dao 层检查

- [ ] SQL 查询条件与原项目一致
- [ ] ResultMap 定义与原项目一致
- [ ] 关联查询（association/collection）与原项目一致
- [ ] 字段别名与原项目一致

### Entity 层检查

- **【重要】所有实体类必须从老项目完整复制，禁止自己猜测字段**
- 数据库字段与实体类属性映射正确
- 非数据库字段（业务计算字段）已添加
- 字段命名规范统一

### ⚠️ 绝对禁止事项

- **【强制】禁止猜想字段名称！** 所有字段名称必须严格按照老项目的原名，不得自己猜测、推断或简化
- **【强制】禁止猜想字段数量！** 不能因为老项目字段多就删减，必须完整复制
- **【强制】禁止猜想字段类型！** 字段类型必须与老项目保持一致，尤其是ID类型（Integer/Long）
- **【强制】禁止猜想SQL列名！** Mapper XML中用到的所有列名必须与数据库实际字段名一致
- **【强制】禁止猜想关联关系！** association/collection 的 property 名称必须与实体类属性名一致

## MyBatis-Plus 表名映射规则

**问题**: MyBatis-Plus 默认将 `XxxEntity` 映射到 `xxx_entity` 表，但实际表名不带 `_entity` 后缀

**解决方案**: 所有实体类必须添加 `@TableName("actual_table_name")` 注解

**自动修复脚本**:
```python
# 将类名转换为表名：去除Entity后缀，转为下划线
def class_name_to_table(name):
    if name.endswith("Entity"):
        name = name[:-6]
    s1 = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', name)
    return re.sub('([a-z0-9])([A-Z])', r'\1_\2', s1).lower()
```

**重要教训**

2026-04-11: 发现新项目实体类大量字段缺失！
- 老项目 GbDepartmentGoodsStockEntity 有 193 行，新项目只有 31 行
- 老项目 GbDepartmentOrdersEntity 有 227 行，新项目只有 32 行
- 老项目 GbDistributerGoodsEntity 有 474 行，新项目只有 56 行
- **迁移原则**: 所有实体类必须从老项目完整复制，不能自己猜测字段数量和名称

2026-04-11: ID字段类型不一致问题
- 老项目：大部分ID用 `Integer` 类型
- 新项目：部分AI实体类用 `Long` 类型
- **原则**:
  1. setter方法如 `setXxxId()` 必须传入参数，不能无参调用
  2. Lombok @Data 自动生成的 setter 需要传入对应类型的参数
  3. 从老项目复制代码时，注意检查方法签名是否匹配
  4. 获取关联ID（如从部门获取分销商ID）：通过 Mapper 查询后再设置

2026-04-11: ResultMap 必须配置 association 映射
- **问题**: SQL 查出了关联表的字段，但 ResultMap 没有配置 association，导致关联对象始终为 null
- **症状**: 前端打印 `gbDistributerGoodsEntity: null`，即使数据库有数据
- **原因**: MyBatis ResultMap 只映射了主表字段，没有配置 `<association>` 标签
- **解决**: 在 ResultMap 中添加 association 映射
  ```xml
  <resultMap id="BaseResultMap" type="...NxGoodsEntity">
      <!-- 主表字段映射... -->
      
      <!-- 分销商商品关联 -->
      <association property="gbDistributerGoodsEntity" javaType="...GbDistributerGoodsEntity">
          <id property="gbDistributerGoodsId" column="gb_distributer_goods_id"/>
          <result property="gbDgGoodsName" column="gb_dg_goods_name"/>
          <!-- 更多字段... -->
      </association>
      
      <!-- 部门分销商品关联 -->
      <association property="gbDepartmentDisGoodsEntity" javaType="...GbDepartmentDisGoodsEntity">
          <!-- ... -->
      </association>
      
      <!-- 部门订单关联 -->
      <association property="gbDepartmentOrdersEntity" javaType="...GbDepartmentOrdersEntity">
          <!-- ... -->
      </association>
  </resultMap>
  ```
- **迁移检查**: 复制复杂查询 SQL 后，必须同步检查 ResultMap 是否包含对应的 association 映射

## ID类型规范

| 实体类 | ID字段 | 类型 |
|:---|:---|:---|
| GbDepartmentEntity | gbDepartmentId | Integer |
| GbDistributerEntity | gbDistributerId | Integer |
| GbDepartmentEntity | gbDepartmentDisId | Integer |
| GbAiConversationEntity | gbAiConversationDistributerId | **Long** (注意！) |
| GbAiConversationEntity | gbAiConversationUserId | Long |

**注意**: AI相关实体的ID可能用Long，其他实体用Integer。调用setter时确保参数类型正确。

## @MapperScan 配置注意

新项目使用 `@MapperScan("com.nongxinle.mapper")`，但老项目迁移来的 `GbDistributerFatherGoodsDao` 等类在 `com.nongxinle.dao` 包里。
**必须同时扫描两个包**：
```java
@MapperScan({"com.nongxinle.mapper", "com.nongxinle.dao"})
```

## Service 接口与 IService 继承规范

### 允许继承 IService 的情况
- 接口中声明的方法都能与 IService 的方法共存（无签名冲突）
- `queryObject(Integer)` 可以用 `default` 方法实现：`default NxGoodsEntity queryObject(Integer id) { return getById(id); }`
- 自定义 `void save(Entity)` / `void update(Entity)` **不能**与 IService 的 `boolean save(T)` / `boolean updateById(T)` 共存

### 推荐做法
- 优先**不继承** IService，直接写自定义接口方法
- 如果要继承 IService，用 `default` 方法包装兼容
- 避免在接口中声明与 IService 同名但不同签名/返回值的方法

## 当前项目结构

- **新项目路径**: `/Users/lpy/Documents/javaWeb/kuangjia/aigrain`
- **老项目路径**: `/Users/lpy/Documents/javaWeb/kuangjia/nongxinle-master`
- **包名**: `com.nongxinle`

## 迁移记录

详见每日工作日志：`/Users/lpy/Documents/javaWeb/kuangjia/aigrain/.workbuddy/memory/YYYY-MM-DD.md`

---

# 补充教训（2026-04-12）

## 问题回顾

**错误**: 修改 GbDistributerFatherGoodsMapper.xml 的 insert 语句时，没有完整复制老项目代码，而是自己"理解"后重写。

**后果**:
1. insert 语句包含了 ID 列，导致 `useGeneratedKeys` 不工作
2. Controller 中访问了新建实体的 `fathersFatherId` 字段（为 null），导致 NullPointerException

**根因**: 规范里只写了"禁止猜想"，但没有规定**具体的复制流程**，导致实际操作时还是自己写了。

## 新的操作规程（强制执行）

### 🔴 Mapper XML 文件迁移

**严禁**自己构建 SQL，必须直接复制：

```
1. 打开老项目对应 Mapper XML 文件
2. 全选 → 复制 → 粘贴到新项目
3. 只修改 namespace 和 type 为新项目的包路径
4. 禁止删除任何列、任何条件、任何注释
5. 禁止重新格式化导致语义变化
```

**检查点**: 复制前后用 `diff` 对比关键语句（特别是 insert 的列名列表）

### 🔴 Controller/Service 业务逻辑迁移

**严禁**选择性复制，必须完整复制：

```
1. 打开老项目对应文件
2. 全选 → 复制 → 粘贴到新项目（同一个方法内的所有代码，包括注释掉的代码）
3. 只修改包名 import 语句
4. 注释掉的代码也要原样复制！注释掉的代码也是代码的一部分
5. 禁止"我觉得这段不需要"而跳过任何代码
```

**检查点**: 复制后对比方法行数，老项目的方法行数必须与新项目一致

### 🔴 自检清单（迁移后必查）

- [ ] Mapper XML 的 insert 语句列数与老项目一致？
- [ ] Mapper XML 的列名顺序与老项目一致？
- [ ] Controller 方法的行数与老项目一致？（含注释）
- [ ] Controller 中所有被注释的代码是否也复制过来了？
- [ ] 如果发现不一致，**立即回退，重新完整复制**

### 🔴 黄金法则

> **"复制粘贴" 而不是 "理解后重写"**
>
> 当你觉得自己"理解了"的时候，就是最容易出错的时候。
> 你的理解可能是错的，但老项目的代码是实际运行过的。

### 🔴 MyBatis-Plus 与自定义 SQL 的关联对象问题

**症状**: `entity.getFatherGoods()` 返回 null，导致 NullPointerException

**根因**: MyBatis-Plus 的 `getById()` 只查询主表，不会自动填充关联对象（association/collection）

**解决方案**: 如果实体类有关联对象，必须使用自定义 SQL + ResultMap 配置 association

**操作流程**:
```
1. 查看老项目的 queryObject SQL（通常包含 LEFT JOIN 多表）
2. 查看老项目的 ResultMap（包含 <association> 和 <collection> 标签）
3. 复制完整的 SQL 和 ResultMap 到新项目的 Mapper XML
4. 在 Mapper 接口中添加 queryObject 方法
5. 在 ServiceImpl 中实现 queryObject，调用 baseMapper.queryObject()
```
