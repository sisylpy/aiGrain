package com.nongxinle.test;

import com.nongxinle.utils.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 简单数据库连接测试
 */
@Slf4j
@RestController
@RequestMapping("/test/simple")
public class SimpleDbTest {

    private static final String URL = "jdbc:mysql://101.42.222.149:3306/ai_marketing?allowMultiQueries=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "Lpy87176693";

    /**
     * 查看 gb_distributer_purchase_batch 表结构
     */
    @GetMapping("/gbBatchStructure")
    public R getGbDistributerPurchaseBatchStructure() {
        List<Map<String, Object>> columnList = new ArrayList<>();
        
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD)) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // 获取表结构
            ResultSet columns = metaData.getColumns(null, null, "gb_distributer_purchase_batch", null);
            
            while (columns.next()) {
                Map<String, Object> columnInfo = new HashMap<>();
                columnInfo.put("columnName", columns.getString("COLUMN_NAME"));
                columnInfo.put("dataType", columns.getString("TYPE_NAME"));
                columnInfo.put("columnSize", columns.getInt("COLUMN_SIZE"));
                columnInfo.put("isNullable", columns.getString("IS_NULLABLE"));
                columnInfo.put("columnDefault", columns.getString("COLUMN_DEF"));
                columnInfo.put("remarks", columns.getString("REMARKS"));
                columnList.add(columnInfo);
            }
            
            // 获取表数据样本
            List<Map<String, Object>> sampleData = new ArrayList<>();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM gb_distributer_purchase_batch LIMIT 5")) {
                
                ResultSetMetaData rsmd = rs.getMetaData();
                int columnCount = rsmd.getColumnCount();
                
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(rsmd.getColumnName(i), rs.getObject(i));
                    }
                    sampleData.add(row);
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("tableName", "gb_distributer_purchase_batch");
            result.put("columns", columnList);
            result.put("sampleData", sampleData);
            result.put("totalColumns", columnList.size());
            
            return R.ok().put("data", result);
            
        } catch (Exception e) {
            log.error("获取表结构失败", e);
            return R.error("获取表结构失败: " + e.getMessage());
        }
    }

    /**
     * 获取表的创建语句
     */
    @GetMapping("/gbBatchCreateSql")
    public R getCreateTableSql() {
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD)) {
            String sql = "SHOW CREATE TABLE gb_distributer_purchase_batch";
            
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                if (rs.next()) {
                    String createTableSql = rs.getString(2);
                    return R.ok().put("data", createTableSql);
                }
            }
            
        } catch (Exception e) {
            log.error("获取创建表语句失败", e);
            return R.error("获取创建表语句失败: " + e.getMessage());
        }
        
        return R.error("未找到表结构");
    }
}