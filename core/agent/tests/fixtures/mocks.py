"""
testMock对象和工具.

提供各种Mock类和工具函数，用于单元test和集成test。
包含MySQL客户端、Redis客户端、OpenAI客户端等Mock实现。
"""

from typing import Any, Dict, List, Optional
from unittest.mock import AsyncMock, Mock

from tests.fixtures.test_data import TestDataFactory


class MockMysqlClient:
    """MySQL客户端Mock类.

    提供MySQL数据库操作的Mock实现，支持表数据设置和查询模拟。
    """

    def __init__(self) -> None:
        self.execute_sql = AsyncMock()
        self.fetch_one = AsyncMock()
        self.fetch_all = AsyncMock()
        self.close = AsyncMock()
        self._data_store: Dict[str, List[Dict[str, Any]]] = {}

    def set_table_data(self, table_name: str, data: List[Dict[str, Any]]) -> None:
        """Set mock data for a table."""
        self._data_store[table_name] = data

    async def mock_fetch_one(
        self,
        sql: str,
        params: Optional[tuple] = None,  # pylint: disable=unused-argument
    ) -> Optional[Dict[str, Any]]:
        """Mock fetch_one with predefined data."""
        # Simple mock implementation
        if "bot_config" in sql.lower():
            if self._data_store.get("bot_config"):
                return self._data_store["bot_config"][0]
        return None

    async def mock_fetch_all(
        self,
        sql: str,
        params: Optional[tuple] = None,  # pylint: disable=unused-argument
    ) -> List[Dict[str, Any]]:
        """Mock fetch_all with predefined data."""
        if "bot_config" in sql.lower():
            return self._data_store.get("bot_config", [])
        return []


class MockRedisClient:
    """Redis客户端Mock类.

    提供Redis缓存操作的Mock实现，支持缓存数据设置和查询模拟。
    """

    def __init__(self) -> None:
        self.get = AsyncMock()
        self.set = AsyncMock()
        self.delete = AsyncMock()
        self.exists = AsyncMock()
        self.expire = AsyncMock()
        self.close = AsyncMock()
        self._cache: Dict[str, Any] = {}

    def set_cache_data(self, key: str, value: Any) -> None:
        """Set mock cache data."""
        self._cache[key] = value

    async def mock_get(self, key: str) -> Any:
        """Mock get with predefined data."""
        return self._cache.get(key)

    async def mock_set(
        self,
        key: str,
        value: Any,
        ex: Optional[int] = None,  # pylint: disable=unused-argument
    ) -> bool:
        """Mock set operation."""
        self._cache[key] = value
        return True

    async def mock_exists(self, key: str) -> bool:
        """Mock exists check."""
        return key in self._cache


class MockOpenAIClient:
    """OpenAI客户端Mock类.

    提供OpenAI API调用的Mock实现，支持响应设置和流式响应模拟。
    """

    def __init__(self) -> None:
        self.chat = Mock()
        self.chat.completions = Mock()
        self.chat.completions.create = AsyncMock()
        self._responses: List[Any] = []
        self._response_index = 0

    def add_response(self, content: str, **kwargs: Any) -> None:
        """Add a mock response."""
        response = TestDataFactory.create_llm_response(content=content, **kwargs)
        self._responses.append(response)

    def add_streaming_response(self, content_chunks: List[str]) -> None:
        """Add a mock streaming response."""
        chunks = []
        for i, chunk in enumerate(content_chunks):
            chunks.append(
                {
                    "id": f"chunk_{i}",
                    "object": "chat.completion.chunk",
                    "created": 1234567890,
                    "model": "gpt-3.5-turbo",
                    "choices": [
                        {
                            "index": 0,
                            "delta": {"content": chunk},
                            "finish_reason": (
                                "stop" if i == len(content_chunks) - 1 else None
                            ),
                        }
                    ],
                }
            )
        self._responses.append(chunks)

    async def mock_create_completion(self, **kwargs: Any) -> Any:
        """Mock completion creation."""
        if self._response_index < len(self._responses):
            response = self._responses[self._response_index]
            self._response_index += 1

            if kwargs.get("stream", False):
                # Return async generator for streaming
                async def stream_generator() -> Any:
                    for chunk in response:
                        yield chunk

                return stream_generator()
            return Mock(**response)
        # Default response if no mock responses set
        return Mock(**TestDataFactory.create_llm_response())


class MockPlugin:
    """Mock plugin for testing."""

    def __init__(self, plugin_type: str = "test"):
        self.plugin_type = plugin_type
        self.enabled = True
        self.config: Dict[str, Any] = {}
        self.execute = AsyncMock()
        self.validate_config = Mock(return_value=True)

    async def mock_execute(
        self, *args: Any, **kwargs: Any  # pylint: disable=unused-argument
    ) -> Dict[str, Any]:
        """Mock plugin execution."""
        return {
            "success": True,
            "data": f"Mock {self.plugin_type} plugin executed",
            "plugin_type": self.plugin_type,
        }


class MockWorkflowEngine:
    """Mock workflow engine for testing."""

    def __init__(self) -> None:
        self.execute_workflow = AsyncMock()
        self.validate_workflow = Mock(return_value=True)
        self.get_workflow_status = AsyncMock()
        self._workflows: Dict[str, Dict[str, Any]] = {}

    def add_workflow(self, workflow_id: str, workflow_config: Dict[str, Any]) -> None:
        """Add a mock workflow."""
        self._workflows[workflow_id] = workflow_config

    async def mock_execute_workflow(
        self,
        workflow_id: str,
        inputs: Dict[str, Any],  # pylint: disable=unused-argument
    ) -> Dict[str, Any]:
        """Mock workflow execution."""
        if workflow_id in self._workflows:
            return {
                "workflow_id": workflow_id,
                "status": "completed",
                "outputs": {"result": "Mock workflow executed successfully"},
                "execution_time": 1.23,
            }
        raise ValueError(f"Workflow {workflow_id} not found")


def create_mock_fastapi_request(
    method: str = "POST",
    url: str = "/api/v1/test",
    headers: Optional[Dict[str, str]] = None,
    json_data: Optional[Dict[str, Any]] = None,
) -> Mock:
    """Create a mock FastAPI request."""
    mock_request = Mock()
    mock_request.method = method
    mock_request.url = Mock(path=url)
    mock_request.headers = headers or {}

    if json_data:
        mock_request.json = AsyncMock(return_value=json_data)

    return mock_request


def create_mock_response(
    status_code: int = 200,
    json_data: Optional[Dict[str, Any]] = None,
    headers: Optional[Dict[str, str]] = None,
) -> Mock:
    """Create a mock HTTP response."""
    mock_response = Mock()
    mock_response.status_code = status_code
    mock_response.headers = headers or {}

    if json_data:
        mock_response.json = Mock(return_value=json_data)

    return mock_response
