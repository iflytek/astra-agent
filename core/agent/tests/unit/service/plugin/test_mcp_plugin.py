"""
Unit tests for service.plugin.mcp
"""

import asyncio
from typing import Any, List
from unittest.mock import AsyncMock, Mock, patch

import pytest

from common_imports import Span
from exceptions.plugin_exc import GetMcpPluginExc, RunMcpPluginExc
from service.plugin.base import BasePlugin, PluginResponse
from service.plugin.mcp import McpPlugin, McpPluginFactory, McpPluginRunner


class AsyncContextManager:
    """Proper async context manager for mocking aiohttp responses."""

    def __init__(self, return_value: Any) -> None:
        self._return_value = return_value

    async def __aenter__(self) -> Any:
        return self._return_value

    async def __aexit__(self, exc_type: Any, exc_val: Any, exc_tb: Any) -> None:
        return None


class TestMcpPlugin:
    """Test cases for McpPlugin class."""

    @pytest.mark.unit
    def test_mcp_plugin_inherits_from_base_plugin(self) -> None:
        """Test McpPlugin inherits from BasePlugin."""
        plugin = McpPlugin(
            server_id="test_server",
            server_url="http://test-server.com",
            name="test_plugin",
            description="A test plugin",
            schema_template="test_schema",
            typ="mcp",
            run=AsyncMock(),
        )
        assert isinstance(plugin, BasePlugin)

    @pytest.mark.unit
    def test_mcp_plugin_creation(self) -> None:
        """Test McpPlugin can be created with required fields."""
        plugin = McpPlugin(
            server_id="test_server",
            server_url="http://test-server.com",
            name="test_plugin",
            description="A test plugin for unit testing",
            schema_template="test_schema",
            typ="mcp",
            run=AsyncMock(),
        )

        assert plugin.server_id == "test_server"
        assert plugin.server_url == "http://test-server.com"
        assert plugin.name == "test_plugin"
        assert plugin.description == "A test plugin for unit testing"

    @pytest.mark.unit
    def test_mcp_plugin_creation_with_defaults(self) -> None:
        """Test McpPlugin creation with default values."""
        plugin = McpPlugin(
            name="test_plugin",
            description="test",
            schema_template="test_schema",
            typ="mcp",
            run=AsyncMock(),
        )

        assert plugin.server_id == ""
        assert plugin.server_url == ""


class TestMcpPluginRunner:
    """Test cases for McpPluginRunner class."""

    @pytest.fixture
    def mcp_runner(self) -> McpPluginRunner:
        """Create a McpPluginRunner instance for testing."""
        return McpPluginRunner(
            server_id="test_server",
            server_url="http://test-server.com",
            sid="test_session",
            name="test_tool",
        )

    @pytest.mark.unit
    def test_mcp_plugin_runner_creation(self, mcp_runner: McpPluginRunner) -> None:
        """Test McpPluginRunner can be created."""
        assert mcp_runner.server_id == "test_server"
        assert mcp_runner.server_url == "http://test-server.com"
        assert mcp_runner.sid == "test_session"
        assert mcp_runner.name == "test_tool"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_success(self, mcp_runner: McpPluginRunner) -> None:
        """Test successful McpPluginRunner execution."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        mock_span_context.sid = "span_session_id"
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        action_input = {"param1": "value1", "param2": "value2"}

        expected_response = {
            "code": 0,
            "sid": "response_session_id",
            "data": {"result": "success", "message": "Tool executed successfully"},
        }

        # Create mock response
        mock_response = Mock()
        mock_response.status = 200
        mock_response.json = AsyncMock(return_value=expected_response)
        mock_response.raise_for_status = Mock()

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch(
            "service.plugin.mcp.aiohttp.ClientSession", return_value=session_context
        ):
            with patch("service.plugin.mcp.agent_config") as mock_config:
                mock_config.RUN_MCP_PLUGIN_URL = "http://mcp-api/run"

                # Act
                result = await mcp_runner.run(action_input, mock_span)

                # Assert
                assert isinstance(result, PluginResponse)
                assert result.code == 0
                assert result.sid == "response_session_id"
                assert result.result == expected_response
                assert len(result.log) == 1
                assert result.log[0]["name"] == "test_tool"
                assert result.log[0]["input"] == action_input
                assert result.log[0]["output"] == expected_response

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_constructs_correct_request(
        self, mcp_runner: McpPluginRunner
    ) -> None:
        """Test run constructs the correct request data."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        mock_span_context.sid = "span_session_id"
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        action_input = {"param1": "value1", "param2": "value2"}

        expected_response = {
            "code": 0,
            "sid": "response_session_id",
        }

        # Create mock response
        mock_response = Mock()
        mock_response.status = 200
        mock_response.json = AsyncMock(return_value=expected_response)
        mock_response.raise_for_status = Mock()

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch(
            "service.plugin.mcp.aiohttp.ClientSession", return_value=session_context
        ):
            with patch("service.plugin.mcp.agent_config") as mock_config:
                mock_config.RUN_MCP_PLUGIN_URL = "http://mcp-api/run"

                # Act
                await mcp_runner.run(action_input, mock_span)

                # Assert - Check the post was called with correct data
                expected_data = {
                    "mcp_server_id": "test_server",
                    "mcp_server_url": "http://test-server.com",
                    "tool_name": "test_tool",
                    "tool_args": action_input,
                    "sid": "span_session_id",
                }

                mock_session.post.assert_called_once()
                call_args = mock_session.post.call_args
                assert call_args[0][0] == "http://mcp-api/run"  # URL
                assert call_args[1]["json"] == expected_data  # JSON data
                assert call_args[1]["headers"] == {"Content-Type": "application/json"}

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_http_error(self, mcp_runner: McpPluginRunner) -> None:
        """Test run handles HTTP errors correctly."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        mock_span_context.sid = "span_session_id"
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        action_input = {"param1": "value1"}

        # Create mock response that raises HTTP error
        mock_response = Mock()
        mock_response.status = 500
        mock_response.raise_for_status = Mock(side_effect=Exception("HTTP 500 Error"))

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch(
            "service.plugin.mcp.aiohttp.ClientSession", return_value=session_context
        ):
            with patch("service.plugin.mcp.agent_config") as mock_config:
                mock_config.RUN_MCP_PLUGIN_URL = "http://mcp-api/run"

                # Act & Assert
                with pytest.raises(Exception):
                    await mcp_runner.run(action_input, mock_span)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_timeout_error(self, mcp_runner: McpPluginRunner) -> None:
        """Test run handles timeout errors correctly."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        mock_span_context.sid = "span_session_id"
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        action_input = {"param1": "value1"}

        # Mock ClientSession context manager to raise timeout in session creation
        async def timeout_session() -> None:
            raise asyncio.TimeoutError("Request timeout")

        session_context = AsyncMock()
        session_context.__aenter__ = AsyncMock(side_effect=timeout_session)
        session_context.__aexit__ = AsyncMock(return_value=None)

        with patch(
            "service.plugin.mcp.aiohttp.ClientSession", return_value=session_context
        ):
            with patch("service.plugin.mcp.agent_config") as mock_config:
                mock_config.RUN_MCP_PLUGIN_URL = "http://mcp-api/run"

                # Act & Assert
                with pytest.raises(type(RunMcpPluginExc)):
                    await mcp_runner.run(action_input, mock_span)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_logs_span_events(self, mcp_runner: McpPluginRunner) -> None:
        """Test run logs appropriate span events."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        mock_span_context.sid = "span_session_id"
        mock_span_context.add_info_events = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        action_input = {"param1": "value1"}
        expected_response = {"code": 0, "sid": "response_session_id"}

        # Create mock response
        mock_response = Mock()
        mock_response.status = 200
        mock_response.json = AsyncMock(return_value=expected_response)
        mock_response.raise_for_status = Mock()

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch(
            "service.plugin.mcp.aiohttp.ClientSession", return_value=session_context
        ):
            with patch("service.plugin.mcp.agent_config") as mock_config:
                mock_config.RUN_MCP_PLUGIN_URL = "http://mcp-api/run"

                # Act
                await mcp_runner.run(action_input, mock_span)

                # Assert - Check span events were logged
                assert (
                    mock_span_context.add_info_events.call_count == 2
                )  # Input and output events


class TestMcpPluginFactory:
    """Test cases for McpPluginFactory class."""

    @pytest.fixture
    def factory(self) -> McpPluginFactory:
        """Create a McpPluginFactory instance for testing."""
        return McpPluginFactory(
            app_id="test_app",
            mcp_server_ids=["server1", "server2"],
            mcp_server_urls=["http://server1.com", "http://server2.com"],
        )

    @pytest.mark.unit
    def test_mcp_plugin_factory_creation(self, factory: McpPluginFactory) -> None:
        """Test McpPluginFactory can be created."""
        assert factory.app_id == "test_app"
        assert factory.mcp_server_ids == ["server1", "server2"]
        assert factory.mcp_server_urls == ["http://server1.com", "http://server2.com"]

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_query_servers_success(self, factory: McpPluginFactory) -> None:
        """Test successful query_servers execution."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        mock_span_context.sid = "span_session_id"
        mock_span_context.add_info_events = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        expected_response = {
            "code": 0,
            "data": {
                "servers": [
                    {
                        "server_id": "server1",
                        "server_url": "http://server1.com",
                        "server_status": 0,
                        "tools": [{"name": "tool1", "description": "Tool 1"}],
                    }
                ]
            },
        }

        # Create mock response
        mock_response = Mock()
        mock_response.status = 200
        mock_response.json = AsyncMock(return_value=expected_response)
        mock_response.raise_for_status = Mock()

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch(
            "service.plugin.mcp.aiohttp.ClientSession", return_value=session_context
        ):
            with patch("service.plugin.mcp.agent_config") as mock_config:
                mock_config.LIST_MCP_PLUGIN_URL = "http://mcp-api/list"

                # Act
                result = await factory.query_servers(mock_span)

                # Assert
                assert len(result) == 1
                assert result[0]["server_id"] == "server1"
                assert result[0]["server_url"] == "http://server1.com"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_query_servers_constructs_correct_request(
        self, factory: McpPluginFactory
    ) -> None:
        """Test query_servers constructs the correct request data."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        mock_span_context.sid = "span_session_id"
        mock_span_context.add_info_events = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        expected_response = {"code": 0, "data": {"servers": []}}

        # Create mock response
        mock_response = Mock()
        mock_response.status = 200
        mock_response.json = AsyncMock(return_value=expected_response)
        mock_response.raise_for_status = Mock()

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch(
            "service.plugin.mcp.aiohttp.ClientSession", return_value=session_context
        ):
            with patch("service.plugin.mcp.agent_config") as mock_config:
                mock_config.LIST_MCP_PLUGIN_URL = "http://mcp-api/list"

                # Act
                await factory.query_servers(mock_span)

                # Assert - Check the post was called with correct data
                expected_data = {
                    "sid": "span_session_id",
                    "mcp_server_ids": ["server1", "server2"],
                    "mcp_server_urls": ["http://server1.com", "http://server2.com"],
                }

                mock_session.post.assert_called_once()
                call_args = mock_session.post.call_args
                assert call_args[0][0] == "http://mcp-api/list"  # URL
                assert call_args[1]["json"] == expected_data  # JSON data

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_query_servers_api_error(self, factory: McpPluginFactory) -> None:
        """Test query_servers handles API errors correctly."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        mock_span_context.sid = "span_session_id"
        mock_span_context.add_info_events = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        # API returns error code
        error_response = {"code": 400, "message": "Invalid request"}

        # Create mock response
        mock_response = Mock()
        mock_response.status = 200
        mock_response.json = AsyncMock(return_value=error_response)
        mock_response.raise_for_status = Mock()

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch(
            "service.plugin.mcp.aiohttp.ClientSession", return_value=session_context
        ):
            with patch("service.plugin.mcp.agent_config") as mock_config:
                mock_config.LIST_MCP_PLUGIN_URL = "http://mcp-api/list"

                # Act & Assert
                with pytest.raises(type(GetMcpPluginExc)):
                    await factory.query_servers(mock_span)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_query_servers_http_error(self, factory: McpPluginFactory) -> None:
        """Test query_servers handles HTTP errors correctly."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        mock_span_context.sid = "span_session_id"
        mock_span_context.add_info_events = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        # Create mock response that raises HTTP error
        mock_response = Mock()
        mock_response.status = 500
        mock_response.raise_for_status = Mock(side_effect=Exception("HTTP 500 Error"))

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch(
            "service.plugin.mcp.aiohttp.ClientSession", return_value=session_context
        ):
            with patch("service.plugin.mcp.agent_config") as mock_config:
                mock_config.LIST_MCP_PLUGIN_URL = "http://mcp-api/list"

                # Act & Assert
                with pytest.raises(Exception):
                    await factory.query_servers(mock_span)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_query_servers_timeout_error(self, factory: McpPluginFactory) -> None:
        """Test query_servers handles timeout errors correctly."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        mock_span_context.sid = "span_session_id"
        mock_span_context.add_info_events = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        # Mock ClientSession context manager to raise timeout in session creation
        async def timeout_session() -> None:
            raise asyncio.TimeoutError("Request timeout")

        session_context = AsyncMock()
        session_context.__aenter__ = AsyncMock(side_effect=timeout_session)
        session_context.__aexit__ = AsyncMock(return_value=None)

        with patch(
            "service.plugin.mcp.aiohttp.ClientSession", return_value=session_context
        ):
            with patch("service.plugin.mcp.agent_config") as mock_config:
                mock_config.LIST_MCP_PLUGIN_URL = "http://mcp-api/list"

                # Act & Assert
                with pytest.raises(type(GetMcpPluginExc)):
                    await factory.query_servers(mock_span)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_convert_tool(self) -> None:
        """Test convert_tool static method."""
        tool = {
            "name": "test_tool",
            "description": "A test tool",
            "inputSchema": {
                "properties": {
                    "param1": {"type": "string"},
                    "param2": {"type": "integer"},
                },
                "required": ["param1"],
            },
        }

        result = await McpPluginFactory.convert_tool(tool)

        assert "tool_name:test_tool" in result
        assert "tool_description:A test tool" in result
        assert "tool_parameters:" in result
        assert "param1" in result
        assert "param2" in result

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_tools_success(self, factory: McpPluginFactory) -> None:
        """Test successful build_tools execution."""
        # Arrange
        mock_span = Mock(spec=Span)
        servers_data = [
            {
                "server_id": "server1",
                "server_url": "http://server1.com",
                "server_status": 0,
                "tools": [
                    {
                        "name": "tool1",
                        "description": "Tool 1",
                        "inputSchema": {"properties": {}, "required": []},
                    },
                    {
                        "name": "tool2",
                        "description": "Tool 2",
                        "inputSchema": {"properties": {}, "required": []},
                    },
                ],
            }
        ]

        # Use patch to mock query_servers method
        with patch(
            "service.plugin.mcp.McpPluginFactory.query_servers", new_callable=AsyncMock
        ) as mock_query:
            mock_query.return_value = servers_data

            # Act
            result = await factory.build_tools(mock_span)

            # Assert
            assert len(result) == 2
            assert all(isinstance(plugin, McpPlugin) for plugin in result)
            assert result[0].name == "tool1"
            assert result[1].name == "tool2"
            assert result[0].server_id == "server1"
            assert result[1].server_id == "server1"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_tools_server_error(self, factory: McpPluginFactory) -> None:
        """Test build_tools handles server errors correctly."""
        # Arrange
        mock_span = Mock(spec=Span)
        servers_data = [
            {
                "server_id": "server1",
                "server_url": "http://server1.com",
                "server_status": 1,  # Error status
                "server_message": "Server error occurred",
                "tools": [],
            }
        ]

        # Use patch to mock query_servers method
        with patch(
            "service.plugin.mcp.McpPluginFactory.query_servers", new_callable=AsyncMock
        ) as mock_query:
            mock_query.return_value = servers_data

            # Act & Assert
            with pytest.raises(type(GetMcpPluginExc)):
                await factory.build_tools(mock_span)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_gen_calls_build_tools(self, factory: McpPluginFactory) -> None:
        """Test gen method calls build_tools."""
        # Arrange
        mock_span = Mock(spec=Span)
        expected_tools = [
            McpPlugin(
                name="test_tool",
                description="test",
                server_id="server1",
                schema_template="test_schema",
                typ="mcp",
                run=AsyncMock(),
            )
        ]

        # Use patch to mock build_tools method
        with patch(
            "service.plugin.mcp.McpPluginFactory.build_tools", new_callable=AsyncMock
        ) as mock_build:
            mock_build.return_value = expected_tools

            # Act
            result = await factory.gen(mock_span)

            # Assert
            mock_build.assert_called_once_with(mock_span)
            assert result == expected_tools

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_tools_with_empty_servers(
        self, factory: McpPluginFactory
    ) -> None:
        """Test build_tools with empty servers list."""
        # Arrange
        mock_span = Mock(spec=Span)
        servers_data: List[Any] = []

        # Use patch to mock query_servers method
        with patch(
            "service.plugin.mcp.McpPluginFactory.query_servers", new_callable=AsyncMock
        ) as mock_query:
            mock_query.return_value = servers_data

            # Act
            result = await factory.build_tools(mock_span)

            # Assert
            assert result == []

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_convert_tool_with_empty_schema(self) -> None:
        """Test convert_tool with empty input schema."""
        tool = {"name": "simple_tool", "description": "Simple tool", "inputSchema": {}}

        result = await McpPluginFactory.convert_tool(tool)

        assert "tool_name:simple_tool" in result
        assert "tool_description:Simple tool" in result
        assert "tool_parameters:" in result

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_tools_with_unicode_content(
        self, factory: McpPluginFactory
    ) -> None:
        """Test build_tools handles Unicode content correctly."""
        # Arrange
        mock_span = Mock(spec=Span)
        servers_data = [
            {
                "server_id": "server1",
                "server_url": "http://server1.com",
                "server_status": 0,
                "tools": [
                    {
                        "name": "中文工具",
                        "description": "这是一个中文工具描述",
                        "inputSchema": {"properties": {}, "required": []},
                    }
                ],
            }
        ]

        # Use patch to mock query_servers method
        with patch(
            "service.plugin.mcp.McpPluginFactory.query_servers", new_callable=AsyncMock
        ) as mock_query:
            mock_query.return_value = servers_data

            # Act
            result = await factory.build_tools(mock_span)

            # Assert
            assert len(result) == 1
            assert result[0].name == "中文工具"
            assert result[0].description == "这是一个中文工具描述"
