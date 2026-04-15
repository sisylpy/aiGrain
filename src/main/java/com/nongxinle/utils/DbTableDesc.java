package com.nongxinle.utils;

import java.sql.*;

public class DbTableDesc {
    public static void main(String[] args) {
        String url = "jdbc:mysql://101.42.222.149:3306/ai_marketing?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false";
        String user = "root";
        String password = "Lpy87176693";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns("ai_marketing", null, "gb_department_goods_stock_reduce", null);
            
            System.out.println("=== gb_department_goods_stock_reduce 表结构 ===");
            while (columns.next()) {
                String colName = columns.getString("COLUMN_NAME");
                String colType = columns.getString("TYPE_NAME");
                int size = columns.getInt("COLUMN_SIZE");
                String nullable = columns.getString("IS_NULLABLE");
                System.out.println(colName + " | " + colType + "(" + size + ") | nullable: " + nullable);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
