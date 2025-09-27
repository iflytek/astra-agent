from unittest.mock import AsyncMock, patch

import pytest

from knowledge.exceptions.exception import ProtocolParamException
from knowledge.service import CBGRAGStrategy


class TestCBGRAGStrategy:
    """CBG RAG strategy unit tests"""

    @pytest.fixture
    def strategy(self):
        """Create test strategy instance"""
        return CBGRAGStrategy()

    @pytest.fixture
    def mock_xinghuo(self):
        """Mock xinghuo module"""
        with patch("knowledge.service.impl.cbg_strategy.xinghuo") as mock:
            yield mock

    @pytest.mark.asyncio
    async def test_query_success(self, strategy, mock_xinghuo):
        """Test successful query scenario"""
        # Mock return values
        mock_results = [
            {
                "score": 0.95,
                "fileName": "test.pdf",
                "chunk": {"fileId": "doc1", "id": "chunk1", "content": "Test content"},
                "overlap": [
                    {"dataIndex": 0, "content": "Previous content"},
                    {"dataIndex": 2, "content": "Next content"},
                ],
            }
        ]

        mock_xinghuo.new_topk_search = AsyncMock(return_value=mock_results)

        # Execute query
        result = await strategy.query(
            query="test query", doc_ids=["doc1", "doc2"], top_k=5, threshold=0.8
        )

        # Verify results
        assert result["query"] == "test query"
        assert result["count"] == 1
        assert result["results"][0]["docId"] == "doc1"
        assert result["results"][0]["chunkId"] == "chunk1"
        assert result["results"][0]["fileName"] == "test.pdf"

        # Verify call parameters
        mock_xinghuo.new_topk_search.assert_called_once_with(
            query="test query", doc_ids=["doc1", "doc2"], top_n=5
        )

    @pytest.mark.asyncio
    async def test_query_empty_doc_ids(self, strategy):
        """Test query when document ID list is empty"""
        with pytest.raises(ProtocolParamException):
            await strategy.query("test query", doc_ids=[])

    @pytest.mark.asyncio
    async def test_query_below_threshold(self, strategy, mock_xinghuo):
        """Test results below score threshold"""
        # Mock return values
        mock_results = [
            {
                "score": 0.5,  # Below threshold
                "fileName": "test.pdf",
                "chunk": {"fileId": "doc1", "id": "chunk1", "content": "Test content"},
            }
        ]

        mock_xinghuo.new_topk_search = AsyncMock(return_value=mock_results)

        # Execute query, set threshold to 0.8
        result = await strategy.query("test query", doc_ids=["doc1"], threshold=0.8)

        # Verify results: should filter out low score results
        assert result["count"] == 0
        assert result["results"] == []

    @pytest.mark.asyncio
    async def test_query_string_results(self, strategy, mock_xinghuo):
        """Test string result return scenario"""
        # Mock string return value (needs JSON parsing)
        mock_results = [
            '{"score": 0.95, "fileName": "test.pdf", "chunk": {"fileId": "doc1", "id": "chunk1", "content": "Test content"}}'
        ]

        mock_xinghuo.new_topk_search = AsyncMock(return_value=mock_results)

        # Execute query
        result = await strategy.query("test query", doc_ids=["doc1"])

        # Verify results
        assert result["count"] == 1
        assert result["results"][0]["docId"] == "doc1"

    @pytest.mark.asyncio
    async def test_query_invalid_json_results(self, strategy, mock_xinghuo):
        """Test invalid JSON string return scenario"""
        # Mock invalid JSON string
        mock_results = ["invalid json string"]

        mock_xinghuo.new_topk_search = AsyncMock(return_value=mock_results)

        # Execute query
        result = await strategy.query("test query", doc_ids=["doc1"])

        # Verify results: should skip invalid JSON
        assert result["count"] == 0
        assert result["results"] == []

    @pytest.mark.asyncio
    async def test_split_success(self, strategy, mock_xinghuo):
        """Test successful file splitting scenario"""
        # Mock upload return value
        mock_upload_response = {"fileId": "doc1"}
        mock_xinghuo.upload = AsyncMock(return_value=mock_upload_response)

        # Mock chunk retrieval return value
        mock_chunks = [
            {
                "dataIndex": "1",
                "content": "Chunk content 1",
                "imgReference": {"img1": "value1"},
            }
        ]
        mock_xinghuo.get_chunks = AsyncMock(return_value=mock_chunks)

        # Execute splitting
        result = await strategy.split(
            file="test file content",
            lengthRange=[256, 2000],
            overlap=16,
            resourceType=1,
            separator=["。", "！"],
            titleSplit=True,
            cutOff=["---"],
        )

        # Verify results
        assert len(result) == 1
        assert result[0]["docId"] == "doc1"
        assert result[0]["dataIndex"] == "1"
        assert result[0]["content"] == "Chunk content 1"

        # Verify call parameters
        mock_xinghuo.upload.assert_called_once()
        mock_xinghuo.get_chunks.assert_called_once_with(file_id="doc1")

    @pytest.mark.asyncio
    async def test_split_default_separator(self, strategy, mock_xinghuo):
        """Test file splitting with default separator"""
        # Mock upload return value
        mock_upload_response = {"fileId": "doc1"}
        mock_xinghuo.upload = AsyncMock(return_value=mock_upload_response)

        # Mock chunk retrieval return value
        mock_chunks = []
        mock_xinghuo.get_chunks = AsyncMock(return_value=mock_chunks)

        # Execute splitting, without providing separator
        result = await strategy.split(
            file="test file content",
            lengthRange=[],
            overlap=0,
            resourceType=1,
            separator=[],
            titleSplit=False,
            cutOff=[],
        )

        # Verify results
        assert result == []

        # Print all call arguments for debugging
        print(f"Call args: {mock_xinghuo.upload.call_args}")

        # Check all parameters
        call_args = mock_xinghuo.upload.call_args
        if call_args:
            # Check positional parameters
            print(f"Positional args: {call_args[0]}")
            # Check keyword parameters
            print(f"Keyword args: {call_args[1]}")

        # Adjust assertions based on actual situation
        # 如果 wiki_split_extends 是位置参数
        if call_args and len(call_args[0]) > 1:
            wiki_split_extends = call_args[0][
                1
            ]  # Assume it is the second positional parameter
            assert wiki_split_extends["chunkSeparators"] == ["DQo="]
        # If wiki_split_extends is a keyword parameter
        elif call_args and "wiki_split_extends" in call_args[1]:
            assert call_args[1]["wiki_split_extends"]["chunkSeparators"] == ["DQo="]
        else:
            # If parameter name is different, look for possible parameter names
            for key, value in call_args[1].items():
                if "chunkSeparators" in str(value):
                    assert value["chunkSeparators"] == ["DQo="]
                    break
            else:
                pytest.fail("wiki_split_extends parameter not found in upload call")

    @pytest.mark.asyncio
    async def test_chunks_save(self, strategy, mock_xinghuo):
        """Test chunk saving"""
        mock_xinghuo.dataset_addchunk = AsyncMock(return_value="save_result")

        chunks = [
            {
                "content": "chunk1 content",
                "dataIndex": "1",
                "references": {"img1": "value1"},
            }
        ]

        result = await strategy.chunks_save("doc1", "group1", "user1", chunks)

        assert result == "save_result"

        # Verify call parameters
        expected_chunks = [
            {
                "fileId": "doc1",
                "chunkType": "RAW",
                "content": "chunk1 content",
                "dataIndex": "1",
                "imgReference": {"img1": "value1"},
            }
        ]
        mock_xinghuo.dataset_addchunk.assert_called_once_with(chunks=expected_chunks)

    @pytest.mark.asyncio
    async def test_chunks_update(self, strategy, mock_xinghuo):
        """Test chunk updating"""
        mock_xinghuo.dataset_updchunk = AsyncMock(return_value="update_result")

        chunks = [{"chunkId": "chunk1", "content": "updated content", "dataIndex": "1"}]

        result = await strategy.chunks_update("doc1", "group1", "user1", chunks)

        assert result == "update_result"
        mock_xinghuo.dataset_updchunk.assert_called_once_with(chunks[0])

    @pytest.mark.asyncio
    async def test_chunks_delete(self, strategy, mock_xinghuo):
        """Test chunk deletion"""
        mock_xinghuo.dataset_delchunk = AsyncMock(return_value="delete_result")

        result = await strategy.chunks_delete("doc1", ["chunk1", "chunk2"])

        assert result == "delete_result"
        mock_xinghuo.dataset_delchunk.assert_called_once_with(
            chunk_ids=["chunk1", "chunk2"]
        )

    @pytest.mark.asyncio
    async def test_chunks_delete_empty_ids(self, strategy):
        """Test chunk deletion when ID list is empty"""
        with pytest.raises(ProtocolParamException):
            await strategy.chunks_delete("doc1", [])

    @pytest.mark.asyncio
    async def test_query_doc(self, strategy, mock_xinghuo):
        """Test querying all chunks of a document"""
        # Mock return values
        mock_chunks = [
            {
                "dataIndex": "1",
                "content": "Content with {img1} reference",
                "imgReference": {"img1": "value1"},
            }
        ]

        mock_xinghuo.get_chunks = AsyncMock(return_value=mock_chunks)

        result = await strategy.query_doc("doc1")

        # Verify results
        assert len(result) == 1
        assert result[0]["docId"] == "doc1"
        assert result[0]["chunkId"] == "1"
        # Verify content references have been removed
        assert "{img1}" not in result[0]["content"]

    @pytest.mark.asyncio
    async def test_query_doc_name(self, strategy, mock_xinghuo):
        """Test querying document name"""
        # Mock return values
        mock_file_info = {
            "fileId": "doc1",
            "fileName": "test_file.pdf",
            "fileStatus": "processed",
            "quantity": 10,
        }

        mock_xinghuo.get_file_info = AsyncMock(return_value=mock_file_info)

        result = await strategy.query_doc_name("doc1")

        # Verify results
        assert result["docId"] == "doc1"
        assert result["fileName"] == "test_file.pdf"  # Decoded file name
        assert result["fileStatus"] == "processed"
        assert result["fileQuantity"] == 10
