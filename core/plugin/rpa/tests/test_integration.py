"""Integration test module.

Test integration and end-to-end functionality between various modules.
"""

import os
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
import pytest
from fastapi.testclient import TestClient
from plugin.rpa.api.app import rpa_server_app


class TestRPAIntegration:
    """RPA system integration tests."""

    @pytest.fixture
    def integration_client(self) -> TestClient:
        """Create integration test client."""
        app = rpa_server_app()
        return TestClient(app)

    @pytest.mark.integration
    @patch.dict(
        os.environ,
        {
            "XIAOWU_RPA_TASK_CREATE_URL": "https://api.example.com/create",
            "XIAOWU_RPA_TASK_QUERY_URL": "https://api.example.com/query",
            "XIAOWU_RPA_TIMEOUT": "300",
            "XIAOWU_RPA_TASK_QUERY_INTERVAL": "1",
        },
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    def test_complete_rpa_execution_flow_success(
        self, mock_client_class: MagicMock, integration_client: TestClient
    ) -> None:
        """Test complete RPA execution flow - success scenario."""
        # Mock HTTP client
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        # Mock create task response
        create_response = MagicMock()
        create_response.json.return_value = {
            "code": "0000",
            "msg": "Success",
            "data": {"executionId": "integration_task_123"},
        }
        create_response.raise_for_status.return_value = None

        # Mock query task response - completed status
        query_response = MagicMock()
        query_response.json.return_value = {
            "code": "0000",
            "msg": "Success",
            "data": {
                "execution": {
                    "status": "COMPLETED",
                    "result": {
                        "data": {
                            "output": "Integration test completed successfully",
                            "execution_time": "30.5s",
                        }
                    },
                }
            },
        }
        query_response.raise_for_status.return_value = None

        # Set mock response sequence
        mock_client.post.return_value = create_response
        mock_client.get.return_value = query_response

        # Execute integration test request
        request_data = {
            "project_id": "integration_project_123",
            "exec_position": "EXECUTOR",
            "params": {"test_param": "integration_value", "mode": "test"},
            "sid": "integration_session_456",
        }

        response = integration_client.post(
            "/rpa/v1/exec",
            headers={"Authorization": "Bearer integration_test_token"},
            json=request_data,
        )

        # Verify response
        assert response.status_code == 200
        assert "text/event-stream" in response.headers.get("content-type", "")

        # Verify create task call
        mock_client.post.assert_called_once()
        create_call = mock_client.post.call_args
        assert "https://api.example.com/create" in create_call[0]
        assert (
            create_call[1]["headers"]["Authorization"]
            == "Bearer integration_test_token"
        )

        request_body = create_call[1]["json"]
        assert request_body["project_id"] == "integration_project_123"
        assert request_body["exec_position"] == "EXECUTOR"
        assert request_body["params"]["test_param"] == "integration_value"

        # Verify query task call
        mock_client.get.assert_called()
        query_call = mock_client.get.call_args
        assert (
            query_call[1]["url"] == "https://api.example.com/query/integration_task_123"
        )

    @pytest.mark.integration
    @patch.dict(
        os.environ,
        {
            "XIAOWU_RPA_TASK_CREATE_URL": "https://api.example.com/create",
            "XIAOWU_RPA_TASK_QUERY_URL": "https://api.example.com/query",
            "XIAOWU_RPA_TIMEOUT": "2",
            "XIAOWU_RPA_TASK_QUERY_INTERVAL": "1",
        },
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    def test_complete_rpa_execution_flow_timeout(
        self, mock_client_class: MagicMock, integration_client: TestClient
    ) -> None:
        """Test complete RPA execution flow - timeout scenario."""
        # Mock HTTP client
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        # Mock create task response
        create_response = MagicMock()
        create_response.json.return_value = {
            "code": "0000",
            "msg": "Success",
            "data": {"executionId": "timeout_task_123"},
        }
        create_response.raise_for_status.return_value = None

        # Mock query task response - always pending status
        query_response = MagicMock()
        query_response.json.return_value = {
            "code": "0000",
            "msg": "Success",
            "data": {"execution": {"status": "PENDING"}},
        }
        query_response.raise_for_status.return_value = None

        mock_client.post.return_value = create_response
        mock_client.get.return_value = query_response

        # Execute request
        request_data = {
            "project_id": "timeout_project_123",
            "sid": "timeout_session_456",
        }

        response = integration_client.post(
            "/rpa/v1/exec",
            headers={"Authorization": "Bearer timeout_test_token"},
            json=request_data,
        )

        # Verify response (timeout should return event stream)
        assert response.status_code == 200

    @pytest.mark.integration
    @patch.dict(
        os.environ,
        {
            "XIAOWU_RPA_TASK_CREATE_URL": "invalid_url",
        },
    )
    def test_integration_invalid_configuration(
        self, integration_client: TestClient
    ) -> None:
        """Test invalid configuration in integration scenario."""
        request_data = {
            "project_id": "config_error_project",
            "sid": "config_error_session",
        }

        response = integration_client.post(
            "/rpa/v1/exec",
            headers={"Authorization": "Bearer config_error_token"},
            json=request_data,
        )

        # Should return event stream, but content contains configuration error
        assert response.status_code == 200

    @pytest.mark.integration
    @patch.dict(
        os.environ,
        {
            "XIAOWU_RPA_TASK_CREATE_URL": "https://api.example.com/create",
            "XIAOWU_RPA_TASK_QUERY_URL": "https://api.example.com/query",
            "XIAOWU_RPA_TIMEOUT": "300",
            "XIAOWU_RPA_TASK_QUERY_INTERVAL": "1",
        },
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    def test_integration_task_failure_flow(
        self, mock_client_class: MagicMock, integration_client: TestClient
    ) -> None:
        """Test task failure integration flow."""
        # Mock HTTP client
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        # Mock create task response
        create_response = MagicMock()
        create_response.json.return_value = {
            "code": "0000",
            "msg": "Success",
            "data": {"executionId": "failure_task_123"},
        }
        create_response.raise_for_status.return_value = None

        # Mock query task response - failed status
        query_response = MagicMock()
        query_response.json.return_value = {
            "code": "0000",
            "msg": "Success",
            "data": {
                "execution": {
                    "status": "FAILED",
                    "error": "Task execution failed due to network error",
                }
            },
        }
        query_response.raise_for_status.return_value = None

        mock_client.post.return_value = create_response
        mock_client.get.return_value = query_response

        # Execute request
        request_data = {
            "project_id": "failure_project_123",
            "sid": "failure_session_456",
        }

        response = integration_client.post(
            "/rpa/v1/exec",
            headers={"Authorization": "Bearer failure_test_token"},
            json=request_data,
        )

        # Verify response
        assert response.status_code == 200

    @pytest.mark.integration
    def test_integration_invalid_request_format(
        self, integration_client: TestClient
    ) -> None:
        """Test invalid request format in integration scenario."""
        # Missing required project_id
        invalid_request = {"exec_position": "EXECUTOR", "params": {"key": "value"}}

        response = integration_client.post(
            "/rpa/v1/exec",
            headers={"Authorization": "Bearer test_token"},
            json=invalid_request,
        )

        # Should return validation error
        assert response.status_code == 422

    @pytest.mark.integration
    def test_integration_missing_authorization_header(
        self, integration_client: TestClient
    ) -> None:
        """Test missing authorization header in integration scenario."""
        request_data = {"project_id": "unauthorized_project"}

        response = integration_client.post(
            "/rpa/v1/exec",
            json=request_data,
            # Missing Authorization header
        )

        # Should return validation error
        assert response.status_code == 422

    @pytest.mark.integration
    @patch.dict(
        os.environ,
        {
            "XIAOWU_RPA_TASK_CREATE_URL": "https://api.example.com/create",
            "XIAOWU_RPA_TIMEOUT": "300",
            "XIAOWU_RPA_TASK_QUERY_INTERVAL": "1",
        },
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    def test_integration_network_error_handling(
        self, mock_client_class: MagicMock, integration_client: TestClient
    ) -> None:
        """Test network error handling in integration scenario."""
        # Mock HTTP client
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        # Mock network error
        mock_client.post.side_effect = httpx.RequestError("Network connection failed")

        request_data = {
            "project_id": "network_error_project",
            "sid": "network_error_session",
        }

        response = integration_client.post(
            "/rpa/v1/exec",
            headers={"Authorization": "Bearer network_error_token"},
            json=request_data,
        )

        # Should return event stream, content contains network error
        assert response.status_code == 200

    @pytest.mark.integration
    def test_integration_app_creation(self) -> None:
        """Test application creation integration."""
        app = rpa_server_app()

        # Verify application creation success
        assert app is not None

        # Verify route registration
        routes = app.routes
        assert any(
            "/rpa/v1/exec" in getattr(route, "path", str(route)) for route in routes
        )

    @pytest.mark.integration
    @patch.dict(
        os.environ,
        {
            "XIAOWU_RPA_TASK_CREATE_URL": "https://api.example.com/create",
            "XIAOWU_RPA_TASK_QUERY_URL": "https://api.example.com/query",
            "XIAOWU_RPA_TIMEOUT": "300",
            "XIAOWU_RPA_TASK_QUERY_INTERVAL": "1",
            "XIAOWU_RPA_PING_INTERVAL": "30",
        },
    )
    @patch("infra.xiaowu.tatks.httpx.AsyncClient")
    def test_integration_response_headers_and_streaming(
        self, mock_client_class: MagicMock, integration_client: TestClient
    ) -> None:
        """Test response headers and streaming integration."""
        # Mock HTTP client
        mock_client = AsyncMock()
        mock_client_class.return_value.__aenter__.return_value = mock_client

        # Mock create task response
        create_response = MagicMock()
        create_response.json.return_value = {
            "code": "0000",
            "msg": "Success",
            "data": {"executionId": "stream_task_123"},
        }
        create_response.raise_for_status.return_value = None
        mock_client.post.return_value = create_response

        # Mock query task response
        query_response = MagicMock()
        query_response.json.return_value = {
            "code": "0000",
            "msg": "Success",
            "data": {
                "execution": {
                    "status": "COMPLETED",
                    "result": {"data": {"result": "streaming test"}},
                }
            },
        }
        query_response.raise_for_status.return_value = None
        mock_client.get.return_value = query_response

        request_data = {"project_id": "streaming_project", "sid": "streaming_session"}

        response = integration_client.post(
            "/rpa/v1/exec",
            headers={"Authorization": "Bearer streaming_token"},
            json=request_data,
        )

        # Verify streaming response headers
        assert response.status_code == 200
        assert "text/event-stream" in response.headers.get("content-type", "")

    @pytest.mark.integration
    def test_integration_bearer_token_formats(
        self, integration_client: TestClient
    ) -> None:
        """Test different Bearer token formats integration."""
        request_data = {"project_id": "token_test_project"}

        # Test standard Bearer format
        response1 = integration_client.post(
            "/rpa/v1/exec",
            headers={"Authorization": "Bearer standard_token"},
            json=request_data,
        )
        assert response1.status_code == 200

        # Test format without Bearer prefix
        response2 = integration_client.post(
            "/rpa/v1/exec", headers={"Authorization": "plain_token"}, json=request_data
        )
        assert response2.status_code == 200
