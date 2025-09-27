import asyncio
import json
from unittest.mock import AsyncMock, MagicMock, patch

import aiohttp
import pytest

from knowledge.consts.error_code import CodeEnum
from knowledge.exceptions.exception import ThirdPartyException

# Target test module
from knowledge.infra.desk import (
    assemble_auth_headers_async,
    async_request,
    sparkdesk_query_async,
)


class TestSparkDeskClient:
    """SparkDesk knowledge base query module unit tests"""

    @pytest.fixture(autouse=True)
    def setup(self, monkeypatch):
        """Set up test environment"""
        # Set environment variables
        monkeypatch.setenv("DESK_RAG_URL", "https://test-api.sparkdesk.com/v1/query")
        monkeypatch.setenv("DESK_APP_ID", "test_app_id")
        monkeypatch.setenv("DESK_API_SECRET", "test_api_secret")
        monkeypatch.setenv("DESK_CLIENT_TIMEOUT", "30")

        # Mock logger to avoid log output during testing
        monkeypatch.setattr("loguru.logger.error", MagicMock())
        monkeypatch.setattr("loguru.logger.info", MagicMock())

        # Mock get_signature function
        monkeypatch.setattr(
            "knowledge.utils.spark_signature.get_signature",
            MagicMock(return_value="test_signature"),
        )

        # Mock time.time returns fixed timestamp
        monkeypatch.setattr("time.time", MagicMock(return_value=1672574400))

    @pytest.mark.asyncio
    async def test_assemble_auth_headers_async(self):
        """Test auth header assembly"""
        result = await assemble_auth_headers_async()

        expected_headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "Method": "POST",
            "appId": "test_app_id",
            "timestamp": "1672574400",
            "signature": "FtLoBlqplIoqR+1O7yq+p+LeI1Y=",
        }

        assert result == expected_headers

    @pytest.mark.asyncio
    async def test_sparkdesk_query_async_with_repo_ids(self):
        """Test knowledge base query (with knowledge base ID)"""
        # Mock async_request returns successful response
        expected_response = {"results": ["result1", "result2"]}
        with patch(
            "knowledge.infra.desk.sparkdesk.async_request",
            new=AsyncMock(return_value=expected_response),
        ) as mock_request:
            result = await sparkdesk_query_async(
                "test query", repo_ids=["repo1"], flow_id="test_flow_id"
            )

            # Verify function call
            mock_request.assert_called_once()
            call_args = mock_request.call_args[1]
            assert call_args["body"] == {
                "question": "test query",
                "datasetId": "repo1",
                "flowId": "test_flow_id",
            }
            assert result == expected_response

    @pytest.mark.asyncio
    async def test_sparkdesk_query_async_without_repo_ids(self):
        """Test knowledge base query (without knowledge base ID)"""
        # Mock async_request returns successful response
        expected_response = {"results": ["result1", "result2"]}
        with patch(
            "knowledge.infra.desk.sparkdesk.async_request",
            new=AsyncMock(return_value=expected_response),
        ) as mock_request:
            result = await sparkdesk_query_async("test query")

            # Verify function call
            mock_request.assert_called_once()
            call_args = mock_request.call_args[1]
            assert call_args["body"] == {
                "question": "test query",
                "datasetId": None,
                "flowId": None,
            }
            assert result == expected_response

    @pytest.mark.asyncio
    async def test_sparkdesk_query_async_with_empty_repo_ids(self):
        """Test knowledge base query (empty knowledge base ID list)"""
        # Mock async_request returns successful response
        expected_response = {"results": ["result1", "result2"]}
        with patch(
            "knowledge.infra.desk.sparkdesk.async_request",
            new=AsyncMock(return_value=expected_response),
        ) as mock_request:
            result = await sparkdesk_query_async("test query", repo_ids=[])

            # Verify function call
            mock_request.assert_called_once()
            call_args = mock_request.call_args[1]
            assert call_args["body"] == {
                "question": "test query",
                "datasetId": None,
                "flowId": None,
            }
            assert result == expected_response

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

        # Mock span object
        mock_span = MagicMock()
        mock_span_context = MagicMock()
        mock_span.__enter__ = MagicMock(return_value=mock_span_context)
        mock_span.__exit__ = MagicMock(return_value=None)

        # Mock session
        with patch("aiohttp.ClientSession.request") as mock_session:
            mock_session.return_value.__aenter__.return_value = mock_response
            with patch(
                "knowledge.infra.desk.sparkdesk.assemble_auth_headers_async",
                new=AsyncMock(return_value={"appId": "test"}),
            ):
                result = await async_request({"test": "data"}, "POST", span=mock_span)

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

        # Mock span object
        mock_span = MagicMock()
        mock_span_context = MagicMock()
        mock_span.__enter__ = MagicMock(return_value=mock_span_context)
        mock_span.__exit__ = MagicMock(return_value=None)

        with pytest.raises(Exception) as exc_info:
            # Mock session
            with patch("aiohttp.ClientSession.request") as mock_session:
                mock_session.return_value.__aenter__.return_value = mock_response
                with patch(
                    "knowledge.infra.desk.sparkdesk.assemble_auth_headers_async",
                    new=AsyncMock(return_value={"appId": "test"}),
                ):
                    with pytest.raises(ThirdPartyException, match="API error"):
                        await async_request({"test": "data"}, "POST", span=mock_span)
            assert exc_info.value.code == CodeEnum.DESK_RAGError.code

    @pytest.mark.asyncio
    async def test_async_request_http_error(self):
        """Test async request HTTP error"""
        # Mock aiohttp response
        mock_response = AsyncMock()
        mock_response.status = 500
        mock_response.text.return_value = "Internal Server Error"

        # Mock span object
        mock_span = MagicMock()
        mock_span_context = MagicMock()
        mock_span.__enter__ = MagicMock(return_value=mock_span_context)
        mock_span.__exit__ = MagicMock(return_value=None)
        with pytest.raises(Exception) as exc_info:
            # Mock session
            with patch("aiohttp.ClientSession.request") as mock_session:
                mock_session.return_value.__aenter__.return_value = mock_response
                with patch(
                    "knowledge.infra..desk.sparkdesk.assemble_auth_headers_async",
                    new=AsyncMock(return_value={"appId": "test"}),
                ):
                    with pytest.raises(
                        ThirdPartyException,
                        match="SPARKDESK-RAG request failed with status: 500",
                    ):
                        await async_request({"test": "data"}, "POST", span=mock_span)

                    assert exc_info.value.code == CodeEnum.DESK_RAGError.code

    @pytest.mark.asyncio
    async def test_async_request_json_decode_error(self):
        """Test async request JSON parsing error"""
        # Mock aiohttp response
        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.text.return_value = "Invalid JSON"
        mock_response.json.side_effect = json.JSONDecodeError(
            "Expecting value", "Invalid JSON", 0
        )

        # Mock span object
        mock_span = MagicMock()
        mock_span_context = MagicMock()
        mock_span.__enter__ = MagicMock(return_value=mock_span_context)
        mock_span.__exit__ = MagicMock(return_value=None)
        with pytest.raises(Exception) as exc_info:
            # Mock session
            with patch("aiohttp.ClientSession.request") as mock_session:
                mock_session.return_value.__aenter__.return_value = mock_response
                with patch(
                    "knowledge.infra..desk.sparkdesk.assemble_auth_headers_async",
                    new=AsyncMock(return_value={"appId": "test"}),
                ):
                    with pytest.raises(
                        ThirdPartyException, match="Failed to parse JSON response"
                    ):
                        await async_request({"test": "data"}, "POST", span=mock_span)

                    assert exc_info.value.code == CodeEnum.DESK_RAGError.code

    @pytest.mark.asyncio
    async def test_async_request_timeout_error(self):
        """Test async request timeout error"""
        # Mock span object
        mock_span = MagicMock()
        mock_span_context = MagicMock()
        mock_span.__enter__ = MagicMock(return_value=mock_span_context)
        mock_span.__exit__ = MagicMock(return_value=None)
        with pytest.raises(Exception) as exc_info:
            # Mock session
            with patch("aiohttp.ClientSession.request") as mock_session:
                mock_session.return_value.__aenter__.side_effect = (
                    asyncio.TimeoutError()
                )
                with patch(
                    "knowledge.infra..desk.sparkdesk.assemble_auth_headers_async",
                    new=AsyncMock(return_value={"appId": "test"}),
                ):
                    with pytest.raises(
                        ThirdPartyException,
                        match="Async request to https://test-api.sparkdesk.com/v1/query timed out after 30 seconds",
                    ):
                        await async_request({"test": "data"}, "POST", span=mock_span)

                    assert exc_info.value.code == CodeEnum.DESK_RAGError.code

    @pytest.mark.asyncio
    async def test_async_request_network_error(self):
        """Test async request network error"""
        # Mock span object
        mock_span = MagicMock()
        mock_span_context = MagicMock()
        mock_span.__enter__ = MagicMock(return_value=mock_span_context)
        mock_span.__exit__ = MagicMock(return_value=None)
        with pytest.raises(Exception) as exc_info:
            # Mock session
            with patch("aiohttp.ClientSession.request") as mock_session:
                mock_session.return_value.__aenter__.side_effect = aiohttp.ClientError(
                    "Network error"
                )
                with patch(
                    "knowledge.infra..desk.sparkdesk.assemble_auth_headers_async",
                    new=AsyncMock(return_value={"appId": "test"}),
                ):
                    with pytest.raises(
                        ThirdPartyException, match="Network error during async request"
                    ):
                        await async_request({"test": "data"}, "POST", span=mock_span)

                assert exc_info.value.code == CodeEnum.DESK_RAGError.code

    @pytest.mark.asyncio
    async def test_async_request_without_span(self):
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
        # Mock span object
        mock_span = MagicMock()
        mock_span_context = MagicMock()
        mock_span.__enter__ = MagicMock(return_value=mock_span_context)
        mock_span.__exit__ = MagicMock(return_value=None)

        # Mock session
        with patch("aiohttp.ClientSession.request") as mock_session:
            mock_session.return_value.__aenter__.return_value = mock_response
            with patch(
                "knowledge.infra.desk.sparkdesk.assemble_auth_headers_async",
                new=AsyncMock(return_value={"appId": "test"}),
            ):
                result = await async_request({"test": "data"}, "POST", span=mock_span)

                assert result == {"result": "success"}


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
