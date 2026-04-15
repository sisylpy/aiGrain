# Aigrain API 接口文档

> 基于 Swagger3 自动生成
> 访问地址: http://localhost:8090/api/swagger-ui/index.html

---

## 目录

- [部门商品管理](#部门商品管理)
- [农鑫商品管理](#农鑫商品管理)
- [农鑫商品规格](#农鑫商品规格)

---

## 部门商品管理

### 1. 获取部门商品分类
- **路径**: `/gbdepartmentdisgoods/depGetDepGoodsCataGb`
- **方法**: POST
- **用途**: 获取指定部门关联的批发商商品分类树，以及该部门已选择的商品ID列表

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| depId | Integer | 是 | 部门ID |
| disId | Integer | 是 | 批发商ID |

**响应示例**:
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "cataArr": [
      {
        "gbDistributerFatherGoodsId": 1,
        "gbDfgFatherGoodsName": "蔬菜类",
        "gbDfgFatherGoodsImg": "/images/vegetable.jpg",
        "fatherGoodsEntities": []
      }
    ],
    "depGoodsArr": [1, 2, 3, 4, 5]
  }
}
```

---

### 2. 分页获取部门商品列表
- **路径**: `/gbdepartmentdisgoods/depGetDepGoodsGbPage`
- **方法**: POST
- **用途**: 分页查询指定部门关联的商品列表，返回商品详情和分页信息

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| depId | Integer | 是 | 部门ID |
| limit | Integer | 是 | 每页数量 |
| page | Integer | 是 | 当前页码 |

**响应示例**:
```json
{
  "code": 0,
  "msg": "success",
  "page": {
    "totalCount": 100,
    "pageSize": 20,
    "totalPage": 5,
    "currPage": 1,
    "list": [
      {
        "gbDepartmentDisGoodsId": 1,
        "gbDdgDisGoodsId": 100,
        "gbDdgDisGoodsName": "大白菜"
      }
    ]
  }
}
```

---

## 农鑫商品管理

### 3. 获取商品分类树
- **路径**: `/nxgoods/gbDepGetNxCataGoods`
- **方法**: POST
- **用途**: 获取农鑫商品的一级和二级分类树结构，以及一级分类下包含的所有商品ID列表

**请求参数**: 无

**响应示例**:
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "cataArr": [
      {
        "nxGoodsId": 1,
        "nxGoodsName": "新鲜蔬菜",
        "nxGoodsFile": "goodsImage/新鲜蔬菜.jpg",
        "nxGoodsEntityList": [
          {
            "nxGoodsId": 101,
            "nxGoodsName": "叶花菜",
            "nxGoodsFile": "goodsImage/叶花菜.jpg"
          }
        ]
      }
    ],
    "depGoodsArr": [1001, 1002, 1003]
  }
}
```

---

### 4. 按分类分页查询商品
- **路径**: `/nxgoods/gbDepGetNxFatherGoods`
- **方法**: POST
- **用途**: 根据指定的一级分类ID，分页查询该分类下的所有商品列表

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| depId | Integer | 是 | 部门ID |
| fatherId | Integer | 是 | 一级分类ID |
| limit | Integer | 是 | 每页数量 |
| page | Integer | 是 | 当前页码 |
| disId | Integer | 是 | 批发商ID |

**响应示例**:
```json
{
  "code": 0,
  "msg": "success",
  "page": {
    "totalCount": 50,
    "pageSize": 20,
    "totalPage": 3,
    "currPage": 1,
    "list": [...]
  }
}
```

---

## 农鑫商品规格

### 5. 查询商品规格列表
- **路径**: `/nxstandard/list/{nxGoodsId}`
- **方法**: GET
- **用途**: 根据商品ID查询该商品下的所有规格记录

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| nxGoodsId | Integer | 是 | 商品ID |

**响应示例**:
```json
{
  "code": 0,
  "msg": "success",
  "data": [
    {
      "nxStandardId": 1,
      "nxStandardName": "500g",
      "nxStandardScale": "1:1",
      "nxStandardWeight": 500
    }
  ]
}
```

---

### 6. 获取规格详情
- **路径**: `/nxstandard/info/{nxStandardId}`
- **方法**: GET
- **用途**: 根据规格ID获取单个规格的详细信息

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| nxStandardId | Integer | 是 | 规格ID |

**响应示例**:
```json
{
  "code": 0,
  "msg": "success",
  "nxStandard": {
    "nxStandardId": 1,
    "nxStandardName": "500g",
    "nxStandardScale": "1:1",
    "nxStandardWeight": 500
  }
}
```

---

### 7. 新增规格
- **路径**: `/nxstandard/saveNxStandard`
- **方法**: POST
- **用途**: 创建新的商品规格记录

**请求体**:
```json
{
  "nxSGoodsId": 100,
  "nxStandardName": "500g",
  "nxStandardScale": "1:1",
  "nxStandardWeight": 500
}
```

**响应示例**:
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "nxStandardId": 1,
    "nxStandardName": "500g"
  }
}
```

---

### 8. 更新规格
- **路径**: `/nxstandard/updateStandard`
- **方法**: POST
- **用途**: 根据规格ID更新规格信息

**请求体**:
```json
{
  "nxStandardId": 1,
  "nxStandardName": "1kg",
  "nxStandardWeight": 1000
}
```

---

### 9. 删除规格
- **路径**: `/nxstandard/deleteStandard/{id}`
- **方法**: POST
- **用途**: 根据规格ID删除指定的商品规格

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Integer | 是 | 规格ID |

---

## 统一响应格式

所有接口统一使用以下响应格式：

```json
{
  "code": 0,         // 状态码: 0=成功, 其他=失败
  "msg": "success",   // 消息
  "data": {},        // 数据
  "page": {}         // 分页数据(可选)
}
```

### 分页响应格式

```json
{
  "totalCount": 100,   // 总记录数
  "pageSize": 20,      // 每页数量
  "totalPage": 5,      // 总页数
  "currPage": 1,       // 当前页
  "list": []           // 数据列表
}
```

---

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 0 | 成功 |
| 500 | 服务器内部错误 |
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 401 | 未授权 |

---

---
## AI日营业额管理

### 10. 获取营业额统计
- **路径**: `/ai/daily-revenue/stats/{departmentId}`
- **方法**: GET
- **用途**: 获取餐厅营业额统计，包含日均营业额、固定开支、成本支出、毛利率、盈亏状态

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| departmentId | Long | 是 | 部门/餐厅ID |

**响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "stats": {
      "days": 30,
      "avgDailyRevenue": 12800.50,
      "totalRevenue": 384015.00,
      "avgOrderCount": 150.20,
      "avgPerCustomer": 85.20,
      "totalCouponAmount": 5000.00,
      "totalRefundAmount": 1200.00,
      "maxDailyRevenue": 18000.00,
      "minDailyRevenue": 8500.00,
      "avgFixedCost": 1666.67,
      "monthlyWage": 30000.00,
      "monthlyRent": 20000.00,
      "avgNetRevenue": 11333.83,
      "totalTakeoutRevenue": 255000.00,
      "avgTakeoutRevenue": 8500.00,
      "totalTakeoutNet": 229500.00,
      "avgTakeoutNet": 7650.00,
      "produceCost": 50000.00,
      "wasteCost": 3000.00,
      "lossCost": 2000.00,
      "returnCost": 5000.00,
      "productionCost": 55000.00,
      "totalCost": 60000.00,
      "grossProfitMargin": 84.37,
      "grossProfitMarginPercent": "84.37%",
      "breakEvenPoint": 1666.67,
      "profitAmount": 9667.16,
      "profitAfterCost": 4167.16,
      "actualProfit": 4167.16,
      "status": "profit",
      "statusDesc": "盈利中"
    },
    "profile": {
      "gbAiRestaurantProfileId": 1,
      "gbAiRestaurantProfileDepartmentId": 123,
      "gbAiRestaurantProfileMonthlyWage": 30000.00,
      "gbAiRestaurantProfileRentMonthly": 20000.00
    }
  }
}
```

---

### 11. 获取日营业额完整数据
- **路径**: `/ai/daily-revenue/list/{departmentId}`
- **方法**: GET
- **用途**: 获取指定餐厅的日营业额完整数据，包含统计数据、曲线图数据、每日详情列表

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| departmentId | Long | 是 | 部门/餐厅ID |

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| startDate | String | 否 | 开始日期（格式：yyyy-MM-dd） |
| endDate | String | 否 | 结束日期（格式：yyyy-MM-dd） |

---

### 12. 保存单条日营业额
- **路径**: `/ai/daily-revenue/save`
- **方法**: POST
- **用途**: 保存单条日营业额记录

**请求体**:
```json
{
  "gbAiDailyRevenueDepartmentId": 123,
  "gbAiDailyRevenueDistributerId": 456,
  "gbAiDailyRevenueRecordDate": "2024-03-20",
  "gbAiDailyRevenueDineInRevenue": 12500.50,
  "gbAiDailyRevenueDineInOrders": 156,
  "gbAiDailyRevenueDineInCustomers": 120,
  "gbAiDailyRevenueTakeoutRevenue": 8500.00,
  "gbAiDailyRevenueTakeoutOrders": 85,
  "gbAiDailyRevenuePlatformFee": 850.00,
  "gbAiDailyRevenueWeekday": 3,
  "gbAiDailyRevenueHoliday": "",
  "gbAiDailyRevenueNotes": ""
}
```

---

### 13. 批量保存日营业额
- **路径**: `/ai/daily-revenue/save-batch`
- **方法**: POST
- **用途**: 批量保存多条日营业额记录

**请求体**:
```json
[
  {
    "gbAiDailyRevenueDepartmentId": 123,
    "gbAiDailyRevenueDistributerId": 456,
    "gbAiDailyRevenueRecordDate": "2024-03-20",
    "gbAiDailyRevenueDineInRevenue": 12500.50,
    "gbAiDailyRevenueDineInOrders": 156,
    "gbAiDailyRevenueDineInCustomers": 120,
    "gbAiDailyRevenueTakeoutRevenue": 8500.00,
    "gbAiDailyRevenueTakeoutOrders": 85,
    "gbAiDailyRevenuePlatformFee": 850.00,
    "gbAiDailyRevenueWeekday": 3,
    "gbAiDailyRevenueHoliday": "",
    "gbAiDailyRevenueNotes": ""
  },
  {
    // 第二条记录...
  }
]
```

---

### 14. Excel上传批量保存日营业额
- **路径**: `/ai/daily-revenue/upload-excel`
- **方法**: POST
- **用途**: 通过Excel文件上传批量保存日营业额记录
- **Content-Type**: `multipart/form-data`

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| file | File | 是 | Excel文件（.xls 或 .xlsx格式） |
| departmentId | Long | 是 | 部门ID（餐厅ID） |
| distributerId | Long | 是 | 分配者ID |

**Excel格式说明**:
| 列 | 字段 | 类型 | 必填 | 说明 |
|----|------|------|------|------|
| 1 | 日期 | String | 是 | 格式：yyyy-MM-dd |
| 2 | 堂食营业额 | Number | 是 | 单位：元，支持小数 |
| 3 | 堂食订单数 | Integer | 是 | 整数 |
| 4 | 堂食顾客数 | Integer | 是 | 整数 |
| 5 | 外卖营业额 | Number | 是 | 单位：元，支持小数 |
| 6 | 外卖订单数 | Integer | 是 | 整数 |
| 7 | 平台抽成 | Number | 是 | 单位：元，支持小数 |
| 8 | 备注 | String | 否 | 备注信息 |

**注意**:
- **星期几**：由系统根据日期自动计算，无需填写
- **节假日**：由系统自动处理，模板中已移除该字段

**Excel示例**:
```
日期,堂食营业额,堂食订单数,堂食顾客数,外卖营业额,外卖订单数,平台抽成,备注
2024-03-20,12500.50,156,120,8500.00,85,850.00,天气好
2024-03-21,9800.00,120,95,7200.50,72,720.05,
2024-03-22,15000.00,180,150,9200.00,92,920.00,节日促销
```

**响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "data": null
}
```

**详细使用说明**: 请参考 [AI日营业额Excel上传模板说明.md](AI日营业额Excel上传模板说明.md)

---

### 15. 更新日营业额
- **路径**: `/ai/daily-revenue/update`
- **方法**: POST
- **用途**: 更新日营业额记录

**请求体**:
```json
{
  "gbAiDailyRevenueId": 1,
  "gbAiDailyRevenueDineInRevenue": 13000.00,
  "gbAiDailyRevenueDineInOrders": 160,
  "gbAiDailyRevenueUpdateTime": "2024-03-20 10:30:00"
}
```

---

### 16. 删除日营业额
- **路径**: `/ai/daily-revenue/delete/{id}`
- **方法**: DELETE
- **用途**: 删除单条日营业额记录

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 日营业额ID |

---

### 17. 下载Excel导入模板
- **路径**: `/ai/daily-revenue/download-template`
- **方法**: GET
- **用途**: 下载日营业额Excel导入模板文件（.xlsx格式）

**响应**:
- 直接下载Excel文件：`日营业额导入模板.xlsx`
- 文件包含：
  - 标准表头
  - 3行示例数据
  - 使用说明工作表
  - 必填字段标记
  - 自动调整列宽

**使用方式**:
```bash
# 浏览器直接访问
http://localhost:8090/ai/daily-revenue/download-template

# 使用curl下载
curl -OJ http://localhost:8090/ai/daily-revenue/download-template
```

---

### 18. 下载示例数据模板（CSV格式）
- **路径**: `/ai/daily-revenue/download-sample`
- **方法**: GET
- **用途**: 下载日营业额示例数据模板（CSV格式）

**响应**:
- 直接下载CSV文件：`日营业额示例数据.csv`
- 文件包含：
  - 标准表头
  - 5行示例数据
  - CSV使用说明注释
  - 格式要求说明

**使用方式**:
```bash
# 浏览器直接访问
http://localhost:8090/ai/daily-revenue/download-sample

# 使用curl下载
curl -OJ http://localhost:8090/ai/daily-revenue/download-sample
```

---

### 19. 智能模板生成
- **路径**: `/ai/daily-revenue/download-smart-template`
- **方法**: GET
- **用途**: 根据日期范围和部门ID生成预填模板，包含日期列和部门信息

**查询参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| startDate | String | 是 | 开始日期，格式：yyyy-MM-dd |
| endDate | String | 是 | 结束日期，格式：yyyy-MM-dd |
| departmentId | Integer | 是 | 部门ID |

**响应**:
- 直接下载Excel文件，文件名格式：`日营业额模板_{部门名称}_{开始日期}_{结束日期}.xlsx`
- 文件包含：
  - 部门信息（部门ID、部门名称、日期范围）
  - 日期序列（自动生成指定范围内的所有日期）
  - 自动计算的星期几
  - 数值字段留空，等待用户填写
  - 详细的使用说明工作表

**模板特性**:
1. **智能填充**：自动生成指定日期范围的所有日期
2. **部门信息**：自动填充部门ID和部门名称
3. **日期计算**：自动计算星期几（0=周日，1=周一，...，6=周六）
4. **用户友好**：数值字段留空，用户只需填写金额和数量
5. **格式规范**：符合系统要求的Excel格式
6. **自动计算**：星期几由系统自动计算，节假日字段已移除

**使用示例**:
```bash
# 生成2024年3月1日至2024年3月31日的模板，部门ID为123
http://localhost:8090/ai/daily-revenue/download-smart-template?startDate=2024-03-01&endDate=2024-03-31&departmentId=123

# 使用curl下载
curl -OJ "http://localhost:8090/ai/daily-revenue/download-smart-template?startDate=2024-03-01&endDate=2024-03-31&departmentId=123"
```

**填写指南**:
1. **只需填写**：堂食营业额、堂食订单数、堂食顾客数、外卖营业额、外卖订单数、平台抽成
2. **自动填写**：日期已自动生成
3. **自动计算**：星期几由系统根据日期自动计算，无需填写
4. **系统处理**：节假日由系统处理，模板中已移除该字段
5. **可选填写**：备注
6. **上传要求**：填写完成后，使用上传接口上传，需提供相同的部门ID

---

## 模板文件说明

### Excel模板特性：
1. **标准格式**：符合系统要求的字段顺序和数据类型
2. **示例数据**：包含3行完整的示例数据，可直接参考
3. **使用说明**：单独的工作表说明使用方法和注意事项
4. **必填标记**：必填字段用星号(*)标记
5. **自动列宽**：根据内容自动调整列宽，便于阅读

### CSV模板特性：
1. **标准CSV格式**：逗号分隔，UTF-8编码
2. **注释说明**：包含详细的格式说明注释
3. **示例数据**：包含5行完整示例数据
4. **兼容性好**：所有文本编辑器都可以打开

### 智能模板特性：
1. **智能生成**：根据参数自动生成模板
2. **信息预填**：部门信息和日期已预填
3. **用户友好**：用户只需填写数值字段
4. **自动计算**：自动计算星期几
5. **格式规范**：符合上传接口要求

### 使用建议：
1. **首次使用**：建议下载Excel模板，包含详细说明
2. **批量处理**：使用CSV格式处理大量数据
3. **智能模板**：需要填写特定日期范围的模板时使用
4. **格式验证**：按照模板格式填写数据，避免格式错误
5. **数据备份**：上传前备份原始数据

---

## 更新日志

| 版本 | 日期 | 更新内容 |
|------|------|---------|
| 1.0.3 | 2026-04-15 | 新增下载模板接口和智能模板生成接口 |
| 1.0.2 | 2026-04-15 | 新增AI日营业额管理接口，包含Excel上传功能 |
| 1.0.1 | 2026-04-11 | 添加 Swagger @Operation 注解，完善接口说明 |
| 1.0.0 | 2026-04-11 | 初始版本，包含 GB 模块和农鑫商品接口 |
