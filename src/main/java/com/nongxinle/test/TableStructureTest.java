package com.nongxinle.test;

import com.nongxinle.utils.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库表结构测试
 */
@Slf4j
@RestController
@RequestMapping("/test/db")
public class TableStructureTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 查看 gb_distributer_purchase_batch 表结构
     */
    @GetMapping("/gbDistributerPurchaseBatch")
    public R getGbDistributerPurchaseBatchStructure() {
        try {
            Connection connection = jdbcTemplate.getDataSource().getConnection();
            DatabaseMetaData metaData = connection.getMetaData();
            
            // 获取表结构
            ResultSet columns = metaData.getColumns(null, null, "gb_distributer_purchase_batch", null);
            
            List<Map<String, Object>> columnList = new ArrayList<>();
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
            columns.close();
            connection.close();
            
            // 获取表数据样本
            List<Map<String, Object>> sampleData = jdbcTemplate.queryForList(
                "SELECT * FROM gb_distributer_purchase_batch LIMIT 5"
            );
            
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
     * 查看所有表
     */
    @GetMapping("/tables")
    public R getAllTables() {
        try {
            Connection connection = jdbcTemplate.getDataSource().getConnection();
            DatabaseMetaData metaData = connection.getMetaData();
            
            // 获取所有表
            ResultSet tables = metaData.getTables(null, null, null, new String[]{"TABLE"});
            
            List<String> tableList = new ArrayList<>();
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                tableList.add(tableName);
            }
            tables.close();
            connection.close();
            
            return R.ok().put("data", tableList);
            
        } catch (Exception e) {
            log.error("获取表列表失败", e);
            return R.error("获取表列表失败: " + e.getMessage());
        }
    }
}