package com.iflytek.astra.console.toolkit;

import com.iflytek.astra.console.toolkit.entity.tool.McpServerTool;
import com.iflytek.astra.console.toolkit.service.workflow.WorkflowService;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class ToolkitApplicationTests {
    @Mock
    private WorkflowService workflowService;

    @Test
    void contextLoads() {}

    @Test
    void getMcpServerListLocallyTest() {
        List<McpServerTool> mcpServerListLocally = workflowService.getMcpServerListLocally("111", 1, 10, true, null);
        for (McpServerTool mcpServerTool : mcpServerListLocally) {
            System.out.println(mcpServerTool);
        }
    }
}
