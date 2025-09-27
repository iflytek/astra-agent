from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from knowledge.consts.error_code import CodeEnum
from knowledge.exceptions.exception import CustomException

# Target test module
from knowledge.infra.aiui import (
    chunk_delete,
    chunk_query,
    chunk_save,
    chunk_split,
    document_parse,
    get_doc_content,
)


class TestAIUIClient:
    """AIUI interface client unit tests"""

    @pytest.fixture(autouse=True)
    def setup(self, monkeypatch):
        """Set up test environment"""
        # Set environment variables
        monkeypatch.setenv("AIUI_API_KEY", "test_api_key")
        monkeypatch.setenv("AIUI_API_SECRET", "test_api_secret")
        monkeypatch.setenv("AIUI_URL_V2", "https://test-api.aiui.com")
        monkeypatch.setenv("AIUI_QUERY_REPOID_V2", "test_repo_id")
        monkeypatch.setenv("AIUI_CLIENT_TIMEOUT", "30.0")
        monkeypatch.setenv("OTLP_SPAN_SERVICE", "knowledge")

        # Mock logger to avoid log output during testing
        monkeypatch.setattr("loguru.logger.error", MagicMock())
        monkeypatch.setattr("loguru.logger.info", MagicMock())

        # Mock get_file_extension_from_url function
        monkeypatch.setattr(
            "knowledge.utils.file_utils.get_file_extension_from_url",
            MagicMock(return_value="pdf"),
        )

        # Mock check_not_empty function
        monkeypatch.setattr(
            "knowledge.utils.verification.check_not_empty", MagicMock(return_value=True)
        )

    @pytest.mark.asyncio
    async def test_chunk_query_success(self):
        """Test successful chunk query"""
        # Mock request returns successful response
        expected_response = {"results": ["result1", "result2"]}

        with patch(
            "knowledge.infra.aiui.aiui.request",
            new=AsyncMock(return_value=expected_response),
        ) as mock_request:
            result = await chunk_query(
                "test query",
                doc_ids=["doc1", "doc2"],
                repo_ids=["repo1"],
                top_k=5,
                threshold=0.5,
            )

            # Verify function call
            mock_request.assert_called_once()
            assert result == expected_response

    @pytest.mark.asyncio
    async def test_document_parse_success_pdf(self):
        """Test successful document parsing (PDF)"""
        # Mock request returns successful response
        expected_response = {"documentId": "test_doc_id", "status": "success"}
        with patch(
            "knowledge.infra.aiui.aiui.request",
            new=AsyncMock(return_value=expected_response),
        ) as mock_request:
            result = await document_parse(
                "http://example.com/test.pdf", 0  # Resource type is file
            )

            # Verify function call
            mock_request.assert_called_once()
            assert result == expected_response

    @pytest.mark.asyncio
    async def test_document_parse_success_url(self):
        """Test successful document parsing (URL)"""
        # Mock request returns successful response
        expected_response = {"documentId": "test_doc_id", "status": "success"}
        with patch(
            "knowledge.infra.aiui.aiui.request",
            new=AsyncMock(return_value=expected_response),
        ) as mock_request:
            result = await document_parse(
                "http://example.com", 1
            )  # Resource type is URL

            # Verify function call
            mock_request.assert_called_once()
            assert result == expected_response

    @pytest.mark.asyncio
    async def test_document_parse_invalid_resource_type(self):
        """Test document parsing - invalid resource type"""
        with pytest.raises(CustomException) as exc_info:
            await document_parse(
                "http://example.com/test.pdf", 2
            )  # Invalid resource type

        assert exc_info.value.code == CodeEnum.ParameterInvalid.code
        assert "Resource type" in str(exc_info.value.message)

    @pytest.mark.asyncio
    async def test_document_parse_file_type_failed(self):
        """Test document parsing - file type retrieval failed"""
        # Mock check_not_empty returns False
        with patch("knowledge.utils.verification.check_not_empty", return_value=False):
            with pytest.raises(CustomException) as exc_info:
                await document_parse("http://example.com/test", 0)

            assert exc_info.value.code == CodeEnum.ParameterInvalid.code
            assert "File type retrieval failed" in str(exc_info.value.message)

    @pytest.mark.asyncio
    async def test_chunk_split_success(self):
        """Test successful chunk splitting"""
        # Mock request returns successful response
        expected_response = {"chunks": ["chunk1", "chunk2"]}
        with patch(
            "knowledge.infra.aiui.aiui.request",
            new=AsyncMock(return_value=expected_response),
        ) as mock_request:
            document = {"content": "test content"}
            result = await chunk_split(
                document=document,
                length_range=[100, 500],
                overlap=50,
                cut_off=["。", "!"],
                separator=["\n", "\r\n"],
                title_split=True,
            )

            # Verify function call
            mock_request.assert_called_once()
            assert result == expected_response

    @pytest.mark.asyncio
    async def test_chunk_save_success(self):
        """Test successful chunk saving"""
        # Mock request returns successful response
        expected_response = {"success": True}
        with patch(
            "knowledge.infra.aiui.aiui.request",
            new=AsyncMock(return_value=expected_response),
        ) as mock_request:
            chunks = [{"id": "1", "content": "test"}]
            result = await chunk_save("test_doc_id", "test_group", chunks)

            # Verify function call
            mock_request.assert_called_once()
            assert result == expected_response

    @pytest.mark.asyncio
    async def test_chunk_delete_success(self):
        """Test successful chunk deletion"""
        # Mock request returns successful response
        expected_response = {"success": True}
        with patch(
            "knowledge.infra.aiui.aiui.request",
            new=AsyncMock(return_value=expected_response),
        ) as mock_request:
            result = await chunk_delete("test_doc_id", ["chunk1", "chunk2"])

            # Verify function call
            mock_request.assert_called_once()
            assert result == expected_response

    @pytest.mark.asyncio
    async def test_get_doc_content_success(self):
        """Test successful document content retrieval"""
        # Mock request returns successful response
        expected_response = {"content": "test content"}
        with patch(
            "knowledge.infra.aiui.aiui.request",
            new=AsyncMock(return_value=expected_response),
        ) as mock_request:
            result = await get_doc_content("test_doc_id")

            # Verify function call
            mock_request.assert_called_once()
            assert result == expected_response
