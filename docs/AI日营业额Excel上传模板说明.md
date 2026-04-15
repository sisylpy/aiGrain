# AI日营业额Excel上传模板说明

## 接口信息
- **接口地址**: `POST /ai/daily-revenue/upload-excel`
- **请求方式**: `multipart/form-data`
- **参数**:
  1. `file`: Excel文件（.xls 或 .xlsx格式）
  2. `departmentId`: 部门ID（餐厅ID）
  3. `distributerId`: 分配者ID

## Excel模板格式

### 表头（第一行）
| 日期 | 堂食营业额 | 堂食订单数 | 堂食顾客数 | 外卖营业额 | 外卖订单数 | 平台抽成 | 星期几 | 节假日 | 备注 |
|------|------------|------------|------------|------------|------------|----------|--------|--------|------|

### 详细说明

1. **日期**（必填）：格式为 `yyyy-MM-dd`，如 `2024-03-20`
2. **堂食营业额**（必填）：堂食收入金额，单位：元，支持小数
3. **堂食订单数**（必填）：堂食订单数量，整数
4. **堂食顾客数**（必填）：堂食顾客人数，整数
5. **外卖营业额**（必填）：外卖收入金额，单位：元，支持小数
6. **外卖订单数**（必填）：外卖订单数量，整数
7. **平台抽成**（必填）：外卖平台抽成金额，单位：元，支持小数
8. **星期几**（可选）：0-6（0=周日，1=周一，...，6=周六），如果为空会根据日期自动计算
9. **节假日**（可选）：节假日名称，如 "国庆节"、"春节"等，可为空
10. **备注**（可选）：其他备注信息，可为空

### 示例数据

| 日期       | 堂食营业额 | 堂食订单数 | 堂食顾客数 | 外卖营业额 | 外卖订单数 | 平台抽成 | 星期几 | 节假日 | 备注 |
|------------|------------|------------|------------|------------|------------|----------|--------|--------|------|
| 2024-03-20 | 12500.50   | 156        | 120        | 8500.00    | 85         | 850.00   | 3      |        |      |
| 2024-03-21 | 9800.00    | 120        | 95         | 7200.50    | 72         | 720.05   | 4      |        | 天气好 |
| 2024-03-22 | 15000.00   | 180        | 150        | 9200.00    | 92         | 920.00   | 5      | 清明节 | 节日促销 |

## 使用步骤

### 1. 准备Excel文件
1. 下载或创建上述格式的Excel文件
2. 按照模板填写数据
3. 确保数据格式正确

### 2. 调用接口
使用Postman或前端表单上传：
```http
POST /ai/daily-revenue/upload-excel
Content-Type: multipart/form-data

参数：
- file: [选择的Excel文件]
- departmentId: 123
- distributerId: 456
```

### 3. 响应结果
成功响应：
```json
{
  "code": 200,
  "msg": "success",
  "data": null
}
```

错误响应：
```json
{
  "code": 500,
  "msg": "Excel解析失败：具体错误信息",
  "data": null
}
```

## 注意事项

1. **文件格式**：只支持 `.xls` 和 `.xlsx` 格式
2. **数据验证**：
   - 日期必须有效
   - 金额和数量必须为数字
   - 不能有空行或格式错误的数据
3. **批量限制**：建议一次上传不超过1000条记录
4. **重复数据**：系统不会检查重复数据，请确保数据不重复
5. **错误处理**：
   - 如果某行数据格式错误，该行会被跳过
   - 其他正确行会被正常保存
   - 会返回成功保存的总行数

## 前端调用示例（JavaScript）

```javascript
// 使用FormData上传Excel文件
async function uploadExcel() {
    const formData = new FormData();
    formData.append('file', document.getElementById('excelFile').files[0]);
    formData.append('departmentId', '123');
    formData.append('distributerId', '456');
    
    try {
        const response = await fetch('/ai/daily-revenue/upload-excel', {
            method: 'POST',
            body: formData
        });
        
        const result = await response.json();
        if (result.code === 200) {
            alert('上传成功！');
        } else {
            alert('上传失败：' + result.msg);
        }
    } catch (error) {
        alert('网络错误：' + error.message);
    }
}
```

## 后端日志

成功上传后，可以在后端日志中看到：
```
[INFO] Excel上传成功，共处理 {count} 条记录
[INFO] 部门ID：{departmentId}，分配者ID：{distributerId}
```

## 常见问题

**Q: 为什么上传失败？**
A: 请检查：
1. 文件格式是否正确（.xls 或 .xlsx）
2. 文件是否为空
3. 文件是否损坏
4. Excel数据格式是否正确

**Q: 为什么部分数据没有保存？**
A: 可能是某行数据格式错误，该行会被自动跳过

**Q: 如何批量更新现有数据？**
A: 目前只能新增，如果需要更新请使用单独的更新接口

**Q: 支持哪些Excel版本？**
A: 支持 Microsoft Excel 2003及以上版本（.xls 和 .xlsx）

## 联系支持

如有问题，请联系技术支持团队。