import asyncio
from unittest.mock import AsyncMock, MagicMock, patch

import aiohttp
import pytest

from knowledge.consts.error_code import CodeEnum
from knowledge.exceptions.exception import CustomException, ThirdPartyException

# Target test module
from knowledge.infra.xinghuo import (
    assemble_spark_auth_headers_async,
    async_form_request,
    async_request,
    dataset_addchunk,
    dataset_delchunk,
    dataset_updchunk,
    get_chunks,
    get_file_info,
    get_file_status,
    new_topk_search,
    split,
    upload,
)


class TestXinghuoRag:
    """Spark knowledge base interface unit tests"""

    @pytest.fixture(autouse=True)
    def setup(self, monkeypatch):
        """Set up test environment"""
        # Set environment variables
        monkeypatch.setenv("XINGHUO_RAG_URL", "https://test-api.xinghuo.com/")
        monkeypatch.setenv("XINGHUO_APP_ID", "test_app_id")
        monkeypatch.setenv("XINGHUO_APP_SECRET", "test_app_secret")
        monkeypatch.setenv("XINGHUO_DATASET_ID", "test_dataset_id")
        monkeypatch.setenv("XINGHUO_SEARCH_OVERLAP", "test_overlap")
        monkeypatch.setenv("XINGHUO_CLIENT_TIMEOUT", "60.0")

        # Mock logger to avoid log output during testing
        monkeypatch.setattr("loguru.logger.error", MagicMock())
        monkeypatch.setattr("loguru.logger.info", MagicMock())

        # Mock get_signature function
        monkeypatch.setattr(
            "knowledge.utils.spark_signature.get_signature",
            MagicMock(return_value="test_signature"),
        )

        # Mock get_file_info_from_url function
        monkeypatch.setattr(
            "knowledge.utils.file_utils.get_file_info_from_url",
            MagicMock(return_value=("test_file", "test_path", "txt")),
        )

    @pytest.mark.asyncio
    async def test_upload_success(self):
        """Test successful file upload"""
        # Mock async_form_request returns successful response
        with patch(
            "knowledge.infra.xinghuo.xinghuo.async_form_request",
            new=AsyncMock(return_value={"fileId": "test_file_id"}),
        ) as mock_request:
            result = await upload("http://example.com/test.txt", {"test": "extend"}, 1)

            # Verify function call
            mock_request.assert_called_once()
            assert result == {"fileId": "test_file_id"}

    @pytest.mark.asyncio
    async def test_upload_failure(self):
        """Test file upload failure"""
        # Mock async_form_request throws exception
        with patch(
            "knowledge.infra.xinghuo.xinghuo.async_form_request",
            new=AsyncMock(side_effect=ThirdPartyException("Upload failed")),
        ):
            with pytest.raises(ThirdPartyException, match="Upload failed"):
                await upload("http://example.com/test.txt", {"test": "extend"}, 1)

    @pytest.mark.asyncio
    async def test_split_success(self):
        """Test successful document splitting"""
        # Mock async_request returns successful response
        with patch(
            "knowledge.infra.xinghuo.xinghuo.async_request",
            new=AsyncMock(return_value={"status": "success"}),
        ) as mock_request:
            result = await split("test_file_id", ["。", "!"], [256, 2000])

            # Verify function call
            mock_request.assert_called_once()
            assert result == {"status": "success"}

    @pytest.mark.asyncio
    async def test_split_retry_success(self):
        """Test document splitting succeeds after retries"""
        # Mock async_request fails first two times, succeeds third time
        mock_request = AsyncMock(
            side_effect=[
                Exception("First failure"),
                Exception("Second failure"),
                {"status": "success"},
            ]
        )

        with patch("knowledge.infra.xinghuo.xinghuo.async_request", mock_request):
            with patch(
                "asyncio.sleep", new=AsyncMock()
            ):  # Mock sleep to avoid actual waiting
                result = await split("test_file_id", ["。", "!"], [256, 2000])

                # Verify function was called 3 times
                assert mock_request.call_count == 3
                assert result == {"status": "success"}

    @pytest.mark.asyncio
    async def test_split_failure_after_retries(self):
        """Test document splitting still fails after retries"""
        # Mock async_request always fails
        mock_request = AsyncMock(side_effect=Exception("Always failing"))

        with patch("knowledge.infra.xinghuo.xinghuo.async_request", mock_request):
            with patch(
                "asyncio.sleep", new=AsyncMock()
            ):  # Mock sleep to avoid actual waiting
                with pytest.raises(
                    ThirdPartyException, match="Document splitting failed after retries"
                ):
                    await split("test_file_id", ["。", "!"], [256, 2000])

                # Verify function was called 3 times
                assert mock_request.call_count == 3

    @pytest.mark.asyncio
    async def test_get_chunks_success(self):
        """Test successful document chunk retrieval"""
        # Mock get_file_status returns success status
        with patch(
            "knowledge.infra.xinghuo.xinghuo.get_file_status",
            new=AsyncMock(return_value=[{"fileStatus": "success"}]),
        ):
            # Mock async_request returns chunk data
            with patch(
                "knowledge.infra.xinghuo.xinghuo.async_request",
                new=AsyncMock(return_value=[{"chunkId": "1", "content": "test"}]),
            ):
                result = await get_chunks("test_file_id")

                assert result == [{"chunkId": "1", "content": "test"}]

    @pytest.mark.asyncio
    async def test_get_chunks_file_failed(self):
        """Test chunk retrieval when file processing failed"""
        # Mock get_file_status returns failure status
        with patch(
            "knowledge.infra.xinghuo.xinghuo.get_file_status",
            new=AsyncMock(return_value=[{"fileStatus": "failed"}]),
        ):
            with pytest.raises(ThirdPartyException, match="Document splitting failed"):
                await get_chunks("test_file_id")

    @pytest.mark.asyncio
    async def test_get_chunks_retry_success(self):
        """Test chunk retrieval succeeds after retries"""
        # Mock get_file_status returns processing status first two times, success third time
        mock_status = AsyncMock(
            side_effect=[
                [{"fileStatus": "spliting"}],
                [{"fileStatus": "spliting"}],
                [{"fileStatus": "success"}],
            ]
        )

        # Mock async_request returns chunk data
        mock_request = AsyncMock(return_value=[{"chunkId": "1", "content": "test"}])

        with patch("knowledge.infra.xinghuo.xinghuo.get_file_status", mock_status):
            with patch("knowledge.infra.xinghuo.xinghuo.async_request", mock_request):
                with patch(
                    "asyncio.sleep", new=AsyncMock()
                ):  # Mock sleep to avoid actual waiting
                    result = await get_chunks("test_file_id")

                    # Verify status check was called 3 times
                    assert mock_status.call_count == 3
                    assert result == [{"chunkId": "1", "content": "test"}]

    @pytest.mark.asyncio
    async def test_get_chunks_failure_after_retries(self):
        """Test chunk retrieval still fails after retries"""
        # Mock get_file_status always returns processing status
        mock_status = AsyncMock(return_value=[{"fileStatus": "spliting"}])

        # Mock async_request returns empty data
        mock_request = AsyncMock(return_value=None)

        with patch("knowledge.infra.xinghuo.xinghuo.get_file_status", mock_status):
            with patch("knowledge.infra.xinghuo.xinghuo.async_request", mock_request):
                with patch(
                    "asyncio.sleep", new=AsyncMock()
                ):  # Mock sleep to avoid actual waiting
                    with pytest.raises(CustomException) as exc_info:
                        await get_chunks("test_file_id")

                    # Verify status check was called 70 times
                    assert mock_status.call_count == 70
                    assert exc_info.value.code == CodeEnum.GetFileContentFailed.code

    @pytest.mark.asyncio
    async def test_new_topk_search_success(self):
        """Test successful new hybrid search"""
        # Mock async_request returns search results
        with patch(
            "knowledge.infra.xinghuo.xinghuo.async_request",
            new=AsyncMock(return_value={"results": ["result1", "result2"]}),
        ) as mock_request:
            result = await new_topk_search("test query", ["doc1", "doc2"], 5)

            # Verify function call
            mock_request.assert_called_once()
            assert result == {"results": ["result1", "result2"]}

    @pytest.mark.asyncio
    async def test_get_file_status_success(self):
        """Test successful file status retrieval"""
        # Mock async_form_request returns file status
        with patch(
            "knowledge.infra.xinghuo.xinghuo.async_form_request",
            new=AsyncMock(
                return_value=[{"fileId": "test_file_id", "fileStatus": "success"}]
            ),
        ) as mock_request:
            result = await get_file_status("test_file_id")

            # Verify function call
            mock_request.assert_called_once()
            assert result == [{"fileId": "test_file_id", "fileStatus": "success"}]

    @pytest.mark.asyncio
    async def test_get_file_info_success(self):
        """Test successful file info retrieval"""
        # Mock async_form_request returns file info
        with patch(
            "knowledge.infra.xinghuo.xinghuo.async_form_request",
            new=AsyncMock(
                return_value={"fileId": "test_file_id", "fileName": "test.txt"}
            ),
        ) as mock_request:
            result = await get_file_info("test_file_id")

            # Verify function call
            mock_request.assert_called_once()
            assert result == {"fileId": "test_file_id", "fileName": "test.txt"}

    @pytest.mark.asyncio
    async def test_dataset_addchunk_success(self):
        """Test successful chunk addition to dataset"""
        # Mock async_request returns addition result
        with patch(
            "knowledge.infra.xinghuo.xinghuo.async_request",
            new=AsyncMock(return_value={"success": True}),
        ) as mock_request:
            chunks = [{"chunkId": "1", "content": "test"}]
            result = await dataset_addchunk(chunks)

            # Verify function call
            mock_request.assert_called_once()
            assert result == {"success": True}

    @pytest.mark.asyncio
    async def test_dataset_delchunk_success(self):
        """Test successful chunk deletion from dataset"""
        # Mock async_form_request returns deletion result
        with patch(
            "knowledge.infra.xinghuo.xinghuo.async_form_request",
            new=AsyncMock(return_value={"success": True}),
        ) as mock_request:
            result = await dataset_delchunk(["chunk1", "chunk2"])

            # Verify function call
            mock_request.assert_called_once()
            assert result == {"success": True}

    @pytest.mark.asyncio
    async def test_dataset_updchunk_success(self):
        """Test successful chunk update in dataset"""
        # Mock async_request returns update result
        with patch(
            "knowledge.infra.xinghuo.xinghuo.async_request",
            new=AsyncMock(return_value={"success": True}),
        ) as mock_request:
            chunk = {
                "chunkId": "1",
                "content": "updated content",
                "question": "test question",
                "answer": "test answer",
            }
            result = await dataset_updchunk(chunk)

            # Verify function call
            mock_request.assert_called_once()
            assert result == {"success": True}

    @pytest.mark.asyncio
    async def test_async_request_success(self):
        """Test successful async request"""
        # Mock aiohttp response
        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.text.return_value = (
            '{"code": 0, "flag": true, "data": {"result": "success"}}'
        )
        mock_response.json.return_value = {
            "code": 0,
            "flag": True,
            "data": {"result": "success"},
        }

        # Mock session
        with patch("aiohttp.ClientSession.request") as mock_session:
            mock_session.return_value.__aenter__.return_value = mock_response
            with patch(
                "knowledge.infra.xinghuo.assemble_spark_auth_headers_async",
                new=AsyncMock(return_value={"appId": "test"}),
            ):
                result = await async_request(
                    {"test": "data"}, "https://test-api.xinghuo.com/test", "POST"
                )

                assert result == {"result": "success"}

    @pytest.mark.asyncio
    async def test_async_request_api_error(self):
        """Test async request API error"""
        # Mock aiohttp response
        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.text.return_value = (
            '{"code": 1, "flag": false, "desc": "API error"}'
        )
        mock_response.json.return_value = {
            "code": 1,
            "flag": False,
            "desc": "API error",
        }

        # Mock session
        with patch("aiohttp.ClientSession.request") as mock_session:
            mock_session.return_value.__aenter__.return_value = mock_response
            with patch(
                "knowledge.infra.xinghuo.assemble_spark_auth_headers_async",
                new=AsyncMock(return_value={"appId": "test"}),
            ):
                with pytest.raises(ThirdPartyException, match="API error"):
                    await async_request(
                        {"test": "data"}, "https://test-api.xinghuo.com/test", "POST"
                    )

    @pytest.mark.asyncio
    async def test_async_request_network_error(self):
        """Test async request network error"""
        with patch("aiohttp.ClientSession.request") as mock_session:
            mock_session.return_value.__aenter__.side_effect = aiohttp.ClientError(
                "Network error"
            )
            with patch(
                "knowledge.infra.xinghuo.assemble_spark_auth_headers_async",
                new=AsyncMock(return_value={"appId": "test"}),
            ):
                with pytest.raises(ThirdPartyException, match="CBG Network error"):
                    await async_request(
                        {"test": "data"}, "https://test-api.xinghuo.com/test", "POST"
                    )

    @pytest.mark.asyncio
    async def test_async_request_timeout_error(self):
        """Test async request timeout error"""
        with patch("aiohttp.ClientSession.request") as mock_session:
            mock_session.return_value.__aenter__.side_effect = asyncio.TimeoutError()
            with patch(
                "knowledge.infra.xinghuo.assemble_spark_auth_headers_async",
                new=AsyncMock(return_value={"appId": "test"}),
            ):
                with pytest.raises(ThirdPartyException, match="CBG Request timeout"):
                    await async_request(
                        {"test": "data"}, "https://test-api.xinghuo.com/test", "POST"
                    )

    @pytest.mark.asyncio
    async def test_async_form_request_success(self):
        """Test successful async form request"""
        # Mock aiohttp response
        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.text.return_value = (
            '{"code": 0, "flag": true, "data": {"result": "success"}}'
        )
        mock_response.json.return_value = {
            "code": 0,
            "flag": True,
            "data": {"result": "success"},
        }

        # Mock session
        with patch("aiohttp.ClientSession.request") as mock_session:
            mock_session.return_value.__aenter__.return_value = mock_response
            with patch(
                "knowledge.infra.xinghuo.assemble_spark_auth_headers_async",
                new=AsyncMock(return_value={"appId": "test"}),
            ):
                result = await async_form_request(
                    {"test": "data"}, "https://test-api.xinghuo.com/test", "POST"
                )

                assert result == {"result": "success"}

    @pytest.mark.asyncio
    async def test_assemble_spark_auth_headers_async(self):
        """Test auth header assembly"""
        with patch("time.time", return_value=1234567890):
            result = await assemble_spark_auth_headers_async()

            expected = {
                "Accept": "application/json",
                "appId": "test_app_id",
                "timestamp": "1234567890",
                "signature": "RYxr79RDVtvwNIWuTfyJzYdsvjU=",
            }

            assert result.get("signature") == expected.get("signature")


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
