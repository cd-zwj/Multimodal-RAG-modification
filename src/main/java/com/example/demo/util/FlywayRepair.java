package com.example.demo.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 一次性工具：清理 Flyway 失败的迁移记录。
 * 修复后应删除此类。
 */
public class FlywayRepair {

    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://39.108.175.183:3306/rag_knowledge?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        String user = "root";
        String password = "nm561234789";

        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {

            // 查看失败记录
            System.out.println("=== flyway_schema_history (failed) ===");
            var rs = stmt.executeQuery("SELECT * FROM flyway_schema_history WHERE success = 0");
            while (rs.next()) {
                System.out.printf("  version=%s, description=%s, success=%d%n",
                        rs.getString("version"), rs.getString("description"), rs.getInt("success"));
            }

            // 删除失败记录
            int deleted = stmt.executeUpdate("DELETE FROM flyway_schema_history WHERE success = 0");
            System.out.println("Deleted " + deleted + " failed migration record(s).");
        }
    }
}
