# -*- coding: utf-8 -*-
"""
API response test module.

This module contains comprehensive unit tests for API response classes,
testing BaseResponse, SuccessResponse, SuccessDataResponse, and ErrorResponse.
"""

from typing import List
from unittest.mock import MagicMock

from knowledge.consts.error_code import CodeEnum
from knowledge.domain.response import BaseResponse, ErrorResponse, SuccessDataResponse


class TestBaseResponse:
    """Test BaseResponse class."""

    def test_init_with_required_params(self) -> None:
        """Test initialization with required parameters."""
        response = BaseResponse(code=200, message="OK")
        assert response.code == 200
        assert response.message == "OK"
        assert response.sid is None

    def test_init_with_all_params(self) -> None:
        """Test initialization with all parameters."""
        response = BaseResponse(code=404, message="Not Found", sid="session123")
        assert response.code == 404
        assert response.message == "Not Found"
        assert response.sid == "session123"

    def test_init_with_none_sid(self) -> None:
        """Test initialization with explicit None sid."""
        response = BaseResponse(code=500, message="Error", sid=None)
        assert response.code == 500
        assert response.message == "Error"
        assert response.sid is None

    def test_to_dict_without_sid(self) -> None:
        """Test to_dict method without sid."""
        response = BaseResponse(code=200, message="Success")
        result = response.to_dict()

        expected = {"code": 200, "message": "Success"}
        assert result == expected
        assert "sid" not in result

    def test_to_dict_with_sid(self) -> None:
        """Test to_dict method with sid."""
        response = BaseResponse(code=201, message="Created", sid="abc123")
        result = response.to_dict()

        expected = {"code": 201, "message": "Created", "sid": "abc123"}
        assert result == expected

    def test_is_success_true(self) -> None:
        """Test is_success method returning True."""
        response = BaseResponse(code=0, message="Success")
        assert response.is_success() is True

    def test_is_success_false(self) -> None:
        """Test is_success method returning False."""
        response1 = BaseResponse(code=1, message="Error")
        response2 = BaseResponse(code=-1, message="Error")
        response3 = BaseResponse(code=404, message="Not Found")

        assert response1.is_success() is False
        assert response2.is_success() is False
        assert response3.is_success() is False

    def test_modifiable_attributes(self) -> None:
        """Test that attributes can be modified after initialization."""
        response = BaseResponse(code=200, message="Original")

        # Modify attributes
        response.code = 404
        response.message = "Modified"
        response.sid = "new_session"

        assert response.code == 404
        assert response.message == "Modified"
        assert response.sid == "new_session"

    def test_to_dict_after_modification(self) -> None:
        """Test to_dict after modifying attributes."""
        response = BaseResponse(code=200, message="Original")
        response.code = 500
        response.message = "Modified"
        response.sid = "modified_session"

        result = response.to_dict()
        expected = {"code": 500, "message": "Modified", "sid": "modified_session"}
        assert result == expected


class TestSuccessDataResponse:
    """Test SuccessDataResponse class."""

    def test_init_with_data(self) -> None:
        """Test initialization with data."""
        test_data = {"key": "value", "count": 42}
        response = SuccessDataResponse(data=test_data)

        assert response.code == 0
        assert response.message == "success"
        assert response.data == test_data
        assert response.sid is None

    def test_init_with_all_params(self) -> None:
        """Test initialization with all parameters."""
        test_data = [1, 2, 3, 4, 5]
        response = SuccessDataResponse(
            data=test_data, message="Data retrieved", sid="data_session"
        )

        assert response.code == 0
        assert response.message == "Data retrieved"
        assert response.data == test_data
        assert response.sid == "data_session"

    def test_init_with_none_data(self) -> None:
        """Test initialization with None data."""
        response = SuccessDataResponse(data=None)
        assert response.code == 0
        assert response.message == "success"
        assert response.data is None

    def test_init_with_various_data_types(self) -> None:
        """Test initialization with various data types."""
        # Test with dictionary
        dict_response = SuccessDataResponse(data={"test": "dict"})
        assert dict_response.data == {"test": "dict"}

        # Test with list
        list_response = SuccessDataResponse(data=[1, 2, 3])
        assert list_response.data == [1, 2, 3]

        # Test with string
        str_response = SuccessDataResponse(data="string data")
        assert str_response.data == "string data"

        # Test with integer
        int_response = SuccessDataResponse(data=42)
        assert int_response.data == 42

        # Test with boolean
        bool_response = SuccessDataResponse(data=True)
        assert bool_response.data is True

    def test_to_dict_includes_data(self) -> None:
        """Test that to_dict method includes data field."""
        test_data = {"result": "test", "items": [1, 2, 3]}
        response = SuccessDataResponse(
            data=test_data, message="Success with data", sid="test_sid"
        )

        result = response.to_dict()
        expected = {
            "code": 0,
            "message": "Success with data",
            "sid": "test_sid",
            "data": test_data,
        }
        assert result == expected

    def test_to_dict_with_none_data(self) -> None:
        """Test to_dict with None data."""
        response = SuccessDataResponse(data=None, message="No data")
        result = response.to_dict()

        expected = {"code": 0, "message": "No data", "data": None}
        assert result.get("code") == expected["code"]
        assert result.get("message") == expected["message"]

    def test_to_dict_without_sid(self) -> None:
        """Test to_dict without sid."""
        response = SuccessDataResponse(data={"test": "data"})
        result = response.to_dict()

        expected = {"code": 0, "message": "success", "data": {"test": "data"}}
        assert result == expected
        assert "sid" not in result

    def test_data_attribute_modifiable(self) -> None:
        """Test that data attribute can be modified."""
        response = SuccessDataResponse(data="original")
        assert response.data == "original"

        response.data = {"modified": "data"}
        assert response.data == {"modified": "data"}

        result = response.to_dict()
        assert result["data"] == {"modified": "data"}

    def test_is_success_always_true(self) -> None:
        """Test that is_success is always True for SuccessDataResponse."""
        response1 = SuccessDataResponse(data={})
        response2 = SuccessDataResponse(data=None)
        response3 = SuccessDataResponse(data="any data")

        assert response1.is_success() is True
        assert response2.is_success() is True
        assert response3.is_success() is True


class TestErrorResponse:
    """Test ErrorResponse class."""

    def test_init_with_code_enum(self) -> None:
        """Test initialization with CodeEnum."""
        # Mock CodeEnum
        mock_code_enum = MagicMock()
        mock_code_enum.code = 10001
        mock_code_enum.msg = "Parameter error"

        response = ErrorResponse(code_enum=mock_code_enum)

        assert response.code == 10001
        assert response.message == "Parameter error"
        assert response.sid is None

    def test_init_with_code_enum_and_sid(self) -> None:
        """Test initialization with CodeEnum and sid."""
        mock_code_enum = MagicMock()
        mock_code_enum.code = 10002
        mock_code_enum.msg = "Validation error"

        response = ErrorResponse(code_enum=mock_code_enum, sid="error_session")

        assert response.code == 10002
        assert response.message == "Validation error"
        assert response.sid == "error_session"

    def test_init_with_custom_message(self) -> None:
        """Test initialization with custom message override."""
        mock_code_enum = MagicMock()
        mock_code_enum.code = 10003
        mock_code_enum.msg = "Default error message"

        response = ErrorResponse(
            code_enum=mock_code_enum, message="Custom error message"
        )

        assert response.code == 10003
        assert response.message == "Custom error message"  # Should use custom message
        assert response.sid is None

    def test_init_with_all_params(self) -> None:
        """Test initialization with all parameters."""
        mock_code_enum = MagicMock()
        mock_code_enum.code = 10004
        mock_code_enum.msg = "Default message"

        response = ErrorResponse(
            code_enum=mock_code_enum,
            sid="full_test_session",
            message="Override message",
        )

        assert response.code == 10004
        assert response.message == "Override message"
        assert response.sid == "full_test_session"

    def test_init_with_none_message(self) -> None:
        """Test initialization with explicit None message."""
        mock_code_enum = MagicMock()
        mock_code_enum.code = 10005
        mock_code_enum.msg = "Enum message"

        response = ErrorResponse(code_enum=mock_code_enum, message=None)

        assert response.code == 10005
        assert response.message == "Enum message"  # Should use enum message

    def test_init_with_real_code_enum(self) -> None:
        """Test initialization with real CodeEnum values."""
        # Test with ParameterCheckException
        response1 = ErrorResponse(code_enum=CodeEnum.ParameterCheckException)
        assert response1.code == CodeEnum.ParameterCheckException.code
        assert response1.message == CodeEnum.ParameterCheckException.msg

        # Test with different CodeEnum
        response2 = ErrorResponse(code_enum=CodeEnum.ThirdPartyServiceFailed)
        assert response2.code == CodeEnum.ThirdPartyServiceFailed.code
        assert response2.message == CodeEnum.ThirdPartyServiceFailed.msg

    def test_to_dict_inheritance(self) -> None:
        """Test that to_dict method is inherited from BaseResponse."""
        mock_code_enum = MagicMock()
        mock_code_enum.code = 50001
        mock_code_enum.msg = "Server error"

        response = ErrorResponse(code_enum=mock_code_enum, sid="error_123")
        result = response.to_dict()

        expected = {"code": 50001, "message": "Server error", "sid": "error_123"}
        assert result == expected

    def test_is_success_always_false(self) -> None:
        """Test that is_success is always False for ErrorResponse."""
        mock_code_enum1 = MagicMock()
        mock_code_enum1.code = 10001
        mock_code_enum1.msg = "Error 1"

        mock_code_enum2 = MagicMock()
        mock_code_enum2.code = 50000
        mock_code_enum2.msg = "Error 2"

        response1 = ErrorResponse(code_enum=mock_code_enum1)
        response2 = ErrorResponse(code_enum=mock_code_enum2)

        assert response1.is_success() is False
        assert response2.is_success() is False


class TestResponseIntegration:
    """Test integration scenarios between response classes."""

    def test_response_type_identification(self) -> None:
        """Test identifying response types."""
        success_data_resp = SuccessDataResponse(data={"test": "data"})

        mock_error_enum = MagicMock()
        mock_error_enum.code = 10001
        mock_error_enum.msg = "Error"
        error_resp = ErrorResponse(code_enum=mock_error_enum)

        # Test isinstance checks
        assert isinstance(success_data_resp, BaseResponse)
        assert isinstance(success_data_resp, SuccessDataResponse)

        assert isinstance(error_resp, BaseResponse)
        assert isinstance(error_resp, ErrorResponse)

    def test_response_serialization_consistency(self) -> None:
        """Test that all response types serialize consistently."""
        responses: List[BaseResponse] = [
            SuccessDataResponse(data={"key": "value"}, sid="test2"),
        ]

        # Mock for ErrorResponse
        mock_code_enum = MagicMock()
        mock_code_enum.code = 10001
        mock_code_enum.msg = "Error message"
        error_response = ErrorResponse(code_enum=mock_code_enum, sid="test3")
        responses.append(error_response)

        for response in responses:
            result_dict = response.to_dict()

            # All responses should have these fields
            assert "code" in result_dict
            assert "message" in result_dict
            # Note: sid may not be present if None
            if response.sid is not None:
                assert "sid" in result_dict

            # Verify types
            assert isinstance(result_dict["code"], int)
            assert isinstance(result_dict["message"], str)
            if response.sid is not None:
                assert isinstance(result_dict["sid"], str)

    def test_success_vs_error_distinction(self) -> None:
        """Test distinguishing success from error responses."""
        success_responses = [SuccessDataResponse(data="test")]

        mock_error_enum = MagicMock()
        mock_error_enum.code = 10001
        mock_error_enum.msg = "Error"
        error_responses = [ErrorResponse(code_enum=mock_error_enum)]

        # All success responses should return True for is_success
        for response in success_responses:
            assert response.is_success() is True
            assert response.code == 0

        # All error responses should return False for is_success
        for response in error_responses:
            assert response.is_success() is False
            assert response.code != 0

    def test_response_dict_json_serializable(self) -> None:
        """Test that response dictionaries are JSON serializable."""
        import json

        responses: List[BaseResponse] = [
            SuccessDataResponse(data={"nested": {"data": [1, 2, 3]}}),
        ]

        # Mock for ErrorResponse
        mock_code_enum = MagicMock()
        mock_code_enum.code = 10001
        mock_code_enum.msg = "Test error"
        error_response = ErrorResponse(code_enum=mock_code_enum)
        responses.append(error_response)

        for response in responses:
            result_dict = response.to_dict()

            # Should be able to serialize to JSON without error
            json_str = json.dumps(result_dict)
            assert isinstance(json_str, str)

            # Should be able to deserialize back
            deserialized = json.loads(json_str)
            assert isinstance(deserialized, dict)
            assert deserialized["code"] == result_dict["code"]
            assert deserialized["message"] == result_dict["message"]
