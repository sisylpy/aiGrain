# MyBatis-Plus Entity 开发规范

## ⚠️ 迁移前必读（重要）

**每次从其他项目迁移实体类时，必须按照本文档逐项检查！不按规范会导致接口 404、编译失败等问题！**

---

## 背景

在项目开发中，实体类（Entity）与数据库表的映射问题反复出现，本文档总结经验教训，避免后续迁移时重复踩坑。

---

## 一、主键必须添加 @TableId 注解

**问题现象**：
```
BindingException: Invalid bound statement (not found): ... selectById
```

**原因**：实体类的主键字段缺少 `@TableId` 注解，MyBatis-Plus 无法识别哪个是主键。

**解决方案**：每个实体类的主键必须添加 `@TableId` 注解，并指定主键策略。

```java
// 自增主键（最常用）
@TableId(type = IdType.AUTO)
private Integer id;

// 其他策略根据实际需求选择
// IdType.ASSIGN_ID  // 分布式ID
// IdType.ASSIGN_UUID // UUID
// IdType.INPUT      // 手动输入
```

**涉及文件**（已修复）：
- `GbDepartmentDisGoodsEntity.java`
- `GbDepartmentGoodsStockEntity.java`
- `GbDepartmentGoodsStockReduceEntity.java`
- `GbDistributerPurchaseGoodsEntity.java`
- `NxAliasEntity.java`
- `GbDistributerAliasEntity.java`
- `GbReportEntity.java`

---

## 二、非数据库字段必须标记 @TableField(exist = false)

**问题现象**：
```
Unknown column 'xxx' in 'field list'
```

**原因**：实体类中包含了许多非数据库表的字段（如关联实体、计算字段、页面展示字段等），MyBatis-Plus 会尝试将所有字段映射到数据库表。

**解决方案**：所有非数据库字段必须添加 `@TableField(exist = false)` 注解。

```java
// 关联实体（不是数据库字段）
@TableField(exist = false)
private GbDepartmentGoodsStockEntity fromDepStockEntity;

@TableField(exist = false)
private GbDistributerGoodsEntity gbDistributerGoodsEntity;

// 计算字段
@TableField(exist = false)
private Boolean isSelected = false;

@TableField(exist = false)
private BigDecimal totalAmount;

// 页面展示字段（不属于任何表）
@TableField(exist = false)
private String gbDisGoodsFile;

@TableField(exist = false)
private Integer gbDgControlFresh;
```

**常见需要标记的场景**：
1. **关联实体对象**：如 `private GbDepartmentEntity gbDepartmentEntity;`
2. **计算/汇总字段**：如 `private BigDecimal stockTotalAmount;`
3. **Boolean 标记字段**：如 `private Boolean isSelected;`
4. **JSON 序列化字段**：如 `private List<String> goodsImages;`
5. **跨表展示字段**：如属于其他表的字段名写在当前实体中
6. **枚举类型属性**：如果枚举不直接对应数据库值

---

## 三、字段名映射规则

**数据库字段命名**：通常使用下划线命名法
```
gb_department_dis_goods_id
gb_create_time
goods_name
```

**实体类字段命名**：通常使用驼峰命名法
```java
private Integer gbDepartmentDisGoodsId;
private LocalDateTime gbCreateTime;
private String goodsName;
```

MyBatis-Plus 默认会自动转换（驼峰转下划线），无需特殊配置。

**注意**：如果数据库字段名与转换后的名称不一致，需要显式指定：
```java
@TableField("actual_column_name")
private String fieldName;
```

---

## 四、迁移检查清单（⚠️ 必须逐项检查）

> **注意**：本项目使用 MyBatis-Plus，实体类必须满足以下全部条件才能正常工作！

每次新建实体类或从其他项目迁移实体类时，请逐项检查：

### ✅ 必检项（全部满足才能编译通过）

| 检查项 | 说明 | 如果缺失会怎样 |
|--------|------|--------------|
| 主键字段是否有 `@TableId` | 必须指定 `type = IdType.AUTO` | 编译失败 + 运行时 404 |
| 是否有 `@Data` | Lombok 自动生成 getter/setter | 编译失败 |
| 是否有 `@EqualsAndHashCode(callSuper = false)` | 继承父类时必须 | 可能导致问题 |
| Mapper 是否有 `@Mapper` 注解 | 声明为 MyBatis Mapper | Mapper 不生效 |
| Mapper 是否继承 `BaseMapper<T>` | MyBatis-Plus 基础功能 | 部分功能不可用 |
| 非数据库字段是否有 `@TableField(exist = false)` | 否则 MyBatis-Plus 会尝试映射到数据库 | SQL 执行报错 |

### 📝 实体类最小模板

```java
@Data
@TableName("表名")
@EqualsAndHashCode(callSuper = false)
public class XxxEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)  // ⚠️ 主键必须有！
    private Integer id;

    // 数据库字段正常写
    private String name;

    // ⚠️ 非数据库字段必须标记
    @TableField(exist = false)
    private String computedField;
}
```

### 📝 Mapper 最小模板

```java
@Mapper
public interface XxxMapper extends BaseMapper<XxxEntity> {
    // 自定义方法写这里
}
```

---

## ❌ 常见错误对照表

| 错误现象 | 原因 | 解决 |
|---------|------|------|
| 接口 404，后台无日志 | 实体类缺少必要注解 | 添加 `@Data`, `@TableId` 等 |
| `No MyBatis mapper was found` | Mapper 缺少 `@Mapper` 注解 | 添加 `@Mapper` |
| `queryById` 方法找不到 | 主键缺少 `@TableId` | 添加 `@TableId(type = IdType.AUTO)` |
| `Unknown column 'xxx'` | 非数据库字段未标记 `exist = false` | 添加 `@TableField(exist = false)` |

---

## 五、快速排查方法

### 方法1：启动时查看 MyBatis-Plus 日志

启动 Spring Boot 应用时，MyBatis-Plus 会打印 SQL 语句。如果字段有问题，会看到类似输出：

```
Cause: com.mysql.jdbc.exceptions.jdbc4.MySQLSyntaxErrorException: 
Unknown column 'xxx' in 'field list'
```

### 方法2：对比 Entity 和数据库表结构

```sql
-- 查看数据库表结构
DESC table_name;

-- 查看 Entity 源码
-- 逐字段核对
```

### 方法3：启用 MyBatis-Plus SQL 日志

在 `application.yml` 中添加：

```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

### 方法4：检查编译后的 class 文件

如果接口 404 但没有报错，检查 Controller 方法是否被编译：

```bash
javap -c target/classes/com/nongxinle/controller/XxxController.class | grep 方法名
```

如果方法不存在，说明实体类编译失败，热部署没有正确更新。

---

## 🔴 重要提醒：接口 404 排查（必读）

### 问题现象
```
Whitelabel Error Page
There was an unexpected error (type=Not Found, status=404).
No static resource xxx/yyy.
```

**错误解读**：请求被当成静态资源处理，说明 **Controller 根本没有被 Spring 加载**！

---

### 原因1️⃣：应用没有重启（最常见！）

**问题**：修改了代码（Entity、Controller、Service等），但 Spring Boot 没有重启。

**症状**：
- class 文件已经更新（时间戳最新）
- 但接口访问仍然是 404
- 后台无任何日志

**解决**：
> ⚠️ **必须重启 Spring Boot 应用！**
> - 热部署（devtools/JRebel）不可靠，不能完全依赖
> - 手动执行 `mvn clean compile` 后，必须重启应用
> - 重启后再次测试接口

---

### 原因2️⃣：context-path 导致路径错误

**项目配置**（`application.yml`）：
```yaml
server:
  port: 8090
  servlet:
    context-path: /api   # ⬅️ 关键配置！
```

**实际接口路径** = `context-path` + Controller路径 + 方法路径

**示例**：
| Controller 路径 | 方法路径 | 实际访问URL |
|----------------|---------|-----------|
| `/gbdistributerfood` | `/disGetFood/{id}` | `/api/gbdistributerfood/disGetFood/1` |
| `/test` | `/testfood` | `/api/test/testfood` |

---

### 原因3️⃣：Controller 路径前缀不一致

**问题**：部分 Controller 路径缺少前导 `/`，导致路径拼接异常。

**示例**：
```java
// ❌ 错误写法
@RequestMapping("api/gbdistributerfood")

// ✅ 正确写法
@RequestMapping("/gbdistributerfood")
```

**本项目已发现的路径不一致**：
| Controller | 当前路径 | 建议 |
|------------|---------|------|
| GbDistributerFoodController | `api/gbdistributerfood` | `/gbdistributerfood` |
| GbDistributerFoodGoodsController | `api/gbdistributerfoodgoods` | `/gbdistributerfoodgoods` |

---

### 原因4️⃣：Entity 编译失败导致 Controller 未被加载

**问题**：Entity 类缺少必要注解，编译失败，但 IDE 没有及时报错。

**排查方法**：
```bash
# 检查 Controller class 文件中是否包含目标方法
cd target/classes/com/nongxinle/controller
javap -p GbDistributerFoodController.class | grep disGetFood
```

**正常输出**：
```
public com.nongxinle.utils.R disGetFood(java.lang.Integer);
```

**如果方法不存在**：说明 Entity 编译失败，需要检查 Entity 类的注解。

---

### ✅ 404 排查清单

| 步骤 | 检查项 | 操作 |
|-----|-------|------|
| 1 | class 文件是否最新？ | `ls -la target/classes/.../*.class` |
| 2 | class 中方法是否存在？ | `javap -p XxxController.class` |
| 3 | **应用是否已重启？** | **重启应用！！！** |
| 4 | 路径是否正确？ | 对照 context-path + Controller路径 |
| 5 | Controller 路径前缀是否一致？ | 检查是否缺少 `/` |

---

## 六、相关技术栈

- **Spring Boot**：3.2.5
- **MyBatis-Plus**：3.5.6
- **数据库**：MySQL 8
- **Java**：17

---

## 七、参考文档

- [MyBatis-Plus 官方文档 - 注解](https://baomidou.com/pages/223848/)
- [MyBatis-Plus 官方文档 - 主键策略](https://baomidou.com/pages/223848/#idtype)
