# Excel上传功能测试指南

## 接口信息
- **接口地址**: `POST /ai/daily-revenue/upload-excel`
- **请求方式**: `multipart/form-data`
- **测试端口**: `8090`（默认）

## 测试步骤

### 1. 准备测试数据
1. 下载模板文件：`/docs/日营业额导入模板.csv`
2. 使用Excel或文本编辑器打开
3. 修改部门ID和分配者ID为实际值

### 2. 使用Postman测试

#### 配置Postman：
1. **方法**: POST
2. **URL**: `http://localhost:8090/ai/daily-revenue/upload-excel`
3. **Body**: 选择 `form-data`

#### 添加参数：
```
键                值               类型
-------------------------------------------
file            [选择文件]        file
departmentId    123              text
distributerId   456              text
```

#### 选择文件：
- 选择 `docs/日营业额导入模板.csv` 文件
- 或创建自己的Excel文件

#### 发送请求

### 3. 预期响应

#### 成功响应：
```json
{
  "code": 200,
  "msg": "success",
  "data": null
}
```

#### 错误响应：
```json
{
  "code": 500,
  "msg": "Excel解析失败：具体错误信息",
  "data": null
}
```

### 4. 使用curl测试

```bash
curl -X POST \
  http://localhost:8090/ai/daily-revenue/upload-excel \
  -F "file=@/Users/lpy/Documents/javaWeb/kuangjia/aigrain/docs/日营业额导入模板.csv" \
  -F "departmentId=123" \
  -F "distributerId=456"
```

### 5. 使用Python测试

```python
import requests

url = "http://localhost:8090/ai/daily-revenue/upload-excel"
files = {
    'file': open('/Users/lpy/Documents/javaWeb/kuangjia/aigrain/docs/日营业额导入模板.csv', 'rb')
}
data = {
    'departmentId': '123',
    'distributerId': '456'
}

response = requests.post(url, files=files, data=data)
print(response.json())
```

## 测试场景

### 场景1：正常上传
- 使用正确的Excel文件
- 正确的部门ID和分配者ID
- 预期：上传成功

### 场景2：空文件
- 上传空文件
- 预期：返回错误"请上传Excel文件"

### 场景3：错误文件类型
- 上传PDF或图片文件
- 预期：返回错误"请上传Excel文件（.xls 或 .xlsx 格式）"

### 场景4：无效数据格式
- Excel中包含非数字格式的金额
- 预期：错误行被跳过，其他行正常保存

### 场景5：缺少必填字段
- Excel中缺少日期字段
- 预期：该行被跳过

## 验证结果

### 1. 检查数据库
```sql
-- 查询上传的数据
SELECT * FROM gb_ai_daily_revenue 
WHERE gb_ai_daily_revenue_department_id = 123
ORDER BY gb_ai_daily_revenue_record_date DESC;
```

### 2. 检查接口返回
- 调用 `/ai/daily-revenue/list/123` 查看上传的数据
- 调用 `/ai/daily-revenue/stats/123` 查看统计结果是否更新

## 常见问题排查

### Q1: 上传失败，返回"文件读取失败"
- 检查文件路径是否正确
- 检查文件权限
- 检查文件是否被其他程序占用

### Q2: 上传失败，返回"Excel解析失败"
- 检查Excel文件格式是否正确
- 检查Excel文件是否损坏
- 检查Excel中的数据类型是否正确

### Q3: 数据没有保存到数据库
- 检查部门ID是否存在
- 检查数据库连接是否正常
- 检查是否有事务回滚

### Q4: 部分数据保存，部分数据丢失
- 检查Excel中是否有格式错误的数据
- 检查是否有重复数据
- 检查是否有违反数据库约束的数据

## 性能测试

### 测试不同数据量：
1. **小数据量**：10-100条记录
2. **中数据量**：100-1000条记录  
3. **大数据量**：1000-10000条记录

### 监控指标：
- 上传时间
- 内存使用
- CPU使用率
- 数据库响应时间

## 安全测试

### 1. 文件大小限制
- 测试超大文件（>100MB）
- 预期：应该有文件大小限制

### 2. 文件类型检查
- 测试非Excel文件
- 预期：拒绝上传

### 3. SQL注入测试
- 在Excel中插入SQL语句
- 预期：应该被正确处理为字符串

## 日志检查

上传成功后，检查应用日志：
```
[INFO] Excel上传成功，共处理 30 条记录
[INFO] 部门ID：123，分配者ID：456
[INFO] 开始时间：2024-03-20，结束时间：2024-04-20
```

## 集成测试

### 前端集成测试：
1. 创建前端上传页面
2. 测试文件选择功能
3. 测试进度显示
4. 测试错误处理

### API集成测试：
1. 测试所有日营业额接口的联动
2. 验证数据一致性
3. 测试并发上传

## 恢复测试

### 测试中断恢复：
1. 在上传过程中停止服务
2. 重新启动服务
3. 验证数据完整性

## 总结

Excel上传功能已成功实现，支持：
- ✅ 批量上传日营业额数据
- ✅ 自动解析Excel格式
- ✅ 数据验证和错误处理
- ✅ 与现有接口完全兼容
- ✅ 详细的文档和测试指南

如需进一步测试或有任何问题，请参考相关文档或联系技术支持。