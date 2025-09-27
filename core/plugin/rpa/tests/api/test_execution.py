"""Test RPA execution API routes."""

from typing import AsyncGenerator
from unittest.mock import MagicMock, patch

from fastapi.testclient import TestClient


class TestExecutionAPI:
    """Test cases for RPA execution API."""

    def test_exec_endpoint_exists(self, client: TestClient) -> None:
        """Test that /rpa/v1/exec endpoint exists."""
        response = client.post(
            "/rpa/v1/exec",
            headers={"Authorization": "Bearer test_token"},
            json={"project_id": "test_project"},
        )
        # Not expecting 404 error, any other error indicates the endpoint exists
        assert response.status_code != 404

    @patch("api.v1.execution.task_monitoring")
    def test_exec_valid_request(
        self, mock_task_monitoring: MagicMock, client: TestClient
    ) -> None:
        """Test valid execution request."""

        # Mock async generator
        async def mock_generator() -> AsyncGenerator[str, None]:
            yield '{"code": 200, "message": "Success"}'

        mock_task_monitoring.return_value = mock_generator()

        request_data = {
            "project_id": "test_project_123",
            "exec_position": "EXECUTOR",
            "params": {"key": "value"},
            "sid": "test_session",
        }

        _ = client.post(
            "/rpa/v1/exec",
            headers={"Authorization": "Bearer test_token_123"},
            json=request_data,
        )

        # Verify call parameters
        mock_task_monitoring.assert_called_once()
        call_args = mock_task_monitoring.call_args
        assert call_args.kwargs["sid"] == "test_session"
        assert call_args.kwargs["access_token"] == "test_token_123"
        assert call_args.kwargs["project_id"] == "test_project_123"
        assert call_args.kwargs["exec_position"] == "EXECUTOR"
        assert call_args.kwargs["params"] == {"key": "value"}

    @patch("api.v1.execution.task_monitoring")
    def test_exec_bearer_token_parsing(
        self, mock_task_monitoring: MagicMock, client: TestClient
    ) -> None:
        """Test Bearer token parsing."""

        async def mock_generator() -> AsyncGenerator[str, None]:
            yield '{"code": 200, "message": "Success"}'

        mock_task_monitoring.return_value = mock_generator()

        request_data = {"project_id": "test_project_123"}

        # Test token with Bearer prefix
        _ = client.post(
            "/rpa/v1/exec",
            headers={"Authorization": "Bearer my_secret_token"},
            json=request_data,
        )

        call_args = mock_task_monitoring.call_args
        assert call_args.kwargs["access_token"] == "my_secret_token"

    @patch("api.v1.execution.task_monitoring")
    def test_exec_plain_token(
        self, mock_task_monitoring: MagicMock, client: TestClient
    ) -> None:
        """Test token without Bearer prefix."""

        async def mock_generator() -> AsyncGenerator[str, None]:
            yield '{"code": 200, "message": "Success"}'

        mock_task_monitoring.return_value = mock_generator()

        request_data = {"project_id": "test_project_123"}

        # Test token without Bearer prefix
        _ = client.post(
            "/rpa/v1/exec", headers={"Authorization": "plain_token"}, json=request_data
        )

        call_args = mock_task_monitoring.call_args
        assert call_args.kwargs["access_token"] == "plain_token"

    def test_exec_missing_authorization_header(self, client: TestClient) -> None:
        """Test request missing Authorization header."""
        request_data = {"project_id": "test_project_123"}

        response = client.post("/rpa/v1/exec", json=request_data)
        assert response.status_code == 422  # Validation error

    def test_exec_invalid_request_body(self, client: TestClient) -> None:
        """Test invalid request body."""
        # Missing required project_id
        response = client.post(
            "/rpa/v1/exec",
            headers={"Authorization": "Bearer test_token"},
            json={"sid": "test_session"},
        )
        assert response.status_code == 422

    @patch("api.v1.execution.task_monitoring")
    def test_exec_minimal_request(
        self, mock_task_monitoring: MagicMock, client: TestClient
    ) -> None:
        """Test minimal valid request."""

        async def mock_generator() -> AsyncGenerator[str, None]:
            yield '{"code": 200, "message": "Success"}'

        mock_task_monitoring.return_value = mock_generator()

        request_data = {"project_id": "test_project_123"}

        _ = client.post(
            "/rpa/v1/exec",
            headers={"Authorization": "Bearer test_token"},
            json=request_data,
        )

        call_args = mock_task_monitoring.call_args
        assert call_args.kwargs["project_id"] == "test_project_123"
        assert call_args.kwargs["sid"] == ""  # Default value
        assert call_args.kwargs["exec_position"] == "EXECUTOR"  # Default value
        assert call_args.kwargs["params"] is None  # Default value

    @patch("api.v1.execution.task_monitoring")
    @patch("api.v1.execution.os.getenv")
    def test_exec_response_headers_and_ping(
        self,
        mock_getenv: MagicMock,
        mock_task_monitoring: MagicMock,
        client: TestClient,
    ) -> None:
        """Test response headers and ping interval setting."""
        mock_getenv.return_value = "30"  # XIAOWU_RPA_PING_INTERVAL_KEY

        async def mock_generator() -> AsyncGenerator[str, None]:
            yield '{"code": 200, "message": "Success"}'

        mock_task_monitoring.return_value = mock_generator()

        request_data = {"project_id": "test_project_123"}

        with patch("api.v1.execution.EventSourceResponse") as mock_sse:
            _ = client.post(
                "/rpa/v1/exec",
                headers={"Authorization": "Bearer test_token"},
                json=request_data,
            )

            # Verify EventSourceResponse called correctly
            mock_sse.assert_called_once()
            call_args = mock_sse.call_args

            # Verify headers contain necessary fields
            headers = call_args.kwargs["headers"]
            assert "Content-Type" in headers
            assert headers["Content-Type"] == "text/event-stream; charset=utf-8"
            assert headers["Cache-Control"] == "no-cache, no-transform"
            assert headers["Connection"] == "keep-alive"

            # Verify ping interval
            assert call_args.kwargs["ping"] == 30

    @patch("api.v1.execution.task_monitoring")
    def test_exec_exception_handling(
        self, mock_task_monitoring: MagicMock, client: TestClient
    ) -> None:
        """Test exception handling."""
        # Mock task_monitoring raising exception
        mock_task_monitoring.side_effect = ValueError("Test error")

        request_data = {"project_id": "test_project_123"}

        response = client.post(
            "/rpa/v1/exec",
            headers={"Authorization": "Bearer test_token"},
            json=request_data,
        )

        assert response.status_code == 500
        assert "Test error" in response.json()["detail"]
