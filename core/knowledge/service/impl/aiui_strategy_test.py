from unittest.mock import AsyncMock, patch

import pytest

from knowledge.service import AIUIRAGStrategy


class TestAIUIRAGStrategy:
    """AIUI RAG strategy unit tests"""

    @pytest.fixture
    def strategy(self):
        """Create test strategy instance"""
        return AIUIRAGStrategy()

    @pytest.fixture
    def mock_aiui(self):
        """Mock aiui module"""
        with patch("knowledge.service.impl.aiui_strategy.aiui") as mock:
            yield mock

    @pytest.mark.asyncio
    async def test_query_success(self, strategy, mock_aiui):
        """Test successful query scenario"""
        # Mock return values
        mock_response = {
            "query": "test query",
            "count": 2,
            "results": [
                {
                    "score": 0.95,
                    "docId": "doc1",
                    "title": "Test Document",
                    "content": "Test content",
                    "context": "Test context",
                    "chunkId": "chunk1",
                    "references": {"ref1": "value1"},
                    "docInfo": {"documentName": "test.pdf"},
                },
                {
                    "score": 0.85,
                    "docId": "doc2",
                    "title": "Test Document 2",
                    "content": "Test content 2",
                    "context": "Test context 2",
                    "chunkId": "chunk2",
                    "references": {"ref2": "value2"},
                    "docInfo": {"documentName": "test2.pdf"},
                },
            ],
        }

        mock_aiui.chunk_query = AsyncMock(return_value=mock_response)

        # Execute query
        result = await strategy.query(
            query="test query",
            doc_ids=["doc1", "doc2"],
            repo_ids=["repo1"],
            top_k=10,
            threshold=0.8,
        )

        # Verify results
        assert result["query"] == "test query"
        assert result["count"] == 2
        assert len(result["results"]) == 2
        assert result["results"][0]["fileName"] == "test.pdf"
        assert result["results"][1]["fileName"] == "test2.pdf"

        # Verify call parameters
        mock_aiui.chunk_query.assert_called_once_with(
            "test query", ["doc1", "doc2"], ["repo1"], 10, 0.8
        )

    @pytest.mark.asyncio
    async def test_query_empty_results(self, strategy, mock_aiui):
        """Test query returning empty results"""
        # Mock empty return values
        mock_aiui.chunk_query = AsyncMock(return_value=None)

        # Execute query
        result = await strategy.query("test query")

        # Verify results
        assert result["query"] == "test query"
        assert result["count"] == 0
        assert result["results"] == []

    @pytest.mark.asyncio
    async def test_query_partial_empty_results(self, strategy, mock_aiui):
        """Test query returning partial empty results"""
        # Mock partial empty return values
        mock_response = {
            "query": "test query",
            "count": 2,
            "results": None,  # This is empty result
        }

        mock_aiui.chunk_query = AsyncMock(return_value=mock_response)

        # Execute query
        result = await strategy.query("test query")

        # Verify results
        assert result["query"] == "test query"
        assert result["count"] == 0
        assert result["results"] == []

    @pytest.mark.asyncio
    async def test_split_success(self, strategy, mock_aiui):
        """Test successful file splitting scenario"""
        # Mock document parsing return values
        mock_doc_data = {"content": "parsed document content"}
        mock_aiui.document_parse = AsyncMock(return_value=mock_doc_data)

        # Mock chunk return values
        mock_chunk_data = [
            {
                "docId": "doc1",
                "chunkId": "chunk1",
                "title": "Chunk 1",
                "content": "Content 1",
                "context": "Context 1",
                "references": {"ref1": "value1"},
                "docInfo": {"info": "value"},
            }
        ]
        mock_aiui.chunk_split = AsyncMock(return_value=mock_chunk_data)

        # Execute splitting
        result = await strategy.split(
            file="test file content",
            lengthRange=[16, 512],
            overlap=16,
            resourceType=1,
            separator=["。", "！", "；", "？"],
            titleSplit=True,
            cutOff=["---", "===="],
        )

        # Verify results
        assert len(result) == 1
        assert result[0]["docId"] == "doc1"
        assert result[0]["dataIndex"] == "chunk1"
        assert result[0]["title"] == "Chunk 1"

        # Verify call parameters
        mock_aiui.document_parse.assert_called_once_with("test file content", 1)
        mock_aiui.chunk_split.assert_called_once_with(
            lengthRange=[16, 512],
            document=mock_doc_data,
            overlap=16,
            cutOff=["---", "===="],
            separator=["。", "！", "；", "？"],
            titleSplit=True,
        )

    @pytest.mark.asyncio
    async def test_chunks_save(self, strategy, mock_aiui):
        """Test chunk saving"""
        mock_aiui.chunk_save = AsyncMock(return_value="save_result")

        chunks = [{"content": "chunk1"}, {"content": "chunk2"}]
        result = await strategy.chunks_save("doc1", "group1", "user1", chunks)

        assert result == "save_result"
        mock_aiui.chunk_save.assert_called_once_with(
            doc_id="doc1", group="group1", chunks=chunks
        )

    @pytest.mark.asyncio
    async def test_chunks_update(self, strategy, mock_aiui):
        """Test chunk updating"""
        # Mock delete and save methods
        strategy.chunks_delete = AsyncMock()
        strategy.chunks_save = AsyncMock(return_value="update_result")

        chunks = [
            {"chunkId": "chunk1", "content": "content1"},
            {"chunkId": "chunk2", "content": "content2"},
        ]

        result = await strategy.chunks_update("doc1", "group1", "user1", chunks)

        assert result == "update_result"
        strategy.chunks_delete.assert_called_once_with(
            doc_id="doc1", chunk_ids=["chunk1", "chunk2"]
        )

        strategy.chunks_save.assert_called_once_with(
            doc_id="doc1", group="group1", uid="user1", chunks=chunks
        )

    @pytest.mark.asyncio
    async def test_chunks_delete(self, strategy, mock_aiui):
        """Test chunk deletion"""
        mock_aiui.chunk_delete = AsyncMock(return_value="delete_result")

        result = await strategy.chunks_delete("doc1", ["chunk1", "chunk2"])

        assert result == "delete_result"
        mock_aiui.chunk_delete.assert_called_once_with(
            doc_id="doc1", chunk_ids=["chunk1", "chunk2"]
        )

    @pytest.mark.asyncio
    async def test_query_doc(self, strategy, mock_aiui):
        """Test querying all chunks of a document"""
        # Mock return values
        mock_data = [
            {
                "docId": "doc1",
                "chunkId": "chunk1",
                "content": "Content with <table1> and <image1>",
                "references": {
                    "table1": {"format": "table", "content": "Table content"},
                    "image1": {
                        "format": "image",
                        "link": "http://example.com/image.jpg",
                    },
                },
            }
        ]

        mock_aiui.get_doc_content = AsyncMock(return_value=mock_data)

        result = await strategy.query_doc("doc1")

        # Verify results
        assert len(result) == 1
        assert result[0]["docId"] == "doc1"
        assert result[0]["chunkId"] == "chunk1"
        # Verify content replacement
        assert "Table content" in result[0]["content"]
        assert "" in result[0]["content"]

    @pytest.mark.asyncio
    async def test_query_doc_name(self, strategy):
        """Test querying document name"""
        result = await strategy.query_doc_name("doc1")
        assert result is None
