"""Test API schemas (DTO) module."""

import pytest
from plugin.rpa.api.schemas.execution_schema import (
    RPAExecutionRequest,
    RPAExecutionResponse,
)
from pydantic import ValidationError


class TestRPAExecutionRequest:
    """Test cases for RPAExecutionRequest DTO."""

    def test_valid_request_with_all_fields(self) -> None:
        """Test valid request with all fields."""
        request_data = {
            "sid": "test_session_123",
            "project_id": "test_project_456",
            "exec_position": "EXECUTOR",
            "params": {"key1": "value1", "key2": "value2"},
        }
        request = RPAExecutionRequest(**request_data)  # type: ignore[arg-type]

        assert request.sid == "test_session_123"
        assert request.project_id == "test_project_456"
        assert request.exec_position == "EXECUTOR"
        assert request.params == {"key1": "value1", "key2": "value2"}

    def test_valid_request_minimal_fields(self) -> None:
        """Test valid request with only required fields."""
        request_data = {"project_id": "test_project_456"}
        request = RPAExecutionRequest(**request_data)  # type: ignore[arg-type]

        assert request.sid == ""
        assert request.project_id == "test_project_456"
        assert request.exec_position == "EXECUTOR"
        assert request.params is None

    def test_invalid_request_missing_project_id(self) -> None:
        """Test invalid request missing required field project_id."""
        request_data = {"sid": "test_session_123"}

        with pytest.raises(ValidationError) as exc_info:
            RPAExecutionRequest(**request_data)  # type: ignore[arg-type]

        assert "project_id" in str(exc_info.value)

    def test_request_with_empty_params(self) -> None:
        """Test request with empty params dictionary."""
        request_data = {"project_id": "test_project_456", "params": {}}
        request = RPAExecutionRequest(**request_data)  # type: ignore[arg-type]
        assert not request.params

    def test_request_with_none_params(self) -> None:
        """Test request with None params."""
        request_data = {"project_id": "test_project_456", "params": None}
        request = RPAExecutionRequest(**request_data)  # type: ignore[arg-type]
        assert request.params is None


class TestRPAExecutionResponse:
    """Test cases for RPAExecutionResponse DTO."""

    def test_valid_response_with_all_fields(self) -> None:
        """Test valid response with all fields."""
        response_data = {
            "code": 200,
            "message": "Success",
            "sid": "test_session_123",
            "data": {"result": "completed", "task_id": "task_456"},
        }
        response = RPAExecutionResponse(**response_data)  # type: ignore[arg-type]

        assert response.code == 200
        assert response.message == "Success"
        assert response.sid == "test_session_123"
        assert response.data == {"result": "completed", "task_id": "task_456"}

    def test_valid_response_minimal_fields(self) -> None:
        """Test valid response with only required fields."""
        response_data = {"code": 500, "message": "Internal Server Error"}
        response = RPAExecutionResponse(**response_data)  # type: ignore[arg-type]

        assert response.code == 500
        assert response.message == "Internal Server Error"
        assert response.sid == ""
        assert response.data is None

    def test_invalid_response_missing_code(self) -> None:
        """Test invalid response missing required field code."""
        response_data = {"message": "Success"}

        with pytest.raises(ValidationError) as exc_info:
            RPAExecutionResponse(**response_data)  # type: ignore[arg-type]

        assert "code" in str(exc_info.value)

    def test_invalid_response_missing_message(self) -> None:
        """Test invalid response missing required field message."""
        response_data = {"code": 200}

        with pytest.raises(ValidationError) as exc_info:
            RPAExecutionResponse(**response_data)  # type: ignore[arg-type]

        assert "message" in str(exc_info.value)

    def test_response_model_dump_json(self) -> None:
        """Test response object conversion to JSON string."""
        response_data = {
            "code": 200,
            "message": "Success",
            "sid": "test_session_123",
            "data": {"result": "completed"},
        }
        response = RPAExecutionResponse(**response_data)  # type: ignore[arg-type]
        json_str = response.model_dump_json()

        assert isinstance(json_str, str)
        assert "200" in json_str
        assert "Success" in json_str
        assert "test_session_123" in json_str
