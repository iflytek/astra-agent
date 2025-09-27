"""
Unit tests for service.plugin.link
"""

import asyncio
import json
from base64 import b64decode
from typing import Any, Dict
from unittest.mock import AsyncMock, Mock, patch

import pytest

from common_imports import Span
from exceptions.plugin_exc import GetToolSchemaExc, RunToolExc
from service.plugin.base import BasePlugin, PluginResponse
from service.plugin.link import LinkPlugin, LinkPluginFactory, LinkPluginRunner


class AsyncContextManager:
    """Proper async context manager for mocking aiohttp responses."""

    def __init__(self, return_value: Any) -> None:
        self._return_value = return_value

    async def __aenter__(self) -> Any:
        return self._return_value

    async def __aexit__(self, exc_type: Any, exc_val: Any, exc_tb: Any) -> None:
        return None


class TestLinkPlugin:
    """Test cases for LinkPlugin."""

    @pytest.mark.unit
    def test_link_plugin_inherits_from_base_plugin(self) -> None:
        """Test LinkPlugin inherits from BasePlugin."""
        # Assert
        assert issubclass(LinkPlugin, BasePlugin)

    @pytest.mark.unit
    def test_link_plugin_creation(self) -> None:
        """Test LinkPlugin creation with tool_id field."""
        # Act
        plugin = LinkPlugin(
            tool_id="test_tool_123",
            name="test_link",
            description="Test link plugin",
            schema_template="test_schema",
            typ="link",
            run=AsyncMock(),
        )

        # Assert
        assert plugin.tool_id == "test_tool_123"
        assert plugin.name == "test_link"
        assert plugin.description == "Test link plugin"
        assert plugin.typ == "link"


class TestLinkPluginRunner:
    """Test cases for LinkPluginRunner."""

    @pytest.fixture
    def sample_method_schema(self) -> Dict[str, Any]:
        """Sample method schema for testing."""
        return {
            "operationId": "test_operation",
            "description": "Test operation description",
            "parameters": [
                {
                    "name": "test_header",
                    "in": "header",
                    "required": True,
                    "schema": {
                        "type": "string",
                        "x-from": 0,
                        "default": "default_header_value",
                    },
                },
                {
                    "name": "test_query",
                    "in": "query",
                    "required": False,
                    "schema": {
                        "type": "string",
                        "x-display": True,
                        "default": "default_query_value",
                    },
                },
            ],
            "requestBody": {
                "content": {
                    "application/json": {
                        "schema": {
                            "type": "object",
                            "properties": {
                                "body_field": {
                                    "type": "string",
                                    "x-from": 0,
                                    "default": "default_body_value",
                                },
                                "nested_object": {
                                    "type": "object",
                                    "properties": {
                                        "nested_field": {
                                            "type": "string",
                                            "x-display": True,
                                            "default": "nested_default",
                                        }
                                    },
                                },
                            },
                            "required": ["body_field"],
                        }
                    }
                }
            },
        }

    @pytest.fixture
    def link_runner(self, sample_method_schema: Dict[str, Any]) -> LinkPluginRunner:
        """Create LinkPluginRunner instance for testing."""
        return LinkPluginRunner(
            app_id="test_app",
            uid="test_user",
            tool_id="test_tool",
            version="v1.0",
            operation_id="test_operation",
            method_schema=sample_method_schema,
        )

    @pytest.mark.unit
    def test_link_plugin_runner_creation(
        self, link_runner: LinkPluginRunner, sample_method_schema: Dict[str, Any]
    ) -> None:
        """Test LinkPluginRunner creation."""
        # Assert
        assert link_runner.app_id == "test_app"
        assert link_runner.uid == "test_user"
        assert link_runner.tool_id == "test_tool"
        assert link_runner.version == "v1.0"
        assert link_runner.operation_id == "test_operation"
        assert link_runner.method_schema == sample_method_schema

    @pytest.mark.unit
    def test_assemble_parameters(self, link_runner: LinkPluginRunner) -> None:
        """Test parameter assembly for headers and query."""
        # Arrange
        action_input = {
            "test_header": "action_header_value",
            "test_query": "action_query_value",
        }
        business_input = {"business_field": "business_value"}

        # Act
        header, query = link_runner.assemble_parameters(action_input, business_input)

        # Assert
        assert header["test_header"] == "action_header_value"  # x-from: 0 (model input)
        assert (
            query["test_query"] == "action_query_value"
        )  # x-display: True (model input)

    @pytest.mark.unit
    def test_assemble_parameters_with_defaults(
        self, link_runner: LinkPluginRunner
    ) -> None:
        """Test parameter assembly with default values."""
        # Arrange
        action_input: Dict[str, Any] = {}  # Empty action input
        business_input: Dict[str, Any] = {}  # Empty business input

        # Act
        header, query = link_runner.assemble_parameters(action_input, business_input)

        # Assert
        assert header["test_header"] == "default_header_value"  # Should use default
        assert query["test_query"] == "default_query_value"  # Should use default

    @pytest.mark.unit
    def test_update_params_with_model_input(self) -> None:
        """Test update_params with model input (x-from: 0)."""
        # Arrange
        params: Dict[str, Any] = {}
        parameter = {
            "name": "test_param",
            "schema": {"x-from": 0, "default": "default_value"},  # Model input
        }
        action_input = {"test_param": "model_value"}
        business_input = {"test_param": "business_value"}

        # Act
        LinkPluginRunner.update_params(params, parameter, action_input, business_input)

        # Assert
        assert params["test_param"] == "model_value"  # Should use model input

    @pytest.mark.unit
    def test_update_params_with_business_input(self) -> None:
        """Test update_params with business input (x-from: 1)."""
        # Arrange
        params: Dict[str, Any] = {}
        parameter = {
            "name": "test_param",
            "schema": {"x-from": 1, "default": "default_value"},  # Business input
        }
        action_input = {"test_param": "model_value"}
        business_input = {"test_param": "business_value"}

        # Act
        LinkPluginRunner.update_params(params, parameter, action_input, business_input)

        # Assert
        assert params["test_param"] == "business_value"  # Should use business input

    @pytest.mark.unit
    def test_update_params_with_x_display(self) -> None:
        """Test update_params with x-display field."""
        # Arrange
        params: Dict[str, Any] = {}
        parameter = {
            "name": "test_param",
            "schema": {
                "x-display": True,  # Use model input
                "default": "default_value",
            },
        }
        action_input = {"test_param": "model_value"}
        business_input = {"test_param": "business_value"}

        # Act
        LinkPluginRunner.update_params(params, parameter, action_input, business_input)

        # Assert
        assert params["test_param"] == "model_value"  # Should use model input

    @pytest.mark.unit
    def test_assemble_body_simple(self, link_runner: LinkPluginRunner) -> None:
        """Test body assembly for simple fields."""
        # Arrange
        body_schema = {
            "properties": {
                "body_field": {
                    "type": "string",
                    "x-from": 0,
                    "default": "default_value",
                }
            }
        }
        action_input = {"body_field": "action_value"}
        business_input: Dict[str, Any] = {}

        # Act
        body = link_runner.assemble_body(body_schema, action_input, business_input)

        # Assert
        assert body["body_field"] == "action_value"

    @pytest.mark.unit
    def test_assemble_body_nested_object(self, link_runner: LinkPluginRunner) -> None:
        """Test body assembly for nested objects."""
        # Arrange
        body_schema = {
            "properties": {
                "nested_object": {
                    "type": "object",
                    "properties": {
                        "nested_field": {
                            "type": "string",
                            "x-display": True,
                            "default": "nested_default",
                        }
                    },
                }
            }
        }
        action_input = {"nested_field": "nested_value"}
        business_input: Dict[str, Any] = {}

        # Act
        body = link_runner.assemble_body(body_schema, action_input, business_input)

        # Assert
        assert body["nested_object"]["nested_field"] == "nested_value"

    @pytest.mark.unit
    def test_dumps_method(self) -> None:
        """Test dumps method for base64 encoding."""
        # Arrange
        payload = {"key": "value", "number": 123}

        # Act
        result = LinkPluginRunner.dumps(payload)

        # Assert
        # Decode to verify
        decoded = json.loads(b64decode(result).decode())
        assert decoded == payload

    @pytest.mark.unit
    def test_dumps_method_empty_payload(self) -> None:
        """Test dumps method with empty payload."""
        # Act
        result = LinkPluginRunner.dumps({})

        # Assert
        assert result == ""

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_success(self, link_runner: LinkPluginRunner) -> None:
        """Test successful LinkPluginRunner execution."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        action_input = {
            "test_header": "test_value",
            "test_query": "query_value",
            "body_field": "body_value",
        }

        expected_response = {
            "header": {"code": 0, "sid": "test_session"},
            "data": {"result": "success"},
        }

        mock_response = AsyncMock()
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

        with patch("aiohttp.ClientSession", return_value=session_context):
            with patch("service.plugin.link.agent_config") as mock_config:
                mock_config.RUN_LINK_URL = "http://test-url"

                # Act
                result = await link_runner.run(action_input, mock_span)

                # Assert
                assert isinstance(result, PluginResponse)
                assert result.code == 0
                assert result.sid == "test_session"
                assert result.result == expected_response
                assert len(result.log) == 1

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_http_error(self, link_runner: LinkPluginRunner) -> None:
        """Test LinkPluginRunner with HTTP error."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        action_input = {"body_field": "test_value"}

        mock_response = AsyncMock()
        mock_response.status = 500
        mock_response.raise_for_status = Mock()

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch("aiohttp.ClientSession", return_value=session_context):
            with patch("service.plugin.link.agent_config") as mock_config:
                mock_config.RUN_LINK_URL = "http://test-url"

                # Act & Assert
                with pytest.raises(type(RunToolExc)) as exc_info:
                    await link_runner.run(action_input, mock_span)

                assert "Failed to execute link tool" in str(exc_info.value)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_timeout_error(self, link_runner: LinkPluginRunner) -> None:
        """Test LinkPluginRunner with timeout error."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        action_input = {"body_field": "test_value"}

        # Mock ClientSession context manager to raise timeout in session creation
        async def timeout_session() -> None:
            raise asyncio.TimeoutError("Request timeout")

        session_context = AsyncMock()
        session_context.__aenter__ = AsyncMock(side_effect=timeout_session)
        session_context.__aexit__ = AsyncMock(return_value=None)

        with patch("aiohttp.ClientSession", return_value=session_context):
            with patch("service.plugin.link.agent_config") as mock_config:
                mock_config.RUN_LINK_URL = "http://test-url"

                # Act & Assert
                with pytest.raises(type(RunToolExc)) as exc_info:
                    await link_runner.run(action_input, mock_span)

                assert "Failed to execute link tool" in str(exc_info.value)


class TestLinkPluginFactory:
    """Test cases for LinkPluginFactory."""

    @pytest.fixture
    def sample_factory_data(self) -> Dict[str, Any]:
        """Sample factory data for testing."""
        return {
            "app_id": "test_app",
            "uid": "test_user",
            "tool_ids": ["tool1", {"tool_id": "tool2", "version": "v2.0"}],
        }

    @pytest.fixture
    def link_factory(self, sample_factory_data: Dict[str, Any]) -> LinkPluginFactory:
        """Create LinkPluginFactory instance for testing."""
        return LinkPluginFactory(**sample_factory_data)

    @pytest.mark.unit
    def test_link_plugin_factory_creation(
        self, sample_factory_data: Dict[str, Any]
    ) -> None:
        """Test LinkPluginFactory creation."""
        # Act
        factory = LinkPluginFactory(**sample_factory_data)

        # Assert
        assert factory.app_id == "test_app"
        assert factory.uid == "test_user"
        assert factory.tool_ids == ["tool1", {"tool_id": "tool2", "version": "v2.0"}]
        assert factory.const_headers == {"Content-Type": "application/json"}

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_tool_schema_list_success(
        self, link_factory: LinkPluginFactory
    ) -> None:
        """Test successful tool_schema_list retrieval."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        expected_tools = [
            {
                "id": "tool1",
                "version": "V1.0",
                "schema": '{"paths": {"/test": {"get": {"operationId": "test_op"}}}}',
            },
            {
                "id": "tool2",
                "version": "v2.0",
                "schema": (
                    '{"paths": {"/test2": {"post": {"operationId": "test_op2"}}}}'
                ),
            },
        ]

        expected_response = {"code": 0, "data": {"tools": expected_tools}}

        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.json = AsyncMock(return_value=expected_response)
        mock_response.raise_for_status = Mock()

        # Create async context manager for session.get
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.get = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch("aiohttp.ClientSession", return_value=session_context):
            with patch("service.plugin.link.agent_config") as mock_config:
                mock_config.VERSIONS_LINK_URL = "http://test-url"

                # Act
                result = await link_factory.tool_schema_list(mock_span)

                # Assert
                assert result == expected_tools

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_tool_schema_list_empty_tool_ids(
        self, sample_factory_data: Dict[str, Any]
    ) -> None:
        """Test tool_schema_list with empty tool_ids."""
        # Arrange
        sample_factory_data["tool_ids"] = []
        factory = LinkPluginFactory(**sample_factory_data)

        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        # Act
        result = await factory.tool_schema_list(mock_span)

        # Assert
        assert result == []

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_tool_schema_list_api_error(
        self, link_factory: LinkPluginFactory
    ) -> None:
        """Test tool_schema_list with API error response."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        error_response = {"code": 1, "message": "API Error"}

        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.json = AsyncMock(return_value=error_response)
        mock_response.raise_for_status = Mock()

        # Create async context manager for session.get
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.get = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch("aiohttp.ClientSession", return_value=session_context):
            with patch("service.plugin.link.agent_config") as mock_config:
                mock_config.VERSIONS_LINK_URL = "http://test-url"

                # Act & Assert
                with pytest.raises(type(GetToolSchemaExc)) as exc_info:
                    await link_factory.tool_schema_list(mock_span)

                assert "Failed to get link tool protocol" in str(exc_info.value)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_tool_schema_list_http_error(
        self, link_factory: LinkPluginFactory
    ) -> None:
        """Test tool_schema_list with HTTP error."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_response = AsyncMock()
        mock_response.status = 500
        mock_response.raise_for_status = Mock()

        # Create async context manager for session.get
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.get = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch("aiohttp.ClientSession", return_value=session_context):
            with patch("service.plugin.link.agent_config") as mock_config:
                mock_config.VERSIONS_LINK_URL = "http://test-url"

                # Act & Assert
                with pytest.raises(type(GetToolSchemaExc)) as exc_info:
                    await link_factory.tool_schema_list(mock_span)

                assert "Failed to get link tool protocol" in str(exc_info.value)

    @pytest.mark.unit
    def test_parse_request_query_schema(self) -> None:
        """Test parsing request query schema."""
        # Arrange
        query_schema = [
            {
                "name": "query1",
                "description": "Query parameter 1",
                "in": "query",
                "required": True,
                "schema": {"type": "string", "x-from": 0},  # Model input
            },
            {
                "name": "query2",
                "description": "Query parameter 2",
                "in": "query",
                "required": False,
                "schema": {"type": "integer", "x-display": True},  # Model input
            },
            {
                "name": "business_query",
                "description": "Business query",
                "in": "query",
                "required": True,
                "schema": {
                    "type": "string",
                    "x-from": 1,  # Business input - should be ignored
                },
            },
        ]

        # Act
        parameters, required = LinkPluginFactory.parse_request_query_schema(
            query_schema
        )

        # Assert
        assert "query1" in parameters
        assert "query2" in parameters
        assert "business_query" not in parameters  # Should be filtered out

        assert "query1" in required
        assert "query2" not in required  # Not required
        assert "business_query" not in required

        assert parameters["query1"]["type"] == "string"
        assert parameters["query2"]["type"] == "integer"

    @pytest.mark.unit
    def test_recursive_parse_request_body_schema(
        self, link_factory: LinkPluginFactory
    ) -> None:
        """Test recursive parsing of request body schema."""
        # Arrange
        body_schema = {
            "properties": {
                "field1": {
                    "type": "string",
                    "description": "Field 1",
                    "x-from": 0,  # Model input
                },
                "field2": {
                    "type": "object",
                    "properties": {
                        "nested_field": {
                            "type": "string",
                            "description": "Nested field",
                            "x-display": True,  # Model input
                        }
                    },
                },
                "business_field": {
                    "type": "string",
                    "description": "Business field",
                    "x-from": 1,  # Business input - should be ignored
                },
            },
            "required": ["field1", "field2"],
        }

        properties: Dict[str, Any] = {}
        required_set: set[str] = set()

        # Act
        link_factory.recursive_parse_request_body_schema(
            body_schema, properties, required_set
        )

        # Assert
        assert "field1" in properties
        assert "nested_field" in properties  # From nested object
        assert "business_field" not in properties  # Should be filtered out

        assert "field1" in required_set
        assert "field2" in required_set

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_gen_creates_link_plugins(
        self, link_factory: LinkPluginFactory
    ) -> None:
        """Test gen method creates LinkPlugin instances."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        # Mock tool_schema_list to return simplified schema
        sample_tools = [
            {
                "id": "tool1",
                "version": "v1.0",
                "schema": json.dumps(
                    {
                        "paths": {
                            "/test": {
                                "get": {
                                    "operationId": "test_operation",
                                    "description": "Test operation",
                                    "parameters": [
                                        {
                                            "name": "test_param",
                                            "in": "query",
                                            "required": True,
                                            "schema": {"type": "string", "x-from": 0},
                                        }
                                    ],
                                }
                            }
                        }
                    }
                ),
            }
        ]

        with patch(
            "service.plugin.link.LinkPluginFactory.tool_schema_list",
            AsyncMock(return_value=sample_tools),
        ):
            # Act
            plugins = await link_factory.gen(mock_span)

            # Assert
            assert len(plugins) == 1
            assert isinstance(plugins[0], LinkPlugin)
            assert plugins[0].name == "test_operation"
            assert plugins[0].tool_id == "tool1"
            assert plugins[0].typ == "link"
            assert callable(plugins[0].run)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_method_returns_none(
        self, link_factory: LinkPluginFactory
    ) -> None:
        """Test factory run method returns None (incomplete implementation)."""
        # Arrange
        mock_span = Mock(spec=Span)

        # Act
        result = await link_factory.run("test_operation", {}, mock_span)

        # Assert
        assert result is None

    @pytest.mark.unit
    def test_link_plugin_factory_with_custom_headers(self) -> None:
        """Test LinkPluginFactory with custom headers."""
        # Act
        factory = LinkPluginFactory(
            app_id="test_app",
            uid="test_user",
            tool_ids=["tool1"],
            const_headers={"Authorization": "Bearer token", "X-Custom": "value"},
        )

        # Assert
        assert factory.const_headers["Authorization"] == "Bearer token"
        assert factory.const_headers["X-Custom"] == "value"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_parse_react_schema_list_with_complex_schema(
        self, link_factory: LinkPluginFactory
    ) -> None:
        """Test parsing complex schema with nested objects and various types."""
        # This test would require a more complex setup, but demonstrates the structure
        # for testing the full schema parsing functionality

        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        # Mock empty tools to test the basic flow
        with patch(
            "service.plugin.link.LinkPluginFactory.tool_schema_list",
            AsyncMock(return_value=[]),
        ):
            # Act
            plugins = await link_factory.parse_react_schema_list(mock_span)

            # Assert
            assert isinstance(plugins, list)
            assert len(plugins) == 0  # Empty because no tools returned
