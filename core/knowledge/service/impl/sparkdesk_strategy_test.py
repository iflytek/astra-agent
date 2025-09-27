# -*- coding: utf-8 -*-
"""
SparkDesk RAG strategy test module.

This module contains unit tests for the SparkDeskRAGStrategy class, testing its query functionality and exception handling.
"""

from unittest.mock import MagicMock, patch

import pytest

from knowledge.service.impl.sparkdesk_strategy import SparkDeskRAGStrategy
from knowledge.service.rag_strategy import RAGStrategy

pytestmark = pytest.mark.asyncio


class TestSparkDeskRAGStrategy:
    """Test SparkDeskRAGStrategy class."""

    @pytest.fixture
    def strategy(self) -> SparkDeskRAGStrategy:
        """Provide a SparkDeskRAGStrategy instance as test fixture."""
        return SparkDeskRAGStrategy()

    def test_inheritance(self, strategy: SparkDeskRAGStrategy) -> None:
        """Test that SparkDeskRAGStrategy inherits from RAGStrategy."""
        assert isinstance(strategy, RAGStrategy)
        assert isinstance(strategy, SparkDeskRAGStrategy)

    @patch("knowledge.service.impl.sparkdesk_strategy.sparkdesk_query_async")
    async def test_query_success(
        self, mock_sparkdesk_query: MagicMock, strategy: SparkDeskRAGStrategy
    ) -> None:
        """Test successful query execution."""
        # Mock the sparkdesk_query_async function
        expected_result = {"data": "test_result", "status": "success"}
        mock_sparkdesk_query.return_value = expected_result

        # Test query
        result = await strategy.query(
            query="test query", repo_ids=["repo1", "repo2"], top_k=5, threshold=0.8
        )

        # Verify result
        assert result == {"results": expected_result}
        mock_sparkdesk_query.assert_called_once_with("test query", ["repo1", "repo2"])

    @patch("knowledge.service.impl.sparkdesk_strategy.sparkdesk_query_async")
    async def test_query_minimal_params(
        self, mock_sparkdesk_query: MagicMock, strategy: SparkDeskRAGStrategy
    ) -> None:
        """Test query with minimal parameters."""
        expected_result = {"data": "minimal_test"}
        mock_sparkdesk_query.return_value = expected_result

        result = await strategy.query(query="minimal query")

        assert result == {"results": expected_result}
        mock_sparkdesk_query.assert_called_once_with("minimal query", None)

    @patch("knowledge.service.impl.sparkdesk_strategy.sparkdesk_query_async")
    async def test_query_with_kwargs(
        self, mock_sparkdesk_query: MagicMock, strategy: SparkDeskRAGStrategy
    ) -> None:
        """Test query with additional kwargs."""
        expected_result = {"data": "kwargs_test"}
        mock_sparkdesk_query.return_value = expected_result

        result = await strategy.query(
            query="kwargs query",
            repo_ids=["repo1"],
            flow_id="flow123",
            custom_param="custom_value",
        )

        assert result == {"results": expected_result}
        mock_sparkdesk_query.assert_called_once_with(
            "kwargs query", ["repo1"], flow_id="flow123", custom_param="custom_value"
        )

    @patch("knowledge.service.impl.sparkdesk_strategy.sparkdesk_query_async")
    async def test_query_exception_propagation(
        self, mock_sparkdesk_query: MagicMock, strategy: SparkDeskRAGStrategy
    ) -> None:
        """Test that exceptions from sparkdesk_query_async are propagated."""
        mock_sparkdesk_query.side_effect = Exception("SparkDesk API error")

        with pytest.raises(Exception, match="SparkDesk API error"):
            await strategy.query(query="error query")

    async def test_split_not_implemented(self, strategy: SparkDeskRAGStrategy) -> None:
        """Test that split method raises NotImplementedError."""
        with pytest.raises(
            NotImplementedError, match="SparkDesk-RAG does not support split operation"
        ):
            await strategy.split(
                file="test_file.txt",
                lengthRange=[100, 500],
                overlap=50,
                resourceType=1,
                separator=["\n"],
                titleSplit=True,
                cutOff=["EOF"],
            )

    async def test_chunks_save_not_implemented(
        self, strategy: SparkDeskRAGStrategy
    ) -> None:
        """Test that chunks_save method raises NotImplementedError."""
        with pytest.raises(
            NotImplementedError,
            match="SparkDesk-RAG does not support chunks_save operation",
        ):
            await strategy.chunks_save(
                docId="doc123",
                group="test_group",
                uid="user123",
                chunks=[{"content": "test"}],
            )

    async def test_chunks_update_not_implemented(
        self, strategy: SparkDeskRAGStrategy
    ) -> None:
        """Test that chunks_update method raises NotImplementedError."""
        with pytest.raises(
            NotImplementedError,
            match="SparkDesk-RAG does not support chunks_update operation",
        ):
            await strategy.chunks_update(
                docId="doc123",
                group="test_group",
                uid="user123",
                chunks=[{"id": "chunk1", "content": "updated"}],
            )

    async def test_chunks_delete_not_implemented(
        self, strategy: SparkDeskRAGStrategy
    ) -> None:
        """Test that chunks_delete method raises NotImplementedError."""
        with pytest.raises(
            NotImplementedError,
            match="SparkDesk-RAG does not support chunks_delete operation",
        ):
            await strategy.chunks_delete(docId="doc123", chunkIds=["chunk1", "chunk2"])

    async def test_query_doc_not_implemented(
        self, strategy: SparkDeskRAGStrategy
    ) -> None:
        """Test that query_doc method raises NotImplementedError."""
        with pytest.raises(
            NotImplementedError,
            match="SparkDesk-RAG does not support query_doc operation",
        ):
            await strategy.query_doc(docId="doc123")

    async def test_query_doc_name_not_implemented(
        self, strategy: SparkDeskRAGStrategy
    ) -> None:
        """Test that query_doc_name method raises NotImplementedError."""
        with pytest.raises(
            NotImplementedError,
            match="SparkDesk-RAG does not support query_doc_name operation",
        ):
            await strategy.query_doc_name(docId="doc123")

    async def test_all_abstract_methods_implemented(
        self, strategy: SparkDeskRAGStrategy
    ) -> None:
        """Test that all abstract methods from RAGStrategy are implemented (even if they raise NotImplementedError)."""
        methods_to_test = [
            ("query", {"query": "test"}),
            (
                "split",
                {
                    "file": "test",
                    "lengthRange": [100],
                    "overlap": 0,
                    "resourceType": 1,
                    "separator": [],
                    "titleSplit": False,
                    "cutOff": [],
                },
            ),
            (
                "chunks_save",
                {"docId": "test", "group": "test", "uid": "test", "chunks": []},
            ),
            (
                "chunks_update",
                {"docId": "test", "group": "test", "uid": "test", "chunks": []},
            ),
            ("chunks_delete", {"docId": "test", "chunkIds": []}),
            ("query_doc", {"docId": "test"}),
            ("query_doc_name", {"docId": "test"}),
        ]

        for method_name, kwargs in methods_to_test:
            method = getattr(strategy, method_name)
            assert callable(method), f"{method_name} should be callable"

            # For query method, mock the external dependency
            if method_name == "query":
                with patch(
                    "knowledge.service.impl.sparkdesk_strategy.sparkdesk_query_async"
                ) as mock_query:
                    mock_query.return_value = {}
                    result = await method(**kwargs)
                    assert isinstance(result, dict)
            else:
                # Other methods should raise NotImplementedError
                with pytest.raises(NotImplementedError):
                    await method(**kwargs)
