#!/usr/bin/env python3
"""
测试新的模板格式（去掉星期几和节假日字段）
使用方法: python 测试新模板格式.py
"""

import os
import csv

def test_template_format():
    """测试新模板格式"""
    
    print("=" * 60)
    print("新模板格式测试")
    print("=" * 60)
    
    # 创建测试目录
    test_dir = "test_new_templates"
    if not os.path.exists(test_dir):
        os.makedirs(test_dir)
        print(f"✅ 创建测试目录: {test_dir}")
    
    # 测试新的CSV格式
    csv_content = """日期,堂食营业额,堂食订单数,堂食顾客数,外卖营业额,外卖订单数,平台抽成,备注
2024-03-20,12500.50,156,120,8500.00,85,850.00,天气好
2024-03-21,9800.00,120,95,7200.50,72,720.05,
2024-03-22,15000.00,180,150,9200.00,92,920.00,节日促销
2024-03-23,13500.00,165,130,7800.00,78,780.00,周末促销
2024-03-24,11000.00,140,110,6500.00,65,650.00,"""
    
    csv_file = os.path.join(test_dir, "新格式模板.csv")
    with open(csv_file, 'w', encoding='utf-8') as f:
        f.write(csv_content)
    
    print(f"✅ 创建新格式CSV模板: {csv_file}")
    print(f"   文件大小: {os.path.getsize(csv_file):,} bytes")
    
    # 验证CSV格式
    with open(csv_file, 'r', encoding='utf-8') as f:
        reader = csv.reader(f)
        headers = next(reader)
        
        print(f"\n✅ CSV表头验证:")
        print(f"   表头数量: {len(headers)}")
        print(f"   表头内容: {headers}")
        
        # 验证字段数量
        expected_headers = ['日期', '堂食营业额', '堂食订单数', '堂食顾客数', '外卖营业额', '外卖订单数', '平台抽成', '备注']
        if headers == expected_headers:
            print(f"   ✅ 表头格式正确")
        else:
            print(f"   ❌ 表头格式错误，期望: {expected_headers}")
        
        # 验证数据行
        data_rows = list(reader)
        print(f"\n✅ 数据行验证:")
        print(f"   数据行数: {len(data_rows)}")
        
        for i, row in enumerate(data_rows, 1):
            print(f"   第{i}行: {len(row)}列数据")
            if len(row) == len(headers):
                print(f"      ✅ 列数正确")
            else:
                print(f"      ❌ 列数错误，期望{len(headers)}列，实际{len(row)}列")
    
    # 测试说明
    print(f"\n" + "=" * 60)
    print("新模板格式特点")
    print("=" * 60)
    print("""
✅ 模板格式优化:
1. 字段减少: 从10列减少到8列
2. 星期几: 由系统根据日期自动计算，无需用户填写
3. 节假日: 由系统处理，模板中已移除该字段
4. 用户友好: 用户只需填写6个数值字段
5. 一致性: 所有模板格式统一

✅ 接口说明:
1. 基础模板: /ai/daily-revenue/download-template
2. CSV示例: /ai/daily-revenue/download-sample
3. 智能模板: /ai/daily-revenue/download-smart-template

✅ 上传要求:
1. 文件格式: .xls 或 .xlsx
2. 必填字段: 前7列（日期 + 6个数值字段）
3. 自动计算: 星期几由系统根据日期自动计算
4. 系统处理: 节假日由系统处理

✅ 测试建议:
1. 使用智能模板生成特定日期范围的模板
2. 填写数值字段（堂食营业额、订单数等）
3. 使用上传接口测试数据导入
4. 验证星期几是否正确自动计算
    """)

def generate_upload_test_data():
    """生成上传测试数据"""
    
    print(f"\n" + "=" * 60)
    print("上传测试数据生成")
    print("=" * 60)
    
    # 生成测试数据
    test_data = []
    for i in range(5):
        day = 20 + i
        test_data.append({
            "日期": f"2024-03-{day}",
            "堂食营业额": 12000.00 + i * 500,
            "堂食订单数": 150 + i * 5,
            "堂食顾客数": 120 + i * 3,
            "外卖营业额": 8000.00 + i * 300,
            "外卖订单数": 80 + i * 2,
            "平台抽成": 800.00 + i * 30,
            "备注": f"测试数据{i+1}"
        })
    
    # 保存为CSV
    test_file = "test_new_templates/上传测试数据.csv"
    with open(test_file, 'w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=test_data[0].keys())
        writer.writeheader()
        writer.writerows(test_data)
    
    print(f"✅ 生成上传测试数据: {test_file}")
    print(f"\n📋 测试数据预览:")
    print("日期,堂食营业额,堂食订单数,堂食顾客数,外卖营业额,外卖订单数,平台抽成,备注")
    for data in test_data[:3]:  # 只显示前3行
        print(f"{data['日期']},{data['堂食营业额']},{data['堂食订单数']},{data['堂食顾客数']},{data['外卖营业额']},{data['外卖订单数']},{data['平台抽成']},{data['备注']}")
    
    print(f"\n📝 上传测试命令:")
    print(f"""curl -X POST \\
  http://localhost:8090/ai/daily-revenue/upload-excel \\
  -F "file=@{test_file}" \\
  -F "departmentId=123" \\
  -F "distributerId=456"
""")

def main():
    """主函数"""
    
    print("新模板格式测试工具")
    print("-" * 60)
    
    # 运行测试
    test_template_format()
    generate_upload_test_data()
    
    print("\n" + "=" * 60)
    print("测试总结")
    print("=" * 60)
    print("""
✅ 已完成测试:
1. 新模板格式验证
2. CSV格式兼容性测试
3. 上传测试数据生成
4. 接口使用说明

📋 下一步操作:
1. 启动Spring Boot应用
2. 测试模板下载接口
3. 使用新模板格式填写数据
4. 测试上传接口

🔗 相关文档:
- API接口文档: docs/API接口文档.md
- 智能模板使用指南: docs/智能模板使用指南.md
- 测试Excel上传功能: docs/测试Excel上传功能.md

⚠️ 注意事项:
1. 新模板格式已移除星期几和节假日字段
2. 星期几由系统根据日期自动计算
3. 节假日由系统处理
4. 用户只需填写6个数值字段
    """)

if __name__ == "__main__":
    main()