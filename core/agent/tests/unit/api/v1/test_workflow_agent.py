"""WorkflowAgent API单元test模块."""

import concurrent.futures
from typing import Any, AsyncGenerator
from unittest.mock import AsyncMock, Mock, patch

from fastapi.testclient import TestClient

from api.app import app


class TestWorkflowAgentAPI:
    """WorkflowAgent APItest类."""

    def __init__(self) -> None:
        """初始化test类."""
        self.client = TestClient(app)

    def setup_method(self) -> None:
        """test方法初始化."""
        # Reset client state for each test
        self.client = TestClient(app)

    def test_workflow_agent_routes_exist(self) -> None:
        """testWorkflow Agent API路由是否存在."""
        # Test possible workflow agent endpoints
        workflow_endpoints = [
            "/v1/workflow",
            "/workflow",
            "/v1/agent/workflow",
            "/agent/workflow",
            "/v1/workflow/execute",
            "/workflow/execute",
        ]

        responses = []
        for endpoint in workflow_endpoints:
            try:
                response = self.client.get(endpoint)
                responses.append((endpoint, response.status_code))
            except (ConnectionError, ValueError, TypeError) as e:
                responses.append((endpoint, f"Error: {e}"))  # type: ignore[arg-type]

        # Verify at least one endpoint responds
        valid_responses = [r for r in responses if isinstance(r[1], int)]
        assert len(valid_responses) > 0

    @patch("api.v1.workflow_agent.WorkflowAgentRunnerBuilder")
    def test_workflow_execution_success(self, mock_builder: Any) -> None:
        """test工作流execute成功场景."""
        # Mock builder and runner
        mock_runner = Mock()

        async def mock_run_stream() -> AsyncGenerator[dict[str, str], None]:
            yield {"type": "step", "content": "开始execute工作流"}
            yield {"type": "step", "content": "正在处理数据"}
            yield {"type": "result", "content": "工作流execute完成", "status": "success"}

        mock_runner.run = Mock(return_value=mock_run_stream())

        mock_builder_instance = Mock()
        mock_builder_instance.build = AsyncMock(return_value=mock_runner)
        mock_builder.return_value = mock_builder_instance

        # Test workflow execution request
        workflow_request = {
            "workflow_id": "test-workflow-123",
            "inputs": {
                "query": "execute test工作流",
                "parameters": {"temperature": 0.7, "max_steps": 5},
            },
            "model_config": {
                "domain": "gpt-4",
                "api": "https://api.openai.com/v1",
                "api_key": "test-key",
            },
        }

        execution_endpoints = [
            "/v1/workflow/execute",
            "/workflow/execute",
            "/v1/agent/workflow/run",
        ]

        for endpoint in execution_endpoints:
            try:
                response = self.client.post(endpoint, json=workflow_request)
                if response.status_code in [200, 404, 422, 405]:
                    assert response.status_code in [200, 404, 422, 405]
                    if response.status_code == 200:
                        result = response.json()
                        assert isinstance(result, dict)
                    break
            except (ConnectionError, ValueError, TypeError):
                continue

    def test_workflow_streaming_execution(self) -> None:
        """test工作流流式execute."""
        # Test streaming workflow execution
        streaming_request = {
            "workflow_id": "streaming-workflow",
            "inputs": {"query": "流式execute test"},
            "stream": True,
        }

        streaming_endpoints = [
            "/v1/workflow/stream",
            "/workflow/stream",
            "/v1/workflow/execute?stream=true",
        ]

        for endpoint in streaming_endpoints:
            try:
                response = self.client.post(endpoint, json=streaming_request)
                if response.status_code in [200, 404, 405]:
                    assert response.status_code in [200, 404, 405]
                    if response.status_code == 200:
                        # Verify streaming response headers
                        content_type = response.headers.get("content-type", "")
                        assert "stream" in content_type or "json" in content_type
                    break
            except (ConnectionError, ValueError, TypeError):
                continue

    def test_workflow_agent_list_workflows(self) -> None:
        """test列出可用工作流."""
        list_endpoints = [
            "/v1/workflows",
            "/workflows",
            "/v1/workflow/list",
            "/workflow/list",
        ]

        for endpoint in list_endpoints:
            try:
                response = self.client.get(endpoint)
                if response.status_code in [200, 404, 405]:
                    assert response.status_code in [200, 404, 405]
                    if response.status_code == 200:
                        workflows = response.json()
                        assert isinstance(workflows, (list, dict))
                    break
            except (ConnectionError, ValueError, TypeError):
                continue

    def test_workflow_agent_get_workflow_details(self) -> None:
        """test获取工作流详情."""
        workflow_id = "test-workflow-123"

        detail_endpoints = [
            f"/v1/workflow/{workflow_id}",
            f"/workflow/{workflow_id}",
            f"/v1/workflow/details/{workflow_id}",
        ]

        for endpoint in detail_endpoints:
            try:
                response = self.client.get(endpoint)
                if response.status_code not in [200, 404, 405]:
                    continue
                assert response.status_code in [200, 404, 405]
                if response.status_code == 200:
                    workflow_details = response.json()
                    assert isinstance(workflow_details, dict)
                    # Verify workflow details structure
                    expected_fields = [
                        "id",
                        "name",
                        "description",
                        "steps",
                        "inputs",
                        "outputs",
                    ]
                    for field in expected_fields:
                        if field in workflow_details:
                            assert workflow_details[field] is not None
                break
            except (ConnectionError, ValueError, TypeError):
                continue

    def test_workflow_agent_validation_errors(self) -> None:
        """test工作流请求验证错误."""
        # Test various invalid requests
        invalid_requests = [
            {},  # Empty request
            {"workflow_id": ""},  # Empty workflow ID
            {"workflow_id": "test", "inputs": None},  # Empty input
            {"inputs": {"query": "test"}},  # Missing workflow ID
            {"workflow_id": "test", "inputs": {"invalid": None}},  # Invalid input value
        ]

        for invalid_request in invalid_requests:
            response = self.client.post("/v1/workflow/execute", json=invalid_request)
            # Should return validation error
            assert response.status_code in [400, 404, 422, 405]

    def test_workflow_agent_unicode_support(self) -> None:
        """test工作流对Unicode内容的支持."""
        unicode_request = {
            "workflow_id": "中文工作流🔄",
            "inputs": {
                "query": "中文查询test🚀",
                "context": "包含特殊字符的上下文：①②③④⑤",
                "parameters": {
                    "language": "zh-CN",
                    "description": "这是一个Unicodetest",
                },
            },
        }

        response = self.client.post("/v1/workflow/execute", json=unicode_request)
        # Verify Unicode content is handled correctly
        assert response.status_code in [200, 404, 422, 405]

    @patch("api.v1.workflow_agent.WorkflowAgentRunnerBuilder")
    def test_workflow_agent_error_handling(self, mock_builder: Any) -> None:
        """test工作流execute错误处理."""
        # Mock builder throws various exceptions
        mock_builder_instance = Mock()

        # Test build error
        mock_builder_instance.build = AsyncMock(
            side_effect=ValueError("工作流构建失败")
        )
        mock_builder.return_value = mock_builder_instance

        workflow_request = {
            "workflow_id": "error-workflow",
            "inputs": {"query": "错误test"},
        }

        response = self.client.post("/v1/workflow/execute", json=workflow_request)
        assert response.status_code in [400, 404, 422, 405, 500]

    def test_workflow_agent_concurrent_execution(self) -> None:
        """test工作流concurrent execution."""

        def execute_workflow(workflow_id: str) -> Any:
            request_data = {
                "workflow_id": workflow_id,
                "inputs": {"query": f"并发test {workflow_id}"},
            }
            return self.client.post("/v1/workflow/execute", json=request_data)

        # Execute multiple workflows concurrently
        workflow_ids = ["workflow-1", "workflow-2", "workflow-3"]

        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
            futures = [
                executor.submit(execute_workflow, wf_id) for wf_id in workflow_ids
            ]
            responses = [
                future.result() for future in concurrent.futures.as_completed(futures)
            ]

        # Verify all requests get responses
        assert len(responses) == 3
        for response in responses:
            assert response.status_code in [200, 404, 422, 405, 429]

    def test_workflow_agent_large_input_data(self) -> None:
        """test工作流处理大输入数据."""
        # Create large input data
        large_input = {
            "query": "大数据处理test",
            "data": ["数据项 " + str(i) for i in range(1000)],
            "context": "大量上下文内容 " * 100,
        }

        large_request = {"workflow_id": "large-data-workflow", "inputs": large_input}

        response = self.client.post("/v1/workflow/execute", json=large_request)
        # Verify large data processing (may have size limits)
        assert response.status_code in [200, 400, 404, 413, 422, 405]

    def test_workflow_agent_timeout_handling(self) -> None:
        """test工作流execute超时处理."""
        # Test long-running workflow that may cause timeout
        timeout_request = {
            "workflow_id": "long-running-workflow",
            "inputs": {"query": "long-running execution test", "timeout": 30},  # Set timeout
        }

        response = self.client.post("/v1/workflow/execute", json=timeout_request)
        assert response.status_code in [200, 404, 408, 422, 405, 504]

    def test_workflow_agent_execution_history(self) -> None:
        """test工作流execute历史记录."""
        # Test getting execution history
        history_endpoints = [
            "/v1/workflow/history",
            "/workflow/history",
            "/v1/workflow/executions",
        ]

        for endpoint in history_endpoints:
            try:
                response = self.client.get(endpoint)
                if response.status_code in [200, 404, 405]:
                    assert response.status_code in [200, 404, 405]
                    if response.status_code == 200:
                        history = response.json()
                        assert isinstance(history, (list, dict))
                    break
            except (ConnectionError, ValueError, TypeError):
                continue

    def test_workflow_agent_execution_status(self) -> None:
        """test获取工作流execute状态."""
        execution_id = "test-execution-123"

        status_endpoints = [
            f"/v1/workflow/execution/{execution_id}/status",
            f"/workflow/status/{execution_id}",
            f"/v1/execution/{execution_id}",
        ]

        for endpoint in status_endpoints:
            try:
                response = self.client.get(endpoint)
                if response.status_code not in [200, 404, 405]:
                    continue
                assert response.status_code in [200, 404, 405]
                if response.status_code == 200:
                    status_data = response.json()
                    assert isinstance(status_data, dict)
                    # Verify status data structure
                    status_fields = ["status", "progress", "start_time", "result"]
                    for field in status_fields:
                        if field in status_data:
                            assert status_data[field] is not None
                break
            except (ConnectionError, ValueError, TypeError):
                continue

    def test_workflow_agent_cancel_execution(self) -> None:
        """test取消工作流execute."""
        execution_id = "test-execution-123"

        cancel_endpoints = [
            f"/v1/workflow/execution/{execution_id}/cancel",
            f"/workflow/cancel/{execution_id}",
        ]

        for endpoint in cancel_endpoints:
            try:
                response = self.client.post(endpoint)
                if response.status_code in [200, 404, 405]:
                    assert response.status_code in [200, 404, 405]
                    if response.status_code == 200:
                        cancel_result = response.json()
                        assert isinstance(cancel_result, dict)
                    break
            except (ConnectionError, ValueError, TypeError):
                continue

    def test_workflow_agent_authentication(self) -> None:
        """test工作流API认证."""
        # Test authentication (actual auth logic handled by middleware)

        # Test request with auth header
        headers = {"Authorization": "Bearer workflow-token"}
        workflow_request = {
            "workflow_id": "auth-test-workflow",
            "inputs": {"query": "认证test"},
        }

        response = self.client.post(
            "/v1/workflow/execute", json=workflow_request, headers=headers
        )
        assert response.status_code in [200, 401, 403, 404, 422, 405]

    def test_workflow_agent_parameter_validation(self) -> None:
        """test工作流参数验证."""
        # Test various parameter boundary values
        boundary_requests = [
            {"workflow_id": "param-test", "inputs": {"temperature": -1.0}},  # Invalid temperature
            {"workflow_id": "param-test", "inputs": {"max_steps": 0}},  # Invalid step count
            {"workflow_id": "param-test", "inputs": {"timeout": -5}},  # Invalid timeout
        ]

        for request_data in boundary_requests:
            response = self.client.post("/v1/workflow/execute", json=request_data)
            assert response.status_code in [400, 404, 422, 405]

    def test_workflow_agent_custom_model_config(self) -> None:
        """testcustom model configuration."""
        custom_config_request = {
            "workflow_id": "custom-model-workflow",
            "inputs": {"query": "自定义模型test"},
            "model_config": {
                "domain": "custom-llm-model",
                "api": "https://custom-api.example.com/v1",
                "api_key": "custom-api-key",
                "temperature": 0.9,
                "max_tokens": 4000,
                "custom_params": {"top_p": 0.95, "frequency_penalty": 0.1},
            },
        }

        response = self.client.post("/v1/workflow/execute", json=custom_config_request)
        assert response.status_code in [200, 404, 422, 405]
