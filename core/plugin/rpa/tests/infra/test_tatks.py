"""Test RPA task management module."""

import os
from typing import Any
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
import pytest
from fastapi import HTTPException
from plugin.rpa.errors.error_code import ErrorCode
from plugin.rpa.exceptions.config_exceptions import InvalidConfigException
from plugin.rpa.infra.xiaowu.tasks import create_task, query_task_status


class TestCreateTask:
    """Test cases for create_task function."""

    @pytest.mark.asyncio
    @patch.dict(
        os.environ, {"XIAOWU_RPA_TASK_CREATE_URL": "https://api.example.com/create"}
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    async def test_create_task_success(self, mock_client_class: Any) -> None:
        """Test successful task creation."""
        # Mock HTTP client response
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        mock_response = MagicMock()
        mock_response.json.return_value = {
            "code": "0000",
            "msg": "Success",
            "data": {"executionId": "task_123456"},
        }
        mock_response.raise_for_status.return_value = None
        mock_client.post.return_value = mock_response

        # Call function
        result = await create_task(
            access_token="test_token",
            project_id="project_123",
            exec_position="EXECUTOR",
            params={"key": "value"},
        )

        # Verify result
        assert result == "task_123456"

        # Verify HTTP request parameters
        mock_client.post.assert_called_once()
        call_args = mock_client.post.call_args
        assert call_args[0][0] == "https://api.example.com/create"
        assert call_args[1]["headers"]["Authorization"] == "Bearer test_token"
        assert call_args[1]["json"]["project_id"] == "project_123"

    @pytest.mark.asyncio
    @patch.dict(os.environ, {"XIAOWU_RPA_TASK_CREATE_URL": "invalid_url"})
    async def test_create_task_invalid_url(self) -> None:
        """Test invalid task creation URL."""
        with pytest.raises(InvalidConfigException) as exc_info:
            await create_task(
                access_token="test_token",
                project_id="project_123",
                exec_position="EXECUTOR",
                params={"key": "value"},
            )

        assert "Invalid task creation URL" in str(exc_info.value)

    @pytest.mark.asyncio
    @patch.dict(os.environ, {}, clear=True)
    async def test_create_task_missing_url_env(self) -> None:
        """Test missing task creation URL environment variable."""
        with pytest.raises(InvalidConfigException) as exc_info:
            await create_task(
                access_token="test_token",
                project_id="project_123",
                exec_position="EXECUTOR",
                params={"key": "value"},
            )

        assert "Invalid task creation URL" in str(exc_info.value)

    @pytest.mark.asyncio
    @patch.dict(
        os.environ, {"XIAOWU_RPA_TASK_CREATE_URL": "https://api.example.com/create"}
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    async def test_create_task_api_error_response(self, mock_client_class: Any) -> None:
        """Test API error response."""
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        mock_response = MagicMock()
        mock_response.json.return_value = {
            "code": "5000",
            "msg": "Internal server error",
            "data": None,
        }
        mock_response.raise_for_status.return_value = None
        mock_client.post.return_value = mock_response

        with pytest.raises(HTTPException) as exc_info:
            await create_task(
                access_token="test_token",
                project_id="project_123",
                exec_position="EXECUTOR",
                params={"key": "value"},
            )

        assert exc_info.value.status_code == 500
        assert "Task creation failed" in exc_info.value.detail

    @pytest.mark.asyncio
    @patch.dict(
        os.environ, {"XIAOWU_RPA_TASK_CREATE_URL": "https://api.example.com/create"}
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    async def test_create_task_missing_execution_id(
        self, mock_client_class: Any
    ) -> None:
        """Test missing executionId in response."""
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        mock_response = MagicMock()
        mock_response.json.return_value = {
            "code": "0000",
            "msg": "Success",
            "data": {"other_field": "value"},  # Missing executionId
        }
        mock_response.raise_for_status.return_value = None
        mock_client.post.return_value = mock_response

        with pytest.raises(HTTPException) as exc_info:
            await create_task(
                access_token="test_token",
                project_id="project_123",
                exec_position="EXECUTOR",
                params={"key": "value"},
            )

        assert exc_info.value.status_code == 500
        assert "Task ID not returned" in exc_info.value.detail

    @pytest.mark.asyncio
    @patch.dict(
        os.environ, {"XIAOWU_RPA_TASK_CREATE_URL": "https://api.example.com/create"}
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    async def test_create_task_http_status_error(self, mock_client_class: Any) -> None:
        """Test HTTP status error."""
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        # Mock HTTP status error
        mock_response = MagicMock()
        mock_response.status_code = 400
        mock_response.text = "Bad Request"

        http_error = httpx.HTTPStatusError(
            "400", request=MagicMock(), response=mock_response
        )
        mock_client.post.side_effect = http_error

        with pytest.raises(HTTPException) as exc_info:
            await create_task(
                access_token="test_token",
                project_id="project_123",
                exec_position="EXECUTOR",
                params={"key": "value"},
            )

        assert exc_info.value.status_code == 400

    @pytest.mark.asyncio
    @patch.dict(
        os.environ, {"XIAOWU_RPA_TASK_CREATE_URL": "https://api.example.com/create"}
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    async def test_create_task_request_error(self, mock_client_class: Any) -> None:
        """Test network request error."""
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        # Mock network request error
        mock_client.post.side_effect = httpx.RequestError("Network error")

        with pytest.raises(HTTPException) as exc_info:
            await create_task(
                access_token="test_token",
                project_id="project_123",
                exec_position="EXECUTOR",
                params={"key": "value"},
            )

        assert exc_info.value.status_code == 500
        assert "Network error" in exc_info.value.detail


class TestQueryTaskStatus:
    """Test cases for query_task_status function."""

    @pytest.mark.asyncio
    @patch.dict(
        os.environ, {"XIAOWU_RPA_TASK_QUERY_URL": "https://api.example.com/query"}
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    async def test_query_task_status_completed(self, mock_client_class: Any) -> None:
        """Test querying completed task status."""
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        mock_response = MagicMock()
        mock_response.json.return_value = {
            "code": "0000",
            "msg": "Success",
            "data": {
                "execution": {
                    "status": "COMPLETED",
                    "result": {"data": {"output": "task completed successfully"}},
                }
            },
        }
        mock_response.raise_for_status.return_value = None
        mock_client.get.return_value = mock_response

        result = await query_task_status("test_token", "task_123")

        assert result is not None
        code, message, data = result
        assert code == ErrorCode.SUCCESS.code
        assert message == ErrorCode.SUCCESS.message
        assert data == {"output": "task completed successfully"}

    @pytest.mark.asyncio
    @patch.dict(
        os.environ, {"XIAOWU_RPA_TASK_QUERY_URL": "https://api.example.com/query"}
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    async def test_query_task_status_failed(self, mock_client_class: Any) -> None:
        """Test querying failed task status."""
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        mock_response = MagicMock()
        mock_response.json.return_value = {
            "code": "0000",
            "msg": "Success",
            "data": {
                "execution": {"status": "FAILED", "error": "Task execution failed"}
            },
        }
        mock_response.raise_for_status.return_value = None
        mock_client.get.return_value = mock_response

        result = await query_task_status("test_token", "task_123")

        assert result is not None
        code, message, data = result
        assert code == ErrorCode.QUERY_TASK_ERROR.code
        assert "Task execution failed" in message
        assert data == {}

    @pytest.mark.asyncio
    @patch.dict(
        os.environ, {"XIAOWU_RPA_TASK_QUERY_URL": "https://api.example.com/query"}
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    async def test_query_task_status_pending(self, mock_client_class: Any) -> None:
        """Test querying pending task status."""
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        mock_response = MagicMock()
        mock_response.json.return_value = {
            "code": "0000",
            "msg": "Success",
            "data": {"execution": {"status": "PENDING"}},
        }
        mock_response.raise_for_status.return_value = None
        mock_client.get.return_value = mock_response

        result = await query_task_status("test_token", "task_123")

        assert result is None

    @pytest.mark.asyncio
    @patch.dict(os.environ, {"XIAOWU_RPA_TASK_QUERY_URL": "invalid_url"})
    async def test_query_task_status_invalid_url(self) -> None:
        """Test invalid task query URL."""
        with pytest.raises(InvalidConfigException) as exc_info:
            await query_task_status("test_token", "task_123")

        assert "Invalid task query URL" in str(exc_info.value)

    @pytest.mark.asyncio
    @patch.dict(
        os.environ, {"XIAOWU_RPA_TASK_QUERY_URL": "https://api.example.com/query"}
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    async def test_query_task_status_api_error(self, mock_client_class: Any) -> None:
        """Test API error response."""
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        mock_response = MagicMock()
        mock_response.json.return_value = {
            "code": "5000",
            "msg": "Internal server error",
            "data": None,
        }
        mock_response.raise_for_status.return_value = None
        mock_client.get.return_value = mock_response

        with pytest.raises(HTTPException) as exc_info:
            await query_task_status("test_token", "task_123")

        assert exc_info.value.status_code == 500
        assert "Query task status failed" in exc_info.value.detail

    @pytest.mark.asyncio
    @patch.dict(
        os.environ, {"XIAOWU_RPA_TASK_QUERY_URL": "https://api.example.com/query"}
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    async def test_query_task_status_unknown_status(
        self, mock_client_class: Any
    ) -> None:
        """Test unknown task status."""
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        mock_response = MagicMock()
        mock_response.json.return_value = {
            "code": "0000",
            "msg": "Success",
            "data": {"execution": {"status": "UNKNOWN_STATUS"}},
        }
        mock_response.raise_for_status.return_value = None
        mock_client.get.return_value = mock_response

        with pytest.raises(HTTPException) as exc_info:
            await query_task_status("test_token", "task_123")

        assert exc_info.value.status_code == 500
        assert "Unknown task status" in exc_info.value.detail

    @pytest.mark.asyncio
    @patch.dict(
        os.environ, {"XIAOWU_RPA_TASK_QUERY_URL": "https://api.example.com/query"}
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    async def test_query_task_status_missing_execution(
        self, mock_client_class: Any
    ) -> None:
        """Test missing execution info in response."""
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        mock_response = MagicMock()
        mock_response.json.return_value = {
            "code": "0000",
            "msg": "Success",
            "data": {},  # Missing execution field
        }
        mock_response.raise_for_status.return_value = None
        mock_client.get.return_value = mock_response

        with pytest.raises(HTTPException) as exc_info:
            await query_task_status("test_token", "task_123")

        assert exc_info.value.status_code == 500
        assert "Task info not returned" in exc_info.value.detail

    @pytest.mark.asyncio
    @patch.dict(
        os.environ, {"XIAOWU_RPA_TASK_QUERY_URL": "https://api.example.com/query"}
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    async def test_query_task_status_request_error(
        self, mock_client_class: Any
    ) -> None:
        """Test network request error."""
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        # Mock network request error
        mock_client.get.side_effect = httpx.RequestError("Network error")

        with pytest.raises(HTTPException) as exc_info:
            await query_task_status("test_token", "task_123")

        assert exc_info.value.status_code == 500
        assert "Network error" in exc_info.value.detail

    @pytest.mark.asyncio
    @patch.dict(
        os.environ, {"XIAOWU_RPA_TASK_QUERY_URL": "https://api.example.com/query"}
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    async def test_query_task_status_url_construction(
        self, mock_client_class: Any
    ) -> None:
        """Test query URL construction."""
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        mock_response = MagicMock()
        mock_response.json.return_value = {
            "code": "0000",
            "msg": "Success",
            "data": {"execution": {"status": "PENDING"}},
        }
        mock_response.raise_for_status.return_value = None
        mock_client.get.return_value = mock_response

        await query_task_status("test_token", "task_123")

        # Verify request URL and headers
        mock_client.get.assert_called_once()
        call_args = mock_client.get.call_args
        assert call_args[1]["url"] == "https://api.example.com/query/task_123"
        assert call_args[1]["headers"]["Authorization"] == "Bearer test_token"
