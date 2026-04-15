#!/usr/bin/env python3
"""
测试智能模板生成接口
使用方法: python 测试智能模板生成.py
"""

import requests
import os
from datetime import datetime, timedelta

def test_smart_template_generation():
    """测试智能模板生成接口"""
    
    base_url = "http://localhost:8090/ai/daily-revenue"
    
    # 测试参数
    test_cases = [
        {
            "name": "月度模板",
            "start_date": "2024-03-01",
            "end_date": "2024-03-31",
            "department_id": 123
        },
        {
            "name": "周度模板",
            "start_date": "2024-03-01",
            "end_date": "2024-03-07",
            "department_id": 456
        },
        {
            "name": "季度模板",
            "start_date": "2024-01-01",
            "end_date": "2024-03-31",
            "department_id": 789
        }
    ]
    
    print("=" * 60)
    print("智能模板生成接口测试")
    print("=" * 60)
    
    for i, test_case in enumerate(test_cases, 1):
        print(f"\n测试用例 {i}: {test_case['name']}")
        print(f"开始日期: {test_case['start_date']}")
        print(f"结束日期: {test_case['end_date']}")
        print(f"部门ID: {test_case['department_id']}")
        
        # 构建请求URL
        url = f"{base_url}/download-smart-template"
        params = {
            "startDate": test_case["start_date"],
            "endDate": test_case["end_date"],
            "departmentId": test_case["department_id"]
        }
        
        try:
            # 发送请求
            response = requests.get(url, params=params, stream=True)
            
            if response.status_code == 200:
                # 生成文件名
                filename = response.headers.get('content-disposition', '').split('filename=')[-1].strip('"')
                if not filename:
                    filename = f"日营业额模板_{test_case['department_id']}_{test_case['start_date']}_{test_case['end_date']}.xlsx"
                
                # 保存文件
                save_path = os.path.join("test_templates", filename)
                os.makedirs("test_templates", exist_ok=True)
                
                with open(save_path, 'wb') as f:
                    for chunk in response.iter_content(chunk_size=8192):
                        f.write(chunk)
                
                file_size = os.path.getsize(save_path)
                print(f"✅ 成功生成模板: {filename}")
                print(f"   文件大小: {file_size:,} bytes")
                print(f"   保存路径: {save_path}")
                
                # 检查文件内容
                if file_size > 0:
                    print("   文件状态: 有效")
                else:
                    print("   ⚠️ 文件状态: 空文件")
                    
            else:
                print(f"❌ 请求失败: HTTP {response.status_code}")
                if response.text:
                    print(f"   错误信息: {response.text[:200]}")
                    
        except requests.exceptions.ConnectionError:
            print("❌ 连接失败: 请确保服务正在运行")
            print(f"   服务地址: {base_url}")
        except Exception as e:
            print(f"❌ 发生错误: {str(e)}")
    
    print("\n" + "=" * 60)
    print("测试完成")
    print("=" * 60)

def test_basic_templates():
    """测试基础模板接口"""
    
    base_url = "http://localhost:8090/ai/daily-revenue"
    
    templates = [
        {
            "name": "Excel模板",
            "url": f"{base_url}/download-template",
            "filename": "日营业额导入模板.xlsx"
        },
        {
            "name": "CSV示例模板",
            "url": f"{base_url}/download-sample",
            "filename": "日营业额示例数据.csv"
        }
    ]
    
    print("\n" + "=" * 60)
    print("基础模板接口测试")
    print("=" * 60)
    
    for template in templates:
        print(f"\n测试模板: {template['name']}")
        print(f"接口地址: {template['url']}")
        
        try:
            response = requests.get(template['url'], stream=True)
            
            if response.status_code == 200:
                save_path = os.path.join("test_templates", template['filename'])
                os.makedirs("test_templates", exist_ok=True)
                
                with open(save_path, 'wb') as f:
                    for chunk in response.iter_content(chunk_size=8192):
                        f.write(chunk)
                
                file_size = os.path.getsize(save_path)
                print(f"✅ 成功下载: {template['filename']}")
                print(f"   文件大小: {file_size:,} bytes")
                
            else:
                print(f"❌ 下载失败: HTTP {response.status_code}")
                
        except Exception as e:
            print(f"❌ 发生错误: {str(e)}")

def test_upload_function():
    """测试上传功能（需要先填写模板）"""
    
    print("\n" + "=" * 60)
    print("上传功能测试（需要先填写模板）")
    print("=" * 60)
    
    print("""
上传测试步骤:
1. 先生成一个智能模板
2. 填写模板中的数值字段
3. 使用以下命令测试上传:

curl -X POST \\
  http://localhost:8090/ai/daily-revenue/upload-excel \\
  -F "file=@填写好的模板.xlsx" \\
  -F "departmentId=123" \\
  -F "distributerId=456"

或使用Python:

import requests

url = "http://localhost:8090/ai/daily-revenue/upload-excel"
files = {'file': open('填写好的模板.xlsx', 'rb')}
data = {
    'departmentId': '123',
    'distributerId': '456'
}

response = requests.post(url, files=files, data=data)
print(response.json())
    """)

def main():
    """主函数"""
    
    print("智能模板生成系统测试")
    print("-" * 60)
    
    # 创建测试目录
    if not os.path.exists("test_templates"):
        os.makedirs("test_templates")
        print("✅ 创建测试目录: test_templates")
    
    # 运行测试
    test_smart_template_generation()
    test_basic_templates()
    test_upload_function()
    
    print("\n" + "=" * 60)
    print("测试总结")
    print("=" * 60)
    print("""
✅ 已完成的功能:
1. 智能模板生成接口
   - 根据日期范围和部门ID生成模板
   - 自动填充日期和部门信息
   - 自动计算星期几

2. 基础模板接口
   - Excel模板下载
   - CSV示例模板下载

3. 文档资源
   - 智能模板使用指南.md
   - API接口文档.md
   - 测试Excel上传功能.md

📋 下一步操作:
1. 启动Spring Boot应用
2. 运行此测试脚本
3. 检查生成的模板文件
4. 填写模板并测试上传功能

🔗 相关文档:
- API接口文档: docs/API接口文档.md
- 智能模板使用指南: docs/智能模板使用指南.md
- 测试指南: docs/测试Excel上传功能.md
    """)

if __name__ == "__main__":
    main()