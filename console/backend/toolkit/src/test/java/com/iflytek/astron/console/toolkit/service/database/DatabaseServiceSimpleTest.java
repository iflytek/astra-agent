package com.iflytek.astron.console.toolkit.service.database;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DatabaseService 简单测试 不依赖Spring上下文的基础功能测试
 *
 * @author test
 */
@Slf4j
public class DatabaseServiceSimpleTest {

    @Test
    @DisplayName("TC119: SQL语句安全分割测试")
    void testSafeSplitStatements() {
        // Test case 1: 简单语句
        String sql1 = "CREATE TABLE test (id INT); DROP TABLE old;";
        List<String> result1 = DatabaseService.safeSplitStatements(sql1);
        assertEquals(2, result1.size());
        assertEquals("CREATE TABLE test (id INT)", result1.get(0));
        assertEquals("DROP TABLE old", result1.get(1));

        // Test case 2: 包含字符串的语句
        String sql2 = "INSERT INTO test VALUES ('name;test'); UPDATE test SET name='value';";
        List<String> result2 = DatabaseService.safeSplitStatements(sql2);
        assertEquals(2, result2.size());
        assertEquals("INSERT INTO test VALUES ('name;test')", result2.get(0));
        assertEquals("UPDATE test SET name='value'", result2.get(1));

        // Test case 3: 转义字符处理
        String sql3 = "INSERT INTO test VALUES ('It''s a test');";
        List<String> result3 = DatabaseService.safeSplitStatements(sql3);
        assertEquals(1, result3.size());
        assertEquals("INSERT INTO test VALUES ('It''s a test')", result3.get(0));

        // Test case 4: 空语句处理
        String sql4 = ";;;";
        List<String> result4 = DatabaseService.safeSplitStatements(sql4);
        assertEquals(0, result4.size());

        // Test case 5: 复杂SQL注入测试
        String sql5 = "SELECT * FROM users WHERE name=''; DROP TABLE users; --';";
        List<String> result5 = DatabaseService.safeSplitStatements(sql5);
        assertEquals(1, result5.size()); // 应该被识别为一个完整语句
        assertEquals("SELECT * FROM users WHERE name=''; DROP TABLE users; --'", result5.get(0));

        log.info("✅ SQL安全分割测试全部通过！");
    }

    @Test
    @DisplayName("边界条件测试：空字符串和null")
    void testSafeSplitStatements_EdgeCases() {
        // 空字符串
        List<String> result1 = DatabaseService.safeSplitStatements("");
        assertEquals(0, result1.size());

        // 只有空格
        List<String> result2 = DatabaseService.safeSplitStatements("   ");
        assertEquals(0, result2.size());

        // 只有分号
        List<String> result3 = DatabaseService.safeSplitStatements(";");
        assertEquals(0, result3.size());

        // 多个空格和分号
        List<String> result4 = DatabaseService.safeSplitStatements("  ;  ;  ");
        assertEquals(0, result4.size());

        log.info("✅ 边界条件测试通过！");
    }

    @Test
    @DisplayName("复杂SQL语句测试")
    void testSafeSplitStatements_ComplexSQL() {
        String complexSql = """
                CREATE TABLE users (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    email VARCHAR(255) UNIQUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );

                INSERT INTO users (name, email) VALUES
                    ('John Doe', 'john@example.com'),
                    ('Jane Smith', 'jane@example.com');

                UPDATE users SET name = 'John Updated' WHERE id = 1;
                """;

        List<String> results = DatabaseService.safeSplitStatements(complexSql);

        // 验证分割结果
        assertEquals(3, results.size());

        // 验证CREATE TABLE语句
        assertTrue(results.get(0).contains("CREATE TABLE users"));
        assertTrue(results.get(0).contains("PRIMARY KEY"));

        // 验证INSERT语句
        assertTrue(results.get(1).contains("INSERT INTO users"));
        assertTrue(results.get(1).contains("john@example.com"));

        // 验证UPDATE语句
        assertTrue(results.get(2).contains("UPDATE users"));
        assertTrue(results.get(2).contains("John Updated"));

        log.info("✅ 复杂SQL语句测试通过！");
    }

    @Test
    @DisplayName("SQL注入防护测试")
    void testSafeSplitStatements_SQLInjection() {
        // 模拟SQL注入攻击
        String injectionAttempt = "SELECT * FROM users WHERE id = 1; DROP TABLE users; --";
        List<String> results = DatabaseService.safeSplitStatements(injectionAttempt);

        assertEquals(2, results.size());
        assertEquals("SELECT * FROM users WHERE id = 1", results.get(0));
        assertEquals("DROP TABLE users", results.get(1));

        // 带引号的注入尝试
        String quotedInjection = "SELECT * FROM users WHERE name = 'admin'; DELETE FROM users; --'";
        List<String> quotedResults = DatabaseService.safeSplitStatements(quotedInjection);

        // 应该被正确识别为一个语句（因为分号在引号内）
        assertEquals(1, quotedResults.size());
        assertTrue(quotedResults.get(0).contains("DELETE FROM users"));

        log.info("✅ SQL注入防护测试通过！");
    }

    @Test
    @DisplayName("性能测试：大量SQL语句处理")
    void testSafeSplitStatements_Performance() {
        StringBuilder largeSql = new StringBuilder();
        int statementCount = 1000;

        // 生成大量SQL语句
        for (int i = 0; i < statementCount; i++) {
            largeSql.append("INSERT INTO test_table (id, name) VALUES (")
                    .append(i)
                    .append(", 'name_")
                    .append(i)
                    .append("'); ");
        }

        long startTime = System.currentTimeMillis();
        List<String> results = DatabaseService.safeSplitStatements(largeSql.toString());
        long endTime = System.currentTimeMillis();

        assertEquals(statementCount, results.size());

        long processingTime = endTime - startTime;
        log.info("✅ 性能测试通过！处理{}条SQL语句耗时: {}ms", statementCount, processingTime);

        // 性能断言：处理1000条语句应该在合理时间内完成
        assertTrue(processingTime < 1000, "处理时间应该小于1秒");
    }

    @Test
    @DisplayName("字符编码测试：中文和特殊字符")
    void testSafeSplitStatements_Encoding() {
        String chineseSql = "INSERT INTO users (name, description) VALUES " +
                "('张三', '这是一个中文描述；包含分号'); " +
                "UPDATE users SET name = '李四' WHERE id = 1;";

        List<String> results = DatabaseService.safeSplitStatements(chineseSql);

        assertEquals(2, results.size());
        assertTrue(results.get(0).contains("张三"));
        assertTrue(results.get(0).contains("这是一个中文描述；包含分号"));
        assertTrue(results.get(1).contains("李四"));

        log.info("✅ 字符编码测试通过！");
    }
}
