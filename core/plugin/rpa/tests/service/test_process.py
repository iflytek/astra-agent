"""Test task processing module."""

import os
from unittest.mock import MagicMock, patch

import httpx
import pytest
from fastapi import HTTPException
from plugin.rpa.api.schemas.execution_schema import RPAExecutionResponse
from plugin.rpa.errors.error_code import ErrorCode
from plugin.rpa.exceptions.config_exceptions import InvalidConfigException
from plugin.rpa.service.xiaowu.process import task_monitoring


class TestTaskMonitoring:
    """Test cases for task_monitoring function."""

    @pytest.mark.asyncio
    @patch("service.xiaowu.process.create_task")
    @patch("service.xiaowu.process.query_task_status")
    @patch.dict(
        os.environ,
        {"XIAOWU_RPA_TIMEOUT_KEY": "300", "XIAOWU_RPA_TASK_QUERY_INTERVAL_KEY": "1"},
    )
    async def test_task_monitoring_success(
        self, mock_query_task: MagicMock, mock_create_task: MagicMock
    ) -> None:
        """Test successful task monitoring completion."""
        # Mock successful task creation
        mock_create_task.return_value = "test_task_id_123"

        # Mock query task status returns success
        mock_query_task.return_value = (
            ErrorCode.SUCCESS.code,
            "Task completed successfully",
            {"result": "finished", "output": "test_output"},
        )

        # Collect generator output
        results = []
        async for result in task_monitoring(
            sid="test_sid",
            access_token="test_token",
            project_id="test_project",
            exec_position="EXECUTOR",
            params={"key": "value"},
        ):
            results.append(result)

        # Verify result
        assert len(results) == 1
        response = RPAExecutionResponse.model_validate_json(results[0])
        assert response.code == ErrorCode.SUCCESS.code
        assert response.message == "Task completed successfully"
        assert response.sid == "test_sid"
        assert response.data == {"result": "finished", "output": "test_output"}

        # Verify calls
        mock_create_task.assert_called_once_with(
            access_token="test_token",
            project_id="test_project",
            exec_position="EXECUTOR",
            params={"key": "value"},
        )
        mock_query_task.assert_called_with("test_token", "test_task_id_123")

    @pytest.mark.asyncio
    @patch("service.xiaowu.process.create_task")
    @patch.dict(
        os.environ,
        {"XIAOWU_RPA_TIMEOUT_KEY": "300", "XIAOWU_RPA_TASK_QUERY_INTERVAL_KEY": "1"},
    )
    async def test_task_monitoring_create_task_error(
        self, mock_create_task: MagicMock
    ) -> None:
        """Test task monitoring when task creation fails."""
        # Mock task creation failure
        mock_create_task.side_effect = HTTPException(
            status_code=400, detail="Bad request"
        )

        # Collect generator output
        results = []
        async for result in task_monitoring(
            sid="test_sid",
            access_token="test_token",
            project_id="test_project",
            exec_position="EXECUTOR",
            params={"key": "value"},
        ):
            results.append(result)

        # Verify result
        assert len(results) == 1
        response = RPAExecutionResponse.model_validate_json(results[0])
        assert response.code == ErrorCode.CREATE_TASK_ERROR.code
        assert ErrorCode.CREATE_TASK_ERROR.message in response.message
        assert response.sid == "test_sid"

    @pytest.mark.asyncio
    @patch("service.xiaowu.process.create_task")
    @patch.dict(
        os.environ,
        {"XIAOWU_RPA_TIMEOUT_KEY": "300", "XIAOWU_RPA_TASK_QUERY_INTERVAL_KEY": "1"},
    )
    async def test_task_monitoring_invalid_config_error(
        self, mock_create_task: MagicMock
    ) -> None:
        """Test invalid configuration error case."""
        # Mock configuration error
        mock_create_task.side_effect = InvalidConfigException("Invalid configuration")

        # Collect generator output
        results = []
        async for result in task_monitoring(
            sid="test_sid",
            access_token="test_token",
            project_id="test_project",
            exec_position="EXECUTOR",
            params={"key": "value"},
        ):
            results.append(result)

        # Verify result
        assert len(results) == 1
        response = RPAExecutionResponse.model_validate_json(results[0])
        assert response.code == ErrorCode.CREATE_TASK_ERROR.code
        assert "Invalid configuration" in response.message

    @pytest.mark.asyncio
    @patch("service.xiaowu.process.create_task")
    @patch("service.xiaowu.process.query_task_status")
    @patch.dict(
        os.environ,
        {"XIAOWU_RPA_TIMEOUT_KEY": "2", "XIAOWU_RPA_TASK_QUERY_INTERVAL_KEY": "1"},
    )
    async def test_task_monitoring_timeout(
        self, mock_query_task: MagicMock, mock_create_task: MagicMock
    ) -> None:
        """Test task monitoring timeout case."""
        # Mock successful task creation
        mock_create_task.return_value = "test_task_id_123"

        # Mock query task status always returns None (not completed)
        mock_query_task.return_value = None

        # Collect generator output
        results = []
        async for result in task_monitoring(
            sid="test_sid",
            access_token="test_token",
            project_id="test_project",
            exec_position="EXECUTOR",
            params={"key": "value"},
        ):
            results.append(result)

        # Verify result
        assert len(results) == 1
        response = RPAExecutionResponse.model_validate_json(results[0])
        assert response.code == ErrorCode.TIMEOUT_ERROR.code
        assert response.message == ErrorCode.TIMEOUT_ERROR.message
        assert response.sid == "test_sid"

    @pytest.mark.asyncio
    @patch("service.xiaowu.process.create_task")
    @patch("service.xiaowu.process.query_task_status")
    @patch.dict(
        os.environ,
        {"XIAOWU_RPA_TIMEOUT_KEY": "300", "XIAOWU_RPA_TASK_QUERY_INTERVAL_KEY": "1"},
    )
    async def test_task_monitoring_query_error(
        self, mock_query_task: MagicMock, mock_create_task: MagicMock
    ) -> None:
        """Test task monitoring when query task status fails."""
        # Mock successful task creation
        mock_create_task.return_value = "test_task_id_123"

        # Mock query task status returns error
        mock_query_task.return_value = (
            ErrorCode.QUERY_TASK_ERROR.code,
            "Query task failed",
            None,
        )

        # Collect generator output
        results = []
        async for result in task_monitoring(
            sid="test_sid",
            access_token="test_token",
            project_id="test_project",
            exec_position="EXECUTOR",
            params={"key": "value"},
        ):
            results.append(result)

        # Verify result
        assert len(results) == 1
        response = RPAExecutionResponse.model_validate_json(results[0])
        assert response.code == ErrorCode.QUERY_TASK_ERROR.code
        assert response.message == "Query task failed"
        assert response.sid == "test_sid"

    @pytest.mark.asyncio
    @patch("service.xiaowu.process.create_task")
    @patch.dict(
        os.environ,
        {"XIAOWU_RPA_TIMEOUT_KEY": "300", "XIAOWU_RPA_TASK_QUERY_INTERVAL_KEY": "1"},
    )
    async def test_task_monitoring_httpx_error(
        self, mock_create_task: MagicMock
    ) -> None:
        """Test httpx related error case."""
        # Mock httpx error
        mock_create_task.side_effect = httpx.HTTPStatusError(
            "Bad request", request=MagicMock(), response=MagicMock()
        )

        # Collect generator output
        results = []
        async for result in task_monitoring(
            sid="test_sid",
            access_token="test_token",
            project_id="test_project",
            exec_position="EXECUTOR",
            params={"key": "value"},
        ):
            results.append(result)

        # Verify result
        assert len(results) == 1
        response = RPAExecutionResponse.model_validate_json(results[0])
        assert response.code == ErrorCode.CREATE_TASK_ERROR.code

    @pytest.mark.asyncio
    @patch("service.xiaowu.process.create_task")
    @patch.dict(
        os.environ,
        {"XIAOWU_RPA_TIMEOUT_KEY": "300", "XIAOWU_RPA_TASK_QUERY_INTERVAL_KEY": "1"},
    )
    async def test_task_monitoring_request_error(
        self, mock_create_task: MagicMock
    ) -> None:
        """Test network request error case."""
        # Mock network request error
        mock_create_task.side_effect = httpx.RequestError("Network error")

        # Collect generator output
        results = []
        async for result in task_monitoring(
            sid="test_sid",
            access_token="test_token",
            project_id="test_project",
            exec_position="EXECUTOR",
            params={"key": "value"},
        ):
            results.append(result)

        # Verify result
        assert len(results) == 1
        response = RPAExecutionResponse.model_validate_json(results[0])
        assert response.code == ErrorCode.CREATE_TASK_ERROR.code

    @pytest.mark.asyncio
    @patch("service.xiaowu.process.create_task")
    @patch("service.xiaowu.process.query_task_status")
    @patch.dict(
        os.environ,
        {"XIAOWU_RPA_TIMEOUT_KEY": "300", "XIAOWU_RPA_TASK_QUERY_INTERVAL_KEY": "1"},
    )
    async def test_task_monitoring_with_none_params(
        self, mock_query_task: MagicMock, mock_create_task: MagicMock
    ) -> None:
        """Test case where params is None."""
        # Mock successful task creation
        mock_create_task.return_value = "test_task_id_123"

        # Mock query task status returns success
        mock_query_task.return_value = (
            ErrorCode.SUCCESS.code,
            "Task completed successfully",
            {"result": "finished"},
        )

        # Collect generator output
        results = []
        async for result in task_monitoring(
            sid=None,  # sid can also be None
            access_token="test_token",
            project_id="test_project",
            exec_position=None,
            params=None,
        ):
            results.append(result)

        # Verify result
        assert len(results) == 1
        response = RPAExecutionResponse.model_validate_json(results[0])
        assert response.code == ErrorCode.SUCCESS.code
        assert response.sid is None or response.sid == ""

        # Verify call parameters
        mock_create_task.assert_called_once_with(
            access_token="test_token",
            project_id="test_project",
            exec_position=None,
            params=None,
        )
