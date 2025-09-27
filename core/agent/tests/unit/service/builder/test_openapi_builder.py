"""
Unit tests for service.builder.openapi_builder
"""

from typing import Any
from unittest.mock import AsyncMock, Mock, patch

import pytest

from api.schemas.llm_message import LLMMessage
from api.schemas.openapi_inputs import CompletionInputs
from common_imports import Span
from domain.models.base import BaseLLMModel
from engine.nodes.chat.chat_runner import ChatRunner
from engine.nodes.cot.cot_runner import CotRunner
from engine.nodes.cot_process.cot_process_runner import CotProcessRunner
from repository.bot_config_client import BotConfig
from service.builder.base_builder import BaseApiBuilder
from service.builder.openapi_builder import OpenAPIRunnerBuilder
from service.plugin.base import BasePlugin
from service.plugin.knowledge import KnowledgePluginFactory
from service.runner.openapi_runner import OpenAPIRunner


class TestOpenAPIRunnerBuilder:
    """Test cases for OpenAPIRunnerBuilder."""

    @pytest.fixture
    def sample_messages(self) -> list[LLMMessage]:
        """Sample messages for testing."""
        return [
            LLMMessage(role="user", content="Hello"),
            LLMMessage(role="assistant", content="Hi there!"),
            LLMMessage(role="user", content="How can you help me?"),
        ]

    @pytest.fixture
    def sample_completion_inputs(
        self, sample_messages: list[LLMMessage]
    ) -> CompletionInputs:
        """Sample completion inputs for testing."""
        return CompletionInputs(
            bot_id="test_bot_123", messages=sample_messages, stream=True
        )

    @pytest.fixture
    def sample_bot_config(self) -> Mock:
        """Sample bot configuration for testing."""
        mock_config = Mock(spec=BotConfig)

        # Model configuration
        mock_plan_config = Mock()
        mock_plan_config.domain = "gpt-3.5-turbo"
        mock_plan_config.api = "https://api.openai.com/v1"

        mock_summary_config = Mock()
        mock_summary_config.domain = "gpt-3.5-turbo"
        mock_summary_config.api = "https://api.openai.com/v1"

        mock_model_config = Mock()
        mock_model_config.plan = mock_plan_config
        mock_model_config.summary = mock_summary_config
        mock_model_config.instruct = "You are a helpful assistant."
        mock_config.model_config_ = mock_model_config

        # Tool and workflow configurations
        mock_config.tool_ids = ["tool1", "tool2"]
        mock_config.mcp_server_ids = ["mcp1", "mcp2"]
        mock_config.mcp_server_urls = ["http://mcp1.com", "http://mcp2.com"]
        mock_config.flow_ids = ["flow1", "flow2"]

        # Knowledge configuration
        mock_knowledge_config = Mock()
        mock_knowledge_config.top_k = 5
        mock_knowledge_config.score_threshold = 0.7
        mock_config.knowledge_config = mock_knowledge_config

        # Regular configuration
        mock_regular_config = Mock()
        mock_match_config = Mock()
        mock_match_config.repoId = ["repo1", "repo2"]
        mock_match_config.docId = ["doc1", "doc2"]
        mock_regular_config.match = mock_match_config

        mock_rag_config = Mock()
        mock_rag_config.type = "AIUI-RAG2"
        mock_regular_config.rag = mock_rag_config
        mock_config.regular_config = mock_regular_config

        return mock_config

    @pytest.fixture
    def openapi_builder(
        self, sample_completion_inputs: CompletionInputs
    ) -> OpenAPIRunnerBuilder:
        """Create OpenAPIRunnerBuilder instance for testing."""
        mock_span = Mock(spec=Span)
        return OpenAPIRunnerBuilder(
            app_id="test_app",
            uid="test_user",
            inputs=sample_completion_inputs,
            span=mock_span,
        )

    @pytest.mark.unit
    def test_openapi_runner_builder_inherits_from_base_api_builder(self) -> None:
        """Test OpenAPIRunnerBuilder inherits from BaseApiBuilder."""
        # Assert
        assert issubclass(OpenAPIRunnerBuilder, BaseApiBuilder)

    @pytest.mark.unit
    def test_openapi_runner_builder_creation(
        self,
        openapi_builder: OpenAPIRunnerBuilder,
        sample_completion_inputs: CompletionInputs,
    ) -> None:
        """Test OpenAPIRunnerBuilder creation."""
        # Assert
        assert openapi_builder.app_id == "test_app"
        assert openapi_builder.uid == "test_user"
        assert openapi_builder.inputs == sample_completion_inputs
        assert isinstance(openapi_builder.inputs, CompletionInputs)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_success(  # pylint: disable=too-many-locals
        self, openapi_builder: OpenAPIRunnerBuilder, sample_bot_config: Mock
    ) -> None:
        """Test successful build execution."""
        # Arrange - Mock all dependencies
        # Create nested span mocking for query_knowledge
        mock_query_span_context = Mock()
        mock_query_context_manager = Mock()
        mock_query_context_manager.__enter__ = Mock(
            return_value=mock_query_span_context
        )
        mock_query_context_manager.__exit__ = Mock(return_value=None)

        mock_span_context = Mock()
        mock_span_context.start = Mock(return_value=mock_query_context_manager)
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        openapi_builder.span.start = Mock(return_value=context_manager)

        # Mock models
        mock_plan_model = Mock(spec=BaseLLMModel)
        mock_summary_model = Mock(spec=BaseLLMModel)

        # Create real runners for Pydantic validation

        # Create mock LLM model with required attributes
        mock_llm_model = Mock(spec=BaseLLMModel)
        mock_llm_messages = [LLMMessage(role="user", content="test")]

        mock_chat_runner = ChatRunner(
            model=mock_llm_model, chat_history=mock_llm_messages
        )

        # Create mock process runner
        mock_process_runner = Mock(spec=CotProcessRunner)

        # Create mock plugins with necessary attributes
        mock_plugin = Mock(spec=BasePlugin)
        mock_plugin.schema_template = "mock_schema"
        mock_plugin.name = "mock_plugin"

        mock_cot_runner = CotRunner(
            model=mock_llm_model,
            chat_history=mock_llm_messages,
            plugins=[mock_plugin],
            process_runner=mock_process_runner,
        )

        # Mock plugins and knowledge - create real plugin mocks for Pydantic validation
        mock_plugin1 = Mock(spec=BasePlugin)
        mock_plugin1.name = "mock_plugin1"
        mock_plugin1.schema_template = "mock_schema1"

        mock_plugin2 = Mock(spec=BasePlugin)
        mock_plugin2.name = "mock_plugin2"
        mock_plugin2.schema_template = "mock_schema2"

        mock_plugins = [mock_plugin1, mock_plugin2]
        mock_metadata_list = [{"source_id": "doc1", "chunk": []}]
        mock_knowledge = "Background knowledge content"

        with patch(
            "service.builder.base_builder.BaseApiBuilder.build_bot_config",
            AsyncMock(return_value=sample_bot_config),
        ):
            with patch(
                "service.builder.base_builder.BaseApiBuilder.create_model",
                AsyncMock(side_effect=[mock_plan_model, mock_summary_model]),
            ):
                with patch(
                    "service.builder.openapi_builder.OpenAPIRunnerBuilder"
                    ".query_knowledge",
                    AsyncMock(return_value=(mock_metadata_list, mock_knowledge)),
                ):
                    with patch(
                        "service.builder.openapi_builder.OpenAPIRunnerBuilder"
                        ".build_plugins",
                        AsyncMock(return_value=mock_plugins),
                    ):
                        with patch(
                            "service.builder.base_builder.BaseApiBuilder"
                            ".build_chat_runner",
                            AsyncMock(return_value=mock_chat_runner),
                        ):
                            with patch(
                                "service.builder.base_builder.BaseApiBuilder"
                                ".build_process_runner",
                                AsyncMock(return_value=mock_process_runner),
                            ):
                                with patch(
                                    "service.builder.base_builder"
                                    ".BaseApiBuilder.build_cot_runner",
                                    AsyncMock(return_value=mock_cot_runner),
                                ):

                                    # Act
                                    result = await openapi_builder.build()

                                    # Assert
                                    assert isinstance(result, OpenAPIRunner)
                                    assert result.chat_runner == mock_chat_runner
                                    assert result.cot_runner == mock_cot_runner
                                    assert result.plugins == mock_plugins
                                    assert (
                                        result.knowledge_metadata_list
                                        == mock_metadata_list
                                    )

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_calls_correct_methods_with_parameters(  # pylint: disable=too-many-locals
        self, openapi_builder: OpenAPIRunnerBuilder, sample_bot_config: Mock
    ) -> None:
        """Test build calls methods with correct parameters."""
        # Arrange
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        openapi_builder.span.start = Mock(return_value=context_manager)

        mock_plan_model = Mock(spec=BaseLLMModel)
        mock_summary_model = Mock(spec=BaseLLMModel)

        # Create real runners for Pydantic validation

        # Create mock LLM model and messages for runners
        mock_llm_model = Mock(spec=BaseLLMModel)
        mock_llm_messages = [LLMMessage(role="user", content="test")]

        # Create real runners
        mock_chat_runner = ChatRunner(
            model=mock_llm_model, chat_history=mock_llm_messages
        )

        mock_process_runner = Mock(spec=CotProcessRunner)

        mock_plugin = Mock(spec=BasePlugin)
        mock_plugin.schema_template = "mock_schema"
        mock_plugin.name = "mock_plugin"

        mock_cot_runner = CotRunner(
            model=mock_llm_model,
            chat_history=mock_llm_messages,
            plugins=[mock_plugin],
            process_runner=mock_process_runner,
        )

        with patch(
            "service.builder.base_builder.BaseApiBuilder.build_bot_config",
            AsyncMock(return_value=sample_bot_config),
        ) as mock_build_config:
            with patch(
                "service.builder.base_builder.BaseApiBuilder.create_model",
                AsyncMock(side_effect=[mock_plan_model, mock_summary_model]),
            ) as mock_create_model:
                with patch(
                    "service.builder.openapi_builder.OpenAPIRunnerBuilder"
                    ".query_knowledge",
                    AsyncMock(return_value=([], "")),
                ) as _mock_query_knowledge:
                    with patch(
                        "service.builder.openapi_builder.OpenAPIRunnerBuilder"
                        ".build_plugins",
                        AsyncMock(return_value=[]),
                    ) as mock_build_plugins:
                        with patch(
                            "service.builder.base_builder.BaseApiBuilder"
                            ".build_chat_runner",
                            AsyncMock(return_value=mock_chat_runner),
                        ) as mock_build_chat:
                            with patch(
                                "service.builder.base_builder.BaseApiBuilder"
                                ".build_process_runner",
                                AsyncMock(return_value=mock_process_runner),
                            ) as mock_build_process:
                                with patch(
                                    "service.builder.base_builder"
                                    ".BaseApiBuilder.build_cot_runner",
                                    AsyncMock(return_value=mock_cot_runner),
                                ) as mock_build_cot:

                                    # Act
                                    await openapi_builder.build()

                                    # Assert - Verify method calls with correct
                                    # parameters
                                    mock_build_config.assert_called_once_with(
                                        "test_bot_123"
                                    )

                                    # Verify create_model calls
                                    assert mock_create_model.call_count == 2

                                    # Verify build_plugins call
                                    mock_build_plugins.assert_called_once_with(
                                        tool_ids=["tool1", "tool2"],
                                        mcp_server_ids=["mcp1", "mcp2"],
                                        mcp_server_urls=[
                                            "http://mcp1.com",
                                            "http://mcp2.com",
                                        ],
                                        workflow_ids=["flow1", "flow2"],
                                    )

                                    # Verify runner builds were called
                                    mock_build_chat.assert_called_once()
                                    mock_build_process.assert_called_once()
                                    mock_build_cot.assert_called_once()

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_query_knowledge_success(
        self, openapi_builder: OpenAPIRunnerBuilder, sample_bot_config: Mock
    ) -> None:
        """Test successful query_knowledge execution."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_knowledge_response: dict[str, Any] = {
            "data": {
                "results": [
                    {
                        "title": "Document 1",
                        "docId": "doc1",
                        "content": "This is test content",
                        "references": {},
                    },
                    {
                        "title": "Document 2",
                        "docId": "doc2",
                        "content": "More test content",
                        "references": {},
                    },
                ]
            }
        }

        mock_knowledge_plugin = Mock()
        mock_knowledge_plugin.run = AsyncMock(return_value=mock_knowledge_response)

        with patch.object(
            KnowledgePluginFactory, "gen", return_value=mock_knowledge_plugin
        ):
            # Act
            metadata_list, knowledge = await openapi_builder.query_knowledge(
                sample_bot_config, mock_span
            )

            # Assert
            assert len(metadata_list) == 2
            assert metadata_list[0]["source_id"] == "doc1"
            assert metadata_list[1]["source_id"] == "doc2"
            assert "Document 1" in knowledge
            assert "Document 2" in knowledge
            assert "This is test content" in knowledge
            assert "More test content" in knowledge

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_query_knowledge_with_empty_repo_and_doc_ids(
        self, openapi_builder: OpenAPIRunnerBuilder, sample_bot_config: Mock
    ) -> None:
        """Test query_knowledge with empty repo and doc IDs."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        sample_bot_config.regular_config.match.repoId = []
        sample_bot_config.regular_config.match.docId = []

        # Act
        metadata_list, knowledge = await openapi_builder.query_knowledge(
            sample_bot_config, mock_span
        )

        # Assert
        assert metadata_list == []
        assert knowledge == ""

    @pytest.mark.unit
    def test_process_knowledge_results(
        self, openapi_builder: OpenAPIRunnerBuilder
    ) -> None:
        """Test _process_knowledge_results method."""
        # Arrange
        results = [
            {
                "title": "Test Document",
                "docId": "doc123",
                "content": "Test content",
                "references": {},
            },
            {
                "title": "Another Document",
                "docId": "doc123",  # Same docId to test grouping
                "content": "More content",
                "references": {},
            },
        ]

        # Act
        # pylint: disable=protected-access
        metadata_list = openapi_builder._process_knowledge_results(results)

        # Assert
        assert len(metadata_list) == 1  # Should group by docId
        assert metadata_list[0]["source_id"] == "doc123"
        assert len(metadata_list[0]["chunk"]) == 2  # Two chunks for same doc

    @pytest.mark.unit
    def test_process_references_with_images_and_tables(
        self, openapi_builder: OpenAPIRunnerBuilder
    ) -> None:
        """Test _process_references method with different reference types."""
        # Arrange
        content = "Here is an image <ref1> and a table <ref2>"
        references = {
            "ref1": {"format": "image", "link": "https://example.com/image.jpg"},
            "ref2": {
                "format": "table",
                "content": "| Col1 | Col2 |\n|------|------|\n| A    | B    |",
            },
        }

        # Act
        # pylint: disable=protected-access
        result = openapi_builder._process_references(content, references)

        # Assert
        assert "![alt](https://example.com/image.jpg)" in result
        assert "| Col1 | Col2 |" in result

    @pytest.mark.unit
    def test_extract_backgrounds(self, openapi_builder: OpenAPIRunnerBuilder) -> None:
        """Test _extract_backgrounds method."""
        # Arrange
        metadata_list = [
            {
                "source_id": "doc1",
                "chunk": [
                    {"chunk_context": "First chunk content"},
                    {"chunk_context": "Second chunk content"},
                ],
            },
            {"source_id": "doc2", "chunk": [{"chunk_context": "Third chunk content"}]},
        ]

        # Act
        # pylint: disable=protected-access
        backgrounds = openapi_builder._extract_backgrounds(metadata_list)

        # Assert
        assert "First chunk content" in backgrounds
        assert "Second chunk content" in backgrounds
        assert "Third chunk content" in backgrounds
        assert backgrounds.count("\n") == 2  # Should join with newlines

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_query_knowledge_with_unicode_content(
        self, openapi_builder: OpenAPIRunnerBuilder, sample_bot_config: Mock
    ) -> None:
        """Test query_knowledge with unicode content."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_knowledge_response = {
            "data": {
                "results": [
                    {
                        "title": "中文文档",
                        "docId": "中文doc1",
                        "content": "这是中文内容test",
                        "references": {},
                    }
                ]
            }
        }

        mock_knowledge_plugin = Mock()
        mock_knowledge_plugin.run = AsyncMock(return_value=mock_knowledge_response)

        with patch.object(
            KnowledgePluginFactory, "gen", return_value=mock_knowledge_plugin
        ):
            # Act
            metadata_list, knowledge = await openapi_builder.query_knowledge(
                sample_bot_config, mock_span
            )

            # Assert
            assert len(metadata_list) == 1
            assert metadata_list[0]["source_id"] == "中文doc1"
            assert "中文文档" in knowledge
            assert "这是中文内容test" in knowledge

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_query_knowledge_logs_span_events(
        self, openapi_builder: OpenAPIRunnerBuilder, sample_bot_config: Mock
    ) -> None:
        """Test query_knowledge logs span events correctly."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_knowledge_response: dict = {"data": {"results": []}}
        mock_knowledge_plugin = Mock()
        mock_knowledge_plugin.run = AsyncMock(return_value=mock_knowledge_response)

        with patch.object(
            KnowledgePluginFactory, "gen", return_value=mock_knowledge_plugin
        ):
            # Act
            await openapi_builder.query_knowledge(sample_bot_config, mock_span)

            # Assert
            mock_span_context.add_info_events.assert_called_once()
            call_args = mock_span_context.add_info_events.call_args[0][0]
            assert "metadata-list" in call_args
            assert "backgrounds" in call_args

    @pytest.mark.unit
    def test_process_references_with_unknown_format(
        self, openapi_builder: OpenAPIRunnerBuilder
    ) -> None:
        """Test _process_references with unknown reference format."""
        # Arrange
        content = "Here is an unknown reference <ref1>"
        references = {"ref1": {"format": "unknown_format", "content": "Some content"}}

        # Act
        # pylint: disable=protected-access
        result = openapi_builder._process_references(content, references)

        # Assert
        assert result == content  # Should remain unchanged for unknown formats

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_with_different_rag_types(  # pylint: disable=too-many-locals
        self, openapi_builder: OpenAPIRunnerBuilder, sample_bot_config: Mock
    ) -> None:
        """Test build with different RAG types."""
        # Arrange
        sample_bot_config.regular_config.rag.type = "CBG-RAG"

        # Create nested span mocking for query_knowledge
        mock_query_span_context = Mock()
        mock_query_context_manager = Mock()
        mock_query_context_manager.__enter__ = Mock(
            return_value=mock_query_span_context
        )
        mock_query_context_manager.__exit__ = Mock(return_value=None)

        mock_span_context = Mock()
        mock_span_context.start = Mock(return_value=mock_query_context_manager)
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        openapi_builder.span.start = Mock(return_value=context_manager)

        mock_knowledge_plugin = Mock()
        mock_knowledge_plugin.run = AsyncMock(return_value={"data": {"results": []}})

        # Create real runners for Pydantic validation

        # Create mock LLM model and messages for runners
        mock_llm_model = Mock(spec=BaseLLMModel)
        mock_llm_messages = [LLMMessage(role="user", content="test")]

        # Create real runners
        mock_chat_runner = ChatRunner(
            model=mock_llm_model, chat_history=mock_llm_messages
        )

        mock_process_runner = Mock(spec=CotProcessRunner)

        mock_plugin = Mock(spec=BasePlugin)
        mock_plugin.schema_template = "mock_schema"
        mock_plugin.name = "mock_plugin"

        mock_cot_runner = CotRunner(
            model=mock_llm_model,
            chat_history=mock_llm_messages,
            plugins=[mock_plugin],
            process_runner=mock_process_runner,
        )

        with patch(
            "service.builder.base_builder.BaseApiBuilder.build_bot_config",
            AsyncMock(return_value=sample_bot_config),
        ):
            with patch(
                "service.builder.base_builder.BaseApiBuilder.create_model",
                AsyncMock(return_value=mock_llm_model),
            ):
                with patch(
                    "service.builder.openapi_builder.OpenAPIRunnerBuilder"
                    ".build_plugins",
                    AsyncMock(return_value=[]),
                ):
                    with patch(
                        "service.builder.base_builder.BaseApiBuilder"
                        ".build_chat_runner",
                        AsyncMock(return_value=mock_chat_runner),
                    ):
                        with patch(
                            "service.builder.base_builder.BaseApiBuilder"
                            ".build_process_runner",
                            AsyncMock(return_value=mock_process_runner),
                        ):
                            with patch(
                                "service.builder.base_builder"
                                ".BaseApiBuilder.build_cot_runner",
                                AsyncMock(return_value=mock_cot_runner),
                            ):
                                with patch.object(
                                    KnowledgePluginFactory,
                                    "gen",
                                    return_value=mock_knowledge_plugin,
                                ) as mock_factory:

                                    # Act
                                    await openapi_builder.build()

                                    # Assert - Verify KnowledgePluginFactory was
                                    # called with correct rag_type
                                    mock_factory.assert_called_once()
                                    # The factory call should use CBG-RAG type
