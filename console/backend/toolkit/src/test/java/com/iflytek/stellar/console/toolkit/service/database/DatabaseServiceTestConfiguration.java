package com.iflytek.astra.console.toolkit.service.database;

import com.iflytek.astra.console.toolkit.config.properties.CommonConfig;
import com.iflytek.astra.console.toolkit.mapper.ConfigInfoMapper;
import com.iflytek.astra.console.toolkit.mapper.relation.FlowDbRelMapper;
import com.iflytek.astra.console.toolkit.service.extra.CoreSystemService;
import com.iflytek.astra.console.toolkit.tool.DataPermissionCheckTool;
import com.iflytek.astra.console.toolkit.util.S3Util;
import org.jooq.DSLContext;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;

/**
 * DatabaseService 测试配置类 专门为集成测试提供Spring Boot配置
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
                // 排除可能有问题的自动配置
                org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration.class,
                org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration.class
})
@ComponentScan(basePackages = {
                "com.iflytek.astra.console.toolkit.service.database",
                "com.iflytek.astra.console.toolkit.mapper.database"
})
@EntityScan(basePackages = {
                "com.iflytek.astra.console.toolkit.entity.table.database"
})
@MapperScan("com.iflytek.astra.console.toolkit.mapper")
public class DatabaseServiceTestConfiguration {

    // Mock所有DatabaseService需要的外部依赖

    @MockitoBean
    private DataPermissionCheckTool dataPermissionCheckTool;

    @MockitoBean
    private S3Util s3Util;

    @MockitoBean
    private CoreSystemService coreSystemService;

    @MockitoBean
    private FlowDbRelMapper flowDbRelMapper;

    @MockitoBean
    private ConfigInfoMapper configInfoMapper;

    @MockitoBean
    private DSLContext dslContext;

    @MockitoBean
    private CommonConfig commonConfig;

}
