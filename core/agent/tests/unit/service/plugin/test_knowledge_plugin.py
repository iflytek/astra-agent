"""
Unit tests for service.plugin.knowledge
"""

import asyncio
from typing import Any, Dict
from unittest.mock import AsyncMock, Mock, patch

import aiohttp
import pytest

from common_imports import Span
from exceptions.plugin_exc import KnowledgeQueryExc
from service.plugin.base import BasePlugin
from service.plugin.knowledge import KnowledgePlugin, KnowledgePluginFactory


class AsyncContextManager:
    """Proper async context manager for mocking aiohttp responses."""

    def __init__(self, return_value: Any) -> None:
        self._return_value = return_value

    async def __aenter__(self) -> Any:
        return self._return_value

    async def __aexit__(self, exc_type: Any, exc_val: Any, exc_tb: Any) -> None:
        return None


class TestKnowledgePlugin:
    """Test cases for KnowledgePlugin."""

    @pytest.mark.unit
    def test_knowledge_plugin_inherits_from_base_plugin(self) -> None:
        """Test KnowledgePlugin inherits from BasePlugin."""
        # Assert
        assert issubclass(KnowledgePlugin, BasePlugin)

    @pytest.mark.unit
    def test_knowledge_plugin_creation(self) -> None:
        """Test KnowledgePlugin can be instantiated."""
        # Act
        plugin = KnowledgePlugin(
            name="knowledge",
            description="Knowledge plugin for testing",
            schema_template="test_schema",
            typ="knowledge",
            run=AsyncMock(),
        )

        # Assert
        assert plugin.name == "knowledge"
        assert plugin.description == "Knowledge plugin for testing"
        assert plugin.typ == "knowledge"
        assert plugin.schema_template == "test_schema"

    @pytest.mark.unit
    def test_knowledge_plugin_class_structure(self) -> None:
        """Test KnowledgePlugin class structure."""
        # Assert
        assert KnowledgePlugin.__name__ == "KnowledgePlugin"
        assert hasattr(KnowledgePlugin, "__init__")

        # Check inheritance
        assert KnowledgePlugin.__bases__ == (BasePlugin,)


class TestKnowledgePluginFactory:
    """Test cases for KnowledgePluginFactory."""

    @pytest.fixture
    def sample_factory_data(self) -> Dict[str, Any]:
        """Sample factory data for testing."""
        return {
            "query": "test query",
            "top_k": 5,
            "repo_ids": ["repo1", "repo2"],
            "doc_ids": ["doc1", "doc2"],
            "score_threshold": 0.7,
            "rag_type": "CBG-RAG",
        }

    @pytest.fixture
    def knowledge_factory(
        self, sample_factory_data: Dict[str, Any]
    ) -> KnowledgePluginFactory:
        """Create KnowledgePluginFactory instance for testing."""
        return KnowledgePluginFactory(**sample_factory_data)

    @pytest.mark.unit
    def test_knowledge_plugin_factory_creation_with_all_fields(
        self, sample_factory_data: Dict[str, Any]
    ) -> None:
        """Test KnowledgePluginFactory creation with all fields."""
        # Act
        factory = KnowledgePluginFactory(**sample_factory_data)

        # Assert
        assert factory.query == "test query"
        assert factory.top_k == 5
        assert factory.repo_ids == ["repo1", "repo2"]
        assert factory.doc_ids == ["doc1", "doc2"]
        assert factory.score_threshold == 0.7
        assert factory.rag_type == "CBG-RAG"

    @pytest.mark.unit
    def test_knowledge_plugin_factory_gen(
        self, knowledge_factory: KnowledgePluginFactory
    ) -> None:
        """Test factory generates KnowledgePlugin correctly."""
        # Act
        plugin = knowledge_factory.gen()

        # Assert
        assert isinstance(plugin, KnowledgePlugin)
        assert plugin.name == "knowledge"
        assert plugin.description == "knowledge plugin"
        assert plugin.typ == "knowledge"
        assert callable(plugin.run)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_retrieve_success_with_cbg_rag(
        self, knowledge_factory: KnowledgePluginFactory
    ) -> None:
        """Test successful retrieve with CBG-RAG type."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        expected_response = {
            "code": 0,
            "data": {
                "chunks": [
                    {"content": "Test content 1", "score": 0.9},
                    {"content": "Test content 2", "score": 0.8},
                ]
            },
        }

        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.json = AsyncMock(return_value=expected_response)
        mock_response.read = AsyncMock(return_value=b'{"code": 0}')
        mock_response.raise_for_status = Mock()

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch("aiohttp.ClientSession", return_value=session_context):
            with patch("service.plugin.knowledge.agent_config") as mock_config:
                mock_config.chunk_query_url = "http://test-url"

                # Act
                result = await knowledge_factory.retrieve(mock_span)

                # Assert
                assert result == expected_response
                mock_session.post.assert_called_once()

                # Verify request data structure for CBG-RAG
                call_args = mock_session.post.call_args
                request_data = call_args[1]["json"]
                assert request_data["query"] == "test query"
                assert request_data["topN"] == "5"
                assert request_data["ragType"] == "CBG-RAG"
                assert request_data["match"]["docIds"] == ["doc1", "doc2"]

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_retrieve_success_with_non_cbg_rag(self) -> None:
        """Test successful retrieve with non-CBG-RAG type."""
        # Arrange
        factory = KnowledgePluginFactory(
            query="test query",
            top_k=3,
            repo_ids=["repo1"],
            doc_ids=[],
            score_threshold=0.8,
            rag_type="BASIC-RAG",
        )

        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        expected_response = {"code": 0, "data": {"chunks": []}}

        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.json = AsyncMock(return_value=expected_response)
        mock_response.read = AsyncMock(return_value=b'{"code": 0}')
        mock_response.raise_for_status = Mock()

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch("aiohttp.ClientSession", return_value=session_context):
            with patch("service.plugin.knowledge.agent_config") as mock_config:
                mock_config.chunk_query_url = "http://test-url"

                # Act
                result = await factory.retrieve(mock_span)

                # Assert
                assert result == expected_response

                # Verify request data structure for non-CBG-RAG
                call_args = mock_session.post.call_args
                request_data = call_args[1]["json"]
                assert "docIds" not in request_data.get("match", {})

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_retrieve_with_empty_repo_ids(
        self, knowledge_factory: KnowledgePluginFactory
    ) -> None:
        """Test retrieve with empty repo_ids returns empty response."""
        # Arrange
        knowledge_factory.repo_ids = []

        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        # Act
        result = await knowledge_factory.retrieve(mock_span)

        # Assert
        assert result == {}

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_retrieve_http_error_response(
        self, knowledge_factory: KnowledgePluginFactory
    ) -> None:
        """Test retrieve with HTTP error response."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_response = AsyncMock()
        mock_response.status = 500
        mock_response.read = AsyncMock(return_value=b"Internal Server Error")
        mock_response.raise_for_status = Mock()

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch("aiohttp.ClientSession", return_value=session_context):
            with patch("service.plugin.knowledge.agent_config") as mock_config:
                mock_config.chunk_query_url = "http://test-url"

                # Act & Assert
                with pytest.raises(type(KnowledgeQueryExc)) as exc_info:
                    await knowledge_factory.retrieve(mock_span)

                assert "Failed to query knowledge base" in str(exc_info.value)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_retrieve_http_exception(
        self, knowledge_factory: KnowledgePluginFactory
    ) -> None:
        """Test retrieve with HTTP exception."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_response = AsyncMock()
        mock_response.raise_for_status = Mock(
            side_effect=aiohttp.ClientError("Network error")
        )

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch("aiohttp.ClientSession", return_value=session_context):
            with patch("service.plugin.knowledge.agent_config") as mock_config:
                mock_config.chunk_query_url = "http://test-url"

                # Act & Assert
                with pytest.raises(aiohttp.ClientError):
                    await knowledge_factory.retrieve(mock_span)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_retrieve_timeout_error(
        self, knowledge_factory: KnowledgePluginFactory
    ) -> None:
        """Test retrieve with timeout error."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
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

        with patch("aiohttp.ClientSession", return_value=session_context):
            with patch("service.plugin.knowledge.agent_config") as mock_config:
                mock_config.chunk_query_url = "http://test-url"

                # Act & Assert
                with pytest.raises(type(KnowledgeQueryExc)) as exc_info:
                    await knowledge_factory.retrieve(mock_span)

                assert "Failed to query knowledge base" in str(exc_info.value)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_retrieve_with_unicode_query(self) -> None:
        """Test retrieve with unicode query."""
        # Arrange
        factory = KnowledgePluginFactory(
            query="test中文查询",
            top_k=5,
            repo_ids=["中文repo"],
            doc_ids=["中文doc"],
            score_threshold=0.7,
            rag_type="CBG-RAG",
        )

        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        expected_response = {
            "code": 0,
            "data": {"chunks": [{"content": "中文内容", "score": 0.9}]},
        }

        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.json = AsyncMock(return_value=expected_response)
        mock_response.read = AsyncMock(return_value=b'{"code": 0}')
        mock_response.raise_for_status = Mock()

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch("aiohttp.ClientSession", return_value=session_context):
            with patch("service.plugin.knowledge.agent_config") as mock_config:
                mock_config.chunk_query_url = "http://test-url"

                # Act
                result = await factory.retrieve(mock_span)

                # Assert
                assert result == expected_response

                # Verify unicode content is handled correctly
                call_args = mock_session.post.call_args
                request_data = call_args[1]["json"]
                assert request_data["query"] == "test中文查询"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_retrieve_span_events_logging(
        self, knowledge_factory: KnowledgePluginFactory
    ) -> None:
        """Test retrieve logs span events correctly."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        expected_response = {"code": 0, "data": {"chunks": []}}

        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.json = AsyncMock(return_value=expected_response)
        mock_response.read = AsyncMock(return_value=b'{"code": 0}')
        mock_response.raise_for_status = Mock()

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch("aiohttp.ClientSession", return_value=session_context):
            with patch("service.plugin.knowledge.agent_config") as mock_config:
                mock_config.chunk_query_url = "http://test-url"

                # Act
                await knowledge_factory.retrieve(mock_span)

                # Assert - Verify span events were logged
                assert mock_span_context.add_info_events.call_count >= 2

                # Check that request and response data were logged
                call_args_list = mock_span_context.add_info_events.call_args_list
                logged_events = [call[0][0] for call in call_args_list]

                # Should have logged request data
                assert any("request-data" in event for event in logged_events)
                # Should have logged response data
                assert any("response-data" in event for event in logged_events)

    @pytest.mark.unit
    def test_knowledge_plugin_factory_validation(self) -> None:
        """Test KnowledgePluginFactory field validation."""
        # Test with invalid types - should raise validation error
        with pytest.raises(ValueError):
            KnowledgePluginFactory(
                query=123,  # type: ignore  # Should be string
                top_k="invalid",  # type: ignore  # Should be int
                repo_ids="not_a_list",  # type: ignore  # Should be list
                doc_ids="not_a_list",  # type: ignore  # Should be list
                score_threshold="invalid",  # type: ignore  # Should be float
                rag_type=123,  # type: ignore  # Should be string
            )

    @pytest.mark.unit
    def test_knowledge_plugin_factory_minimal_data(self) -> None:
        """Test KnowledgePluginFactory with minimal valid data."""
        # Act
        factory = KnowledgePluginFactory(
            query="", top_k=0, repo_ids=[], doc_ids=[], score_threshold=0.0, rag_type=""
        )

        # Assert
        assert factory.query == ""
        assert factory.top_k == 0
        assert factory.repo_ids == []
        assert factory.doc_ids == []
        assert factory.score_threshold == 0.0
        assert factory.rag_type == ""

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_retrieve_request_data_structure(
        self, knowledge_factory: KnowledgePluginFactory
    ) -> None:
        """Test retrieve constructs request data correctly."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        expected_response = {"code": 0, "data": {}}

        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.json = AsyncMock(return_value=expected_response)
        mock_response.read = AsyncMock(return_value=b'{"code": 0}')
        mock_response.raise_for_status = Mock()

        # Create async context manager for session.post
        async_context = AsyncContextManager(mock_response)

        # Mock aiohttp session
        mock_session = Mock()
        mock_session.post = Mock(return_value=async_context)

        # Mock ClientSession context manager
        session_context = AsyncContextManager(mock_session)

        with patch("aiohttp.ClientSession", return_value=session_context):
            with patch("service.plugin.knowledge.agent_config") as mock_config:
                mock_config.chunk_query_url = "http://test-url"

                # Act
                await knowledge_factory.retrieve(mock_span)

                # Assert
                call_args = mock_session.post.call_args
                request_data = call_args[1]["json"]

                # Verify all expected fields are present
                assert "query" in request_data
                assert "topN" in request_data
                assert "match" in request_data
                assert "ragType" in request_data

                # Verify specific values
                assert request_data["query"] == "test query"
                assert request_data["topN"] == "5"  # Should be string
                assert request_data["ragType"] == "CBG-RAG"
                assert request_data["match"]["repoId"] == ["repo1", "repo2"]
                assert request_data["match"]["threshold"] == 0.7
