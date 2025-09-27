"""
Unit tests for service.plugin.workflow
"""

from typing import Any, Dict
from unittest.mock import AsyncMock, Mock, patch

import aiohttp
import httpx
import pytest

from common_imports import Span
from exceptions.plugin_exc import RunWorkflowExc
from service.plugin.base import BasePlugin, PluginResponse
from service.plugin.workflow import (
    ResponseContext,
    WorkflowPlugin,
    WorkflowPluginFactory,
    WorkflowPluginRunner,
)


class AsyncContextManager:
    """Proper async context manager for mocking aiohttp responses."""

    def __init__(self, return_value: Any) -> None:
        self._return_value = return_value

    async def __aenter__(self) -> Any:
        return self._return_value

    async def __aexit__(self, exc_type: Any, exc_val: Any, exc_tb: Any) -> None:
        return None


class TestWorkflowPlugin:
    """Test cases for WorkflowPlugin."""

    @pytest.mark.unit
    def test_workflow_plugin_inherits_from_base_plugin(self) -> None:
        """Test WorkflowPlugin inherits from BasePlugin."""
        # Assert
        assert issubclass(WorkflowPlugin, BasePlugin)

    @pytest.mark.unit
    def test_workflow_plugin_creation(self) -> None:
        """Test WorkflowPlugin creation with flow_id field."""
        # Act
        plugin = WorkflowPlugin(
            flow_id="workflow_123",
            name="test_workflow",
            description="Test workflow plugin",
            schema_template="test_schema",
            typ="workflow",
            run=AsyncMock(),
        )

        # Assert
        assert plugin.flow_id == "workflow_123"
        assert plugin.name == "test_workflow"
        assert plugin.description == "Test workflow plugin"
        assert plugin.typ == "workflow"


class TestResponseContext:
    """Test cases for ResponseContext."""

    @pytest.mark.unit
    def test_response_context_creation(self) -> None:
        """Test ResponseContext creation."""
        # Act
        ctx = ResponseContext(
            code=0,
            sid="test_session",
            start_time=1234567890,
            end_time=1234567900,
            action_input={"param": "value"},
        )

        # Assert
        assert ctx.code == 0
        assert ctx.sid == "test_session"
        assert ctx.start_time == 1234567890
        assert ctx.end_time == 1234567900
        assert ctx.action_input == {"param": "value"}


class TestWorkflowPluginRunner:
    """Test cases for WorkflowPluginRunner."""

    @pytest.fixture
    def workflow_runner(self) -> WorkflowPluginRunner:
        """Create WorkflowPluginRunner instance for testing."""
        return WorkflowPluginRunner(
            app_id="test_app", uid="test_user", flow_id="flow_123", stream=True
        )

    @pytest.mark.unit
    def test_workflow_plugin_runner_creation(
        self, workflow_runner: WorkflowPluginRunner
    ) -> None:
        """Test WorkflowPluginRunner creation."""
        # Assert
        assert workflow_runner.app_id == "test_app"
        assert workflow_runner.uid == "test_user"
        assert workflow_runner.flow_id == "flow_123"
        assert workflow_runner.stream is True

    @pytest.mark.unit
    def test_workflow_plugin_runner_creation_with_defaults(self) -> None:
        """Test WorkflowPluginRunner creation with default stream value."""
        # Act
        runner = WorkflowPluginRunner(
            app_id="test_app", uid="test_user", flow_id="flow_123"
        )

        # Assert
        assert runner.stream is True  # Default value

    @pytest.mark.unit
    def test_build_request_params(
        self, workflow_runner: WorkflowPluginRunner
    ) -> None:  # pylint: disable=protected-access
        """Test _build_request_params method."""
        # Arrange
        action_input = {"param1": "value1", "param2": "value2"}

        # Act
        params = workflow_runner._build_request_params(
            action_input
        )  # pylint: disable=protected-access

        # Assert
        assert params["model"] == ""
        assert params["messages"] == []
        assert params["stream"] is True
        assert params["extra_body"]["flow_id"] == "flow_123"
        assert params["extra_body"]["uid"] == "test_user"
        assert params["extra_body"]["parameters"] == action_input
        assert params["extra_body"]["extra_body"]["bot_id"] == "workflow"
        assert params["extra_body"]["extra_body"]["caller"] == "agent"
        assert params["extra_headers"]["X-consumer-username"] == "test_app"

    @pytest.mark.unit
    def test_create_error_response(
        self, workflow_runner: WorkflowPluginRunner
    ) -> None:  # pylint: disable=protected-access
        """Test _create_error_response method."""
        # Arrange
        ctx = ResponseContext(
            code=1,
            sid="error_session",
            start_time=1234567890,
            end_time=1234567900,
            action_input={"param": "value"},
        )
        chunk_data = {"error": "Workflow failed", "message": "Something went wrong"}

        # Act
        response = workflow_runner._create_error_response(
            ctx, chunk_data
        )  # pylint: disable=protected-access

        # Assert
        assert isinstance(response, PluginResponse)
        assert response.code == 1
        assert response.sid == "error_session"
        assert response.start_time == 1234567890
        assert response.end_time == 1234567900
        assert response.result == chunk_data
        assert len(response.log) == 1
        assert response.log[0]["name"] == "flow_123"
        assert response.log[0]["input"] == {"param": "value"}
        assert response.log[0]["output"] == chunk_data

    @pytest.mark.unit
    def test_create_success_response(
        self, workflow_runner: WorkflowPluginRunner
    ) -> None:  # pylint: disable=protected-access
        """Test _create_success_response method."""
        # Arrange
        ctx = ResponseContext(
            code=0,
            sid="success_session",
            start_time=1234567890,
            end_time=1234567900,
            action_input={"param": "value"},
        )
        content = "Workflow completed successfully"
        reasoning_content = "Step-by-step reasoning"

        # Act
        response = workflow_runner._create_success_response(  # pylint: disable=protected-access
            ctx, content, reasoning_content
        )

        # Assert
        assert isinstance(response, PluginResponse)
        assert response.code == 0
        assert response.sid == "success_session"
        assert response.result["content"] == content
        assert response.result["reasoning_content"] == reasoning_content
        assert len(response.log) == 1
        assert response.log[0]["content"] == content
        assert response.log[0]["reasoning_content"] == reasoning_content

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_success(self, workflow_runner: WorkflowPluginRunner) -> None:
        """Test successful WorkflowPluginRunner execution."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        action_input = {"task": "test task", "params": {"key": "value"}}

        # Mock OpenAI client and response
        mock_choice = Mock()
        mock_choice.delta.content = "Response content"
        mock_choice.delta.to_dict.return_value = {"reasoning_content": "Reasoning"}

        mock_chunk = Mock()
        mock_chunk.model_dump.return_value = {"code": 0, "id": "session_123"}
        mock_chunk.choices = [mock_choice]

        async def mock_stream(_self: Any) -> Any:  # pylint: disable=unused-argument
            yield mock_chunk

        mock_completion = AsyncMock()
        mock_completion.__aiter__ = mock_stream

        mock_client = AsyncMock()
        mock_client.chat.completions.create = AsyncMock(return_value=mock_completion)

        with patch("service.plugin.workflow.AsyncOpenAI", return_value=mock_client):
            with patch("service.plugin.workflow.agent_config") as mock_config:
                mock_config.WORKFLOW_SSE_BASE_URL = "http://workflow-api"

                # Act
                responses = []
                async for response in workflow_runner.run(action_input, mock_span):
                    responses.append(response)

                # Assert
                assert len(responses) == 1
                assert isinstance(responses[0], PluginResponse)
                assert responses[0].code == 0
                assert responses[0].result["content"] == "Response content"
                assert responses[0].result["reasoning_content"] == "Reasoning"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_error_response(
        self, workflow_runner: WorkflowPluginRunner
    ) -> None:
        """Test WorkflowPluginRunner with error response."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        action_input = {"task": "test task"}

        mock_chunk = Mock()
        mock_chunk.model_dump.return_value = {
            "code": 1,
            "id": "error_session",
            "error": "Workflow execution failed",
        }

        async def mock_stream(_self: Any) -> Any:  # pylint: disable=unused-argument
            yield mock_chunk

        mock_completion = AsyncMock()
        mock_completion.__aiter__ = mock_stream

        mock_client = AsyncMock()
        mock_client.chat.completions.create = AsyncMock(return_value=mock_completion)

        with patch("service.plugin.workflow.AsyncOpenAI", return_value=mock_client):
            with patch("service.plugin.workflow.agent_config") as mock_config:
                mock_config.WORKFLOW_SSE_BASE_URL = "http://workflow-api"

                # Act
                responses = []
                async for response in workflow_runner.run(action_input, mock_span):
                    responses.append(response)

                # Assert
                assert len(responses) == 1
                assert responses[0].code == 1
                assert "error" in responses[0].result

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_timeout_error(
        self, workflow_runner: WorkflowPluginRunner
    ) -> None:
        """Test WorkflowPluginRunner with timeout error."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        action_input = {"task": "test task"}

        mock_client = AsyncMock()
        mock_client.chat.completions.create = AsyncMock(
            side_effect=httpx.TimeoutException("Request timeout")
        )

        with patch("service.plugin.workflow.AsyncOpenAI", return_value=mock_client):
            with patch("service.plugin.workflow.agent_config") as mock_config:
                mock_config.WORKFLOW_SSE_BASE_URL = "http://workflow-api"

                # Act & Assert
                with pytest.raises(type(RunWorkflowExc)) as exc_info:
                    async for _ in workflow_runner.run(action_input, mock_span):
                        pass

                assert "Failed to call workflow tool" in str(exc_info.value)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_logs_span_events(
        self, workflow_runner: WorkflowPluginRunner
    ) -> None:  # pylint: disable=too-many-locals
        """Test run logs span events correctly."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        action_input = {"task": "test task"}

        mock_choice = Mock()
        mock_choice.delta.content = "Content"
        mock_choice.delta.to_dict.return_value = {"reasoning_content": ""}

        mock_chunk = Mock()
        mock_chunk.model_dump.return_value = {"code": 0, "id": "session_123"}
        mock_chunk.choices = [mock_choice]

        async def mock_stream(_self: Any) -> Any:  # pylint: disable=unused-argument
            yield mock_chunk

        mock_completion = AsyncMock()
        mock_completion.__aiter__ = mock_stream

        mock_client = AsyncMock()
        mock_client.chat.completions.create = AsyncMock(return_value=mock_completion)

        with patch("service.plugin.workflow.AsyncOpenAI", return_value=mock_client):
            with patch("service.plugin.workflow.agent_config") as mock_config:
                mock_config.WORKFLOW_SSE_BASE_URL = "http://workflow-api"

                # Act
                async for _ in workflow_runner.run(action_input, mock_span):
                    break

                # Assert - Verify span events were logged
                assert mock_span_context.add_info_events.call_count >= 2

                # Check that input and output events were logged
                call_args_list = mock_span_context.add_info_events.call_args_list
                logged_events = [call[1]["attributes"] for call in call_args_list]

                input_logged = any(
                    "workflow-plugin-run-inputs" in event for event in logged_events
                )
                output_logged = any(
                    "workflow-plugin-run-outputs" in event for event in logged_events
                )

                assert input_logged
                assert output_logged


class TestWorkflowPluginFactory:
    """Test cases for WorkflowPluginFactory."""

    @pytest.fixture
    def sample_factory_data(self) -> Dict[str, Any]:
        """Sample factory data for testing."""
        return {
            "app_id": "test_app",
            "uid": "test_user",
            "workflow_ids": ["workflow1", "workflow2"],
        }

    @pytest.fixture
    def workflow_factory(
        self, sample_factory_data: Dict[str, Any]
    ) -> WorkflowPluginFactory:
        """Create WorkflowPluginFactory instance for testing."""
        return WorkflowPluginFactory(**sample_factory_data)

    @pytest.mark.unit
    def test_workflow_plugin_factory_creation(
        self, sample_factory_data: Dict[str, Any]
    ) -> None:
        """Test WorkflowPluginFactory creation."""
        # Act
        factory = WorkflowPluginFactory(**sample_factory_data)

        # Assert
        assert factory.app_id == "test_app"
        assert factory.uid == "test_user"
        assert factory.workflow_ids == ["workflow1", "workflow2"]

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_do_query_workflow_schema_success(self) -> None:
        """Test successful do_query_workflow_schema."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        workflow_id = "workflow_123"
        expected_response = {
            "code": 0,
            "data": {
                "data": {
                    "id": workflow_id,
                    "name": "Test Workflow",
                    "description": "A test workflow",
                }
            },
        }

        mock_response = AsyncMock()
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
            with patch("service.plugin.workflow.agent_config") as mock_config:
                mock_config.GET_WORKFLOWS_URL = "http://workflow-api/get"

                # Act
                result = await WorkflowPluginFactory.do_query_workflow_schema(
                    workflow_id, mock_span
                )

                # Assert
                assert result == expected_response

                # Verify request was made with correct data
                call_args = mock_session.post.call_args
                assert call_args[1]["json"] == {"flow_id": workflow_id}

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_query_workflows_schema_list(
        self, workflow_factory: WorkflowPluginFactory
    ) -> None:
        """Test query_workflows_schema_list with multiple workflows."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        expected_results = [
            {"data": {"data": {"id": "workflow1", "name": "Workflow 1"}}},
            {"data": {"data": {"id": "workflow2", "name": "Workflow 2"}}},
        ]

        with patch(
            "service.plugin.workflow.WorkflowPluginFactory.do_query_workflow_schema",
            AsyncMock(side_effect=expected_results),
        ) as mock_query:
            # Act
            results = await workflow_factory.query_workflows_schema_list(mock_span)

            # Assert
            assert results == expected_results
            assert mock_query.call_count == 2
            # Check that mock was called with both workflow IDs
            actual_calls = [call.args[0] for call in mock_query.call_args_list]
            assert set(actual_calls) == {"workflow1", "workflow2"}

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_create_workflow_plugin_with_start_node(
        self, workflow_factory: WorkflowPluginFactory
    ) -> None:
        """Test create_workflow_plugin with valid start node."""
        # Arrange
        workflow_schema = {
            "data": {
                "data": {
                    "id": "workflow_123",
                    "name": "Test Workflow",
                    "description": "A test workflow description",
                    "data": {
                        "nodes": [
                            {
                                "id": "node-start::abc123",
                                "data": {
                                    "outputs": [
                                        {
                                            "name": "input_param",
                                            "schema": {"type": "string"},
                                            "required": True,
                                        },
                                        {
                                            "name": "optional_param",
                                            "schema": {"type": "integer"},
                                            "required": False,
                                        },
                                    ]
                                },
                            }
                        ]
                    },
                }
            }
        }

        # Act
        plugin = await workflow_factory.create_workflow_plugin(workflow_schema)

        # Assert
        assert isinstance(plugin, WorkflowPlugin)
        assert plugin.flow_id == "workflow_123"
        assert plugin.name == "Test Workflow"
        assert plugin.description == "A test workflow description"
        assert plugin.typ == "workflow"
        assert callable(plugin.run)

        # Check schema template contains parameters
        assert "input_param" in plugin.schema_template
        assert "optional_param" in plugin.schema_template
        assert "required" in plugin.schema_template

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_create_workflow_plugin_without_start_node(
        self, workflow_factory: WorkflowPluginFactory
    ) -> None:
        """Test create_workflow_plugin without valid start node."""
        # Arrange
        workflow_schema = {
            "data": {
                "data": {
                    "id": "workflow_456",
                    "name": "Invalid Workflow",
                    "description": "Workflow without start node",
                    "data": {
                        "nodes": [
                            {"id": "node-middle::abc123", "data": {"outputs": []}}
                        ]
                    },
                }
            }
        }

        # Act
        plugin = await workflow_factory.create_workflow_plugin(workflow_schema)

        # Assert
        assert isinstance(plugin, WorkflowPlugin)
        assert plugin.flow_id == "workflow_456"
        assert plugin.name == "Invalid Workflow"
        assert plugin.description == "Workflow without start node"
        assert plugin.typ == "workflow"
        # Should return default schema when no start node is found
        assert "unknown" in plugin.schema_template

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_create_workflow_plugin_with_empty_schema(
        self, workflow_factory: WorkflowPluginFactory
    ) -> None:
        """Test create_workflow_plugin with empty/malformed schema."""
        # Arrange
        workflow_schema = {"data": {"data": {"id": "", "name": "", "description": ""}}}

        # Act
        plugin = await workflow_factory.create_workflow_plugin(workflow_schema)

        # Assert
        assert isinstance(plugin, WorkflowPlugin)
        assert plugin.flow_id == ""
        assert plugin.name == "unknown"  # Default fallback
        assert plugin.description == "unknown workflow"  # Default fallback
        assert plugin.typ == "workflow"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_gen_creates_workflow_plugins(
        self, workflow_factory: WorkflowPluginFactory
    ) -> None:
        """Test gen method creates WorkflowPlugin instances."""
        # Arrange
        mock_span = Mock(spec=Span)

        sample_schemas = [
            {
                "data": {
                    "data": {
                        "id": "workflow1",
                        "name": "Workflow 1",
                        "description": "First workflow",
                        "data": {
                            "nodes": [
                                {
                                    "id": "node-start::abc",
                                    "data": {
                                        "outputs": [
                                            {
                                                "name": "param1",
                                                "schema": {"type": "string"},
                                                "required": True,
                                            }
                                        ]
                                    },
                                }
                            ]
                        },
                    }
                }
            }
        ]

        with patch(
            "service.plugin.workflow.WorkflowPluginFactory.query_workflows_schema_list",
            AsyncMock(return_value=sample_schemas),
        ):
            # Act
            plugins = await workflow_factory.gen(mock_span)

            # Assert
            assert len(plugins) == 1
            assert isinstance(plugins[0], WorkflowPlugin)
            assert plugins[0].name == "Workflow 1"
            assert plugins[0].flow_id == "workflow1"
            assert plugins[0].typ == "workflow"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_gen_with_empty_workflow_ids(self) -> None:
        """Test gen with empty workflow_ids list."""
        # Arrange
        factory = WorkflowPluginFactory(
            app_id="test_app", uid="test_user", workflow_ids=[]
        )
        mock_span = Mock(spec=Span)

        with patch(
            "service.plugin.workflow.WorkflowPluginFactory.query_workflows_schema_list",
            AsyncMock(return_value=[]),
        ):
            # Act
            plugins = await factory.gen(mock_span)

            # Assert
            assert plugins == []

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_create_workflow_plugin_with_unicode_content(
        self, workflow_factory: WorkflowPluginFactory
    ) -> None:
        """Test create_workflow_plugin with unicode content."""
        # Arrange
        workflow_schema = {
            "data": {
                "data": {
                    "id": "中文工作流",
                    "name": "中文工作流名称",
                    "description": "这是一个中文工作流描述",
                    "data": {
                        "nodes": [
                            {
                                "id": "node-start::中文节点",
                                "data": {
                                    "outputs": [
                                        {
                                            "name": "中文参数",
                                            "schema": {
                                                "type": "string",
                                                "description": "中文参数描述",
                                            },
                                            "required": True,
                                        }
                                    ]
                                },
                            }
                        ]
                    },
                }
            }
        }

        # Act
        plugin = await workflow_factory.create_workflow_plugin(workflow_schema)

        # Assert
        assert plugin.flow_id == "中文工作流"
        assert plugin.name == "中文工作流名称"
        assert plugin.description == "这是一个中文工作流描述"
        assert "中文参数" in plugin.schema_template

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_do_query_workflow_schema_http_error(self) -> None:
        """Test do_query_workflow_schema with HTTP error."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        workflow_id = "workflow_123"

        mock_response = AsyncMock()
        mock_response.raise_for_status = Mock(
            side_effect=aiohttp.ClientError("HTTP Error")
        )

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch("aiohttp.ClientSession", return_value=session_context):
            with patch("service.plugin.workflow.agent_config") as mock_config:
                mock_config.GET_WORKFLOWS_URL = "http://workflow-api/get"

                # Act & Assert
                with pytest.raises(aiohttp.ClientError):
                    await WorkflowPluginFactory.do_query_workflow_schema(
                        workflow_id, mock_span
                    )
