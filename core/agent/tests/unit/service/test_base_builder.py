"""Unit tests for service.builder.base_builder module."""

from typing import Any, Dict, List
from unittest.mock import AsyncMock, Mock, patch

import pytest
from openai import AsyncOpenAI

from api.schemas.bot_config import BotConfig
from api.schemas.llm_message import LLMMessage
from common_imports import Span
from domain.models.base import BaseLLMModel
from engine.nodes.chat.chat_runner import ChatRunner
from engine.nodes.cot.cot_runner import CotRunner
from engine.nodes.cot_process.cot_process_runner import CotProcessRunner
from service.builder.base_builder import BaseApiBuilder, CotRunnerParams, RunnerParams
from service.plugin.base import BasePlugin
from tests.fixtures.test_data import TestDataFactory


class TestRunnerParams:
    """Test cases for RunnerParams dataclass."""

    @pytest.fixture
    def mock_llm_model(self) -> Mock:
        """Create mock LLM model."""
        return Mock(spec=BaseLLMModel, name="gpt-3.5-turbo")

    @pytest.fixture
    def sample_chat_history(self) -> List[LLMMessage]:
        """Sample chat history."""
        return [
            LLMMessage(role="user", content="Hello"),
            LLMMessage(role="assistant", content="Hi there!"),
        ]

    @pytest.mark.unit
    def test_runner_params_creation(
        self, mock_llm_model: Mock, sample_chat_history: List[LLMMessage]
    ) -> None:
        """Test RunnerParams creation with valid data."""
        # Act
        params = RunnerParams(
            model=mock_llm_model,
            chat_history=sample_chat_history,
            instruct="Test instruction",
            knowledge="Test knowledge",
            question="Test question",
        )

        # Assert
        assert params.model == mock_llm_model
        assert len(params.chat_history) == 2
        assert params.instruct == "Test instruction"
        assert params.knowledge == "Test knowledge"
        assert params.question == "Test question"

    @pytest.mark.unit
    def test_runner_params_required_fields(self) -> None:
        """Test RunnerParams with minimal required fields."""
        mock_model = Mock(spec=BaseLLMModel)
        mock_history = [LLMMessage(role="user", content="test")]

        params = RunnerParams(
            model=mock_model,
            chat_history=mock_history,
            instruct="",
            knowledge="",
            question="",
        )

        assert params.model == mock_model
        assert len(params.chat_history) == 1
        assert params.instruct == ""


class TestCotRunnerParams:
    """Test cases for CotRunnerParams dataclass."""

    @pytest.fixture
    def mock_llm_model(self) -> Mock:
        """Create mock LLM model."""
        return Mock(spec=BaseLLMModel, name="gpt-3.5-turbo")

    @pytest.fixture
    def sample_chat_history(self) -> List[LLMMessage]:
        """Sample chat history."""
        return [LLMMessage(role="user", content="Hello")]

    @pytest.fixture
    def mock_plugins(self) -> List[Mock]:
        """Create mock plugins."""
        return [Mock(typ="test_plugin", schema_template="test_schema")]

    @pytest.mark.unit
    def test_cot_runner_params_creation(
        self,
        mock_llm_model: Mock,
        sample_chat_history: List[LLMMessage],
        mock_plugins: List[Mock],
    ) -> None:
        """Test CotRunnerParams creation with valid data."""
        # Act
        params = CotRunnerParams(
            model=mock_llm_model,
            chat_history=sample_chat_history,
            instruct="CoT instruction",
            knowledge="CoT knowledge",
            question="CoT question",
            plugins=mock_plugins,
            process_runner=Mock(),
            max_loop=5,
        )

        # Assert
        assert params.model == mock_llm_model
        assert len(params.chat_history) == 1
        assert params.instruct == "CoT instruction"
        assert params.plugins == mock_plugins
        assert params.max_loop == 5

    @pytest.mark.unit
    def test_cot_runner_params_default_max_loop(
        self,
        mock_llm_model: Mock,
        sample_chat_history: List[LLMMessage],
    ) -> None:
        """Test CotRunnerParams with default max_loop value."""
        params = CotRunnerParams(
            model=mock_llm_model,
            chat_history=sample_chat_history,
            instruct="CoT instruction",
            knowledge="CoT knowledge",
            question="CoT question",
            plugins=[],
            process_runner=Mock(),
        )

        # Should have default max_loop value
        assert hasattr(params, "max_loop")


class TestBaseApiBuilder:
    """Test cases for BaseApiBuilder class."""

    @pytest.fixture
    def mock_span(self) -> Mock:
        """Create mock span with proper context manager."""
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        mock_span_context.add_info_events = Mock()

        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)
        return mock_span

    @pytest.fixture
    def base_builder(self, mock_span: Mock) -> BaseApiBuilder:
        """Create BaseApiBuilder instance."""
        return BaseApiBuilder(
            app_id="test_app_001", uid="test_user_001", span=mock_span
        )

    @pytest.mark.unit
    def test_base_api_builder_creation(self, mock_span: Mock) -> None:
        """Test BaseApiBuilder creation with valid data."""
        # Act
        builder = BaseApiBuilder(app_id="test_app", uid="test_uid", span=mock_span)

        # Assert
        assert builder.app_id == "test_app"
        assert builder.uid == "test_uid"
        assert builder.span == mock_span

    @pytest.mark.unit
    def test_base_api_builder_with_default_uid(self, mock_span: Mock) -> None:
        """Test BaseApiBuilder creation with default uid."""
        # Act
        builder = BaseApiBuilder(app_id="test_app", span=mock_span)

        # Assert
        assert builder.app_id == "test_app"
        assert builder.uid == ""
        assert builder.span == mock_span

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_bot_config_success(self, base_builder: BaseApiBuilder) -> None:
        """Test successful bot config building."""
        # Arrange
        mock_bot_config_data = TestDataFactory.create_bot_config()

        with patch("service.builder.base_builder.BotConfigClient") as mock_client_class:
            mock_client = AsyncMock()
            mock_client.pull.return_value = mock_bot_config_data
            mock_client_class.return_value = mock_client

            # Act
            result = await base_builder.build_bot_config("test_bot_001")

            # Assert
            assert isinstance(result, BotConfig)
            assert result.bot_id == mock_bot_config_data["bot_id"]
            mock_client_class.assert_called_once()
            mock_client.pull.assert_called_once()

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_bot_config_with_dict_response(
        self, base_builder: BaseApiBuilder
    ) -> None:
        """Test bot config building with dict response."""
        # Arrange
        mock_bot_config_dict = TestDataFactory.create_bot_config()

        with patch("service.builder.base_builder.BotConfigClient") as mock_client_class:
            mock_client = AsyncMock()
            mock_client.pull.return_value = mock_bot_config_dict
            mock_client_class.return_value = mock_client

            # Act
            result = await base_builder.build_bot_config("test_bot_001")

            # Assert
            assert isinstance(result, BotConfig)
            assert result.bot_id == mock_bot_config_dict["bot_id"]

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_plugins_with_tool_ids(
        self, base_builder: BaseApiBuilder
    ) -> None:
        """Test building plugins with tool IDs."""
        # Arrange
        mock_link_plugins = [Mock(typ="link", schema_template="link_schema")]

        with patch(
            "service.builder.base_builder.LinkPluginFactory"
        ) as mock_factory_class:
            mock_factory = AsyncMock()
            mock_factory.gen.return_value = mock_link_plugins
            mock_factory_class.return_value = mock_factory

            # Act
            result = await base_builder.build_plugins(
                tool_ids=["tool_001"],
                mcp_server_ids=[],
                mcp_server_urls=[],
                workflow_ids=[],
            )

            # Assert
            assert len(result) == 1
            assert result[0].typ == "link"
            mock_factory_class.assert_called_once_with(
                app_id="test_app_001", uid="test_user_001", tool_ids=["tool_001"]
            )

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_plugins_with_mcp_servers(
        self, base_builder: BaseApiBuilder
    ) -> None:
        """Test building plugins with MCP servers."""
        # Arrange
        mock_mcp_plugins = [Mock(typ="mcp", schema_template="mcp_schema")]

        with patch(
            "service.builder.base_builder.McpPluginFactory"
        ) as mock_factory_class:
            mock_factory = AsyncMock()
            mock_factory.gen.return_value = mock_mcp_plugins
            mock_factory_class.return_value = mock_factory

            # Act
            result = await base_builder.build_plugins(
                tool_ids=[],
                mcp_server_ids=["mcp_001"],
                mcp_server_urls=["http://localhost:8080"],
                workflow_ids=[],
            )

            # Assert
            assert len(result) == 1
            assert result[0].typ == "mcp"
            mock_factory_class.assert_called_once_with(
                app_id="test_app_001",
                mcp_server_ids=["mcp_001"],
                mcp_server_urls=["http://localhost:8080"],
            )

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_plugins_with_workflows(
        self, base_builder: BaseApiBuilder
    ) -> None:
        """Test building plugins with workflow IDs."""
        # Arrange
        mock_workflow_plugins = [
            Mock(typ="workflow", schema_template="workflow_schema")
        ]

        with patch(
            "service.builder.base_builder.WorkflowPluginFactory"
        ) as mock_factory_class:
            mock_factory = AsyncMock()
            mock_factory.gen.return_value = mock_workflow_plugins
            mock_factory_class.return_value = mock_factory

            # Act
            result = await base_builder.build_plugins(
                tool_ids=[],
                mcp_server_ids=[],
                mcp_server_urls=[],
                workflow_ids=["workflow_001"],
            )

            # Assert
            assert len(result) == 1
            assert result[0].typ == "workflow"
            mock_factory_class.assert_called_once_with(
                app_id="test_app_001",
                uid="test_user_001",
                workflow_ids=["workflow_001"],
            )

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_plugins_all_types(self, base_builder: BaseApiBuilder) -> None:
        """Test building plugins with all plugin types."""
        # Arrange
        mock_link_plugins = [Mock(typ="link", schema_template="link_schema")]
        mock_mcp_plugins = [Mock(typ="mcp", schema_template="mcp_schema")]
        mock_workflow_plugins = [
            Mock(typ="workflow", schema_template="workflow_schema")
        ]

        with (
            patch(
                "service.builder.base_builder.LinkPluginFactory"
            ) as mock_link_factory,
            patch("service.builder.base_builder.McpPluginFactory") as mock_mcp_factory,
            patch(
                "service.builder.base_builder.WorkflowPluginFactory"
            ) as mock_workflow_factory,
        ):

            mock_link_factory.return_value.gen = AsyncMock(
                return_value=mock_link_plugins
            )
            mock_mcp_factory.return_value.gen = AsyncMock(return_value=mock_mcp_plugins)
            mock_workflow_factory.return_value.gen = AsyncMock(
                return_value=mock_workflow_plugins
            )

            # Act
            result = await base_builder.build_plugins(
                tool_ids=["tool_001"],
                mcp_server_ids=["mcp_001"],
                mcp_server_urls=["http://localhost:8080"],
                workflow_ids=["workflow_001"],
            )

            # Assert
            assert len(result) == 3
            plugin_types = [plugin.typ for plugin in result]
            assert "link" in plugin_types
            assert "mcp" in plugin_types
            assert "workflow" in plugin_types

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_chat_runner_success(
        self, base_builder: BaseApiBuilder
    ) -> None:
        """Test successful chat runner building."""
        # Arrange
        mock_model = Mock(spec=BaseLLMModel)
        mock_model.name = "gpt-3.5-turbo"
        chat_history = [LLMMessage(role="user", content="Hello")]

        params = RunnerParams(
            model=mock_model,
            chat_history=chat_history,
            instruct="Test instruction",
            knowledge="Test knowledge",
            question="Test question",
        )

        # Act
        result = await base_builder.build_chat_runner(params)

        # Assert
        assert isinstance(result, ChatRunner)
        assert result.model == mock_model
        assert result.instruct == "Test instruction"
        assert result.knowledge == "Test knowledge"
        assert result.question == "Test question"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_cot_runner_success(self, base_builder: BaseApiBuilder) -> None:
        """Test successful CoT runner building."""
        # Arrange
        mock_model = Mock(spec=BaseLLMModel)
        mock_model.name = "gpt-4"
        chat_history = [LLMMessage(role="user", content="Hello")]

        async def mock_run_func(_action_input: Any, _span: Any) -> Dict[str, Any]:
            return {"result": "test"}

        test_plugin = BasePlugin(
            name="test_plugin",
            description="Test plugin",
            schema_template="test_schema",
            typ="test",
            run=mock_run_func,
        )
        mock_plugins = [test_plugin]

        mock_process_runner = Mock(spec=CotProcessRunner)

        params = CotRunnerParams(
            model=mock_model,
            chat_history=chat_history,
            instruct="CoT instruction",
            knowledge="CoT knowledge",
            question="CoT question",
            plugins=mock_plugins,
            process_runner=mock_process_runner,
            max_loop=3,
        )

        # Act
        result = await base_builder.build_cot_runner(params)

        # Assert
        assert isinstance(result, CotRunner)
        assert result.model == mock_model
        assert result.instruct == "CoT instruction"
        assert result.max_loop == 3

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_process_runner_success(
        self, base_builder: BaseApiBuilder
    ) -> None:
        """Test successful process runner building."""
        # Arrange
        mock_model = Mock(spec=BaseLLMModel)
        mock_model.name = "gpt-3.5-turbo"
        chat_history = [LLMMessage(role="user", content="Hello")]

        params = RunnerParams(
            model=mock_model,
            chat_history=chat_history,
            instruct="Process instruction",
            knowledge="Process knowledge",
            question="Process question",
        )

        # Act
        result = await base_builder.build_process_runner(params)

        # Assert
        assert isinstance(result, CotProcessRunner)
        assert result.model == mock_model
        assert result.instruct == "Process instruction"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_query_maas_sk_success(self, base_builder: BaseApiBuilder) -> None:
        """Test successful MAAS SK query."""
        # Arrange
        expected_sk = "test_secret_key"

        with patch("service.builder.base_builder.MaasAuth") as mock_auth_class:
            mock_auth = AsyncMock()
            mock_auth.sk.return_value = expected_sk
            mock_auth_class.return_value = mock_auth

            # Act
            result = await base_builder.query_maas_sk("test_app", "gpt-3.5-turbo")

            # Assert
            assert result == expected_sk
            mock_auth_class.assert_called_once_with(
                app_id="test_app", model_name="gpt-3.5-turbo"
            )

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_query_maas_sk_with_default_app_id(
        self, base_builder: BaseApiBuilder
    ) -> None:
        """Test MAAS SK query with default app_id."""
        # Arrange
        expected_sk = "test_secret_key"

        with patch("service.builder.base_builder.MaasAuth") as mock_auth_class:
            mock_auth = AsyncMock()
            mock_auth.sk.return_value = expected_sk
            mock_auth_class.return_value = mock_auth

            # Act
            result = await base_builder.query_maas_sk("", "gpt-3.5-turbo")

            # Assert
            assert result == expected_sk
            mock_auth_class.assert_called_once_with(
                app_id="test_app_001", model_name="gpt-3.5-turbo"
            )

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_create_model_with_api_key(
        self, base_builder: BaseApiBuilder
    ) -> None:
        """Test model creation with provided API key."""
        # Arrange
        with patch("service.builder.base_builder.AsyncOpenAI") as mock_openai:
            mock_openai_instance = Mock(spec=AsyncOpenAI)
            mock_openai.return_value = mock_openai_instance

            # Act
            result = await base_builder.create_model(
                app_id="test_app",
                model_name="gpt-3.5-turbo",
                base_url="https://api.openai.com/v1",
                api_key="provided_key",
            )

            # Assert
            assert isinstance(result, BaseLLMModel)
            assert result.name == "gpt-3.5-turbo"
            mock_openai.assert_called_once_with(
                api_key="provided_key", base_url="https://api.openai.com/v1"
            )

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_create_model_without_api_key(
        self, base_builder: BaseApiBuilder
    ) -> None:
        """Test model creation without API key (uses MAAS)."""
        # Arrange
        with (
            patch("service.builder.base_builder.AsyncOpenAI") as mock_openai,
            patch(
                "service.builder.base_builder.BaseApiBuilder.query_maas_sk"
            ) as mock_query_sk,
        ):

            mock_openai_instance = Mock(spec=AsyncOpenAI)
            mock_openai.return_value = mock_openai_instance
            mock_query_sk.return_value = "maas_generated_key"

            # Act
            result = await base_builder.create_model(
                app_id="test_app",
                model_name="gpt-3.5-turbo",
                base_url="https://api.openai.com/v1",
            )

            # Assert
            assert isinstance(result, BaseLLMModel)
            mock_query_sk.assert_called_once_with("test_app", "gpt-3.5-turbo")
            mock_openai.assert_called_once_with(
                api_key="maas_generated_key", base_url="https://api.openai.com/v1"
            )

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_plugins_empty_params(
        self, base_builder: BaseApiBuilder
    ) -> None:
        """Test building plugins with empty parameters."""
        # Act
        result = await base_builder.build_plugins(
            tool_ids=[],
            mcp_server_ids=[],
            mcp_server_urls=[],
            workflow_ids=[],
        )

        # Assert
        assert result == []

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_bot_config_error_handling(
        self, base_builder: BaseApiBuilder
    ) -> None:
        """Test bot config building error handling."""
        with patch("service.builder.base_builder.BotConfigClient") as mock_client_class:
            mock_client = AsyncMock()
            mock_client.pull.side_effect = Exception("Config fetch failed")
            mock_client_class.return_value = mock_client

            # Act & Assert
            with pytest.raises(Exception, match="Config fetch failed"):
                await base_builder.build_bot_config("invalid_bot_id")

    @pytest.mark.unit
    def test_base_api_builder_inheritance(self) -> None:
        """Test BaseApiBuilder class structure."""
        # Verify class exists and has expected methods
        assert hasattr(BaseApiBuilder, "__init__")
        assert hasattr(BaseApiBuilder, "build_bot_config")
        assert hasattr(BaseApiBuilder, "build_plugins")
        assert hasattr(BaseApiBuilder, "create_model")

        # Verify it's properly typed
        assert BaseApiBuilder.__module__ == "service.builder.base_builder"
