package com.iflytek.astron.console.toolkit.service.database;

import com.iflytek.astron.console.toolkit.entity.dto.database.DatabaseDto;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DatabaseService 快速验证测试 验证Spring配置和依赖注入是否正常
 *
 * @author test
 */
@SpringBootTest(classes = DatabaseServiceTestConfiguration.class)
@ActiveProfiles("test")
@Slf4j
public class DatabaseServiceQuickTest {

    @Autowired
    private DatabaseService databaseService;

    @Test
    @DisplayName("验证Spring配置和服务注入")
    void testSpringConfiguration() {
        // 验证服务注入成功
        assertNotNull(databaseService, "DatabaseService应该被正确注入");
        log.info("✅ Spring配置验证通过 - DatabaseService注入成功");
    }

    @Test
    @DisplayName("验证基础方法不抛出配置异常")
    void testBasicMethodsNoConfigError() {
        // 测试SQL工具方法（不依赖Spring上下文）
        assertDoesNotThrow(() -> {
            DatabaseService.safeSplitStatements("SELECT * FROM test;");
            log.info("✅ SQL工具方法验证通过");
        });

        // 测试创建DTO对象（验证类加载）
        assertDoesNotThrow(() -> {
            DatabaseDto dto = new DatabaseDto();
            dto.setName("test");
            dto.setDescription("test desc");
            assertNotNull(dto);
            log.info("✅ DTO对象创建验证通过");
        });

        log.info("✅ 基础方法验证全部通过 - 无配置异常");
    }
}
