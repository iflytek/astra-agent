# -*- coding: utf-8 -*-
"""
Custom exception test module.

This module contains comprehensive unit tests for all custom exception classes,
testing initialization, inheritance, string representation, and error handling scenarios.
"""

from unittest.mock import MagicMock

import pytest

from knowledge.consts.error_code import CodeEnum
from knowledge.exceptions.exception import (
    BaseCustomException,
    CustomException,
    ProtocolParamException,
    ServiceException,
    ThirdPartyException,
)


class TestBaseCustomException:
    """Test BaseCustomException class."""

    def test_init_with_code_enum_only(self) -> None:
        """Test initialization with CodeEnum only."""
        mock_enum = MagicMock()
        mock_enum.code = 10001
        mock_enum.msg = "Test error message"

        exception = BaseCustomException(code_enum=mock_enum)

        assert exception.code == 10001
        assert exception.message == "Test error message"
        assert str(exception.args[0]) == "Test error message"

    def test_init_with_detail_msg(self) -> None:
        """Test initialization with detail message."""
        mock_enum = MagicMock()
        mock_enum.code = 10002
        mock_enum.msg = "Base message"

        exception = BaseCustomException(
            code_enum=mock_enum, detail_msg="Additional details"
        )

        assert exception.code == 10002
        assert exception.message == "Base message(Additional details)"
        assert str(exception.args[0]) == "Base message(Additional details)"

    def test_init_with_empty_detail_msg(self) -> None:
        """Test initialization with empty detail message."""
        mock_enum = MagicMock()
        mock_enum.code = 10003
        mock_enum.msg = "Base message"

        exception = BaseCustomException(code_enum=mock_enum, detail_msg="")

        assert exception.code == 10003
        assert (
            exception.message == "Base message"
        )  # Empty detail_msg should not be included

    def test_init_with_none_detail_msg(self) -> None:
        """Test initialization with None detail message."""
        mock_enum = MagicMock()
        mock_enum.code = 10004
        mock_enum.msg = "Base message"

        exception = BaseCustomException(code_enum=mock_enum, detail_msg=None)

        assert exception.code == 10004
        assert exception.message == "Base message"

    def test_str_representation(self) -> None:
        """Test string representation of exception."""
        mock_enum = MagicMock()
        mock_enum.code = 40404
        mock_enum.msg = "Resource not found"

        exception = BaseCustomException(code_enum=mock_enum, detail_msg="Item ID: 123")

        str_repr = str(exception)
        assert str_repr == "40404: Resource not found(Item ID: 123)"

    def test_str_representation_without_detail(self) -> None:
        """Test string representation without detail message."""
        mock_enum = MagicMock()
        mock_enum.code = 50001
        mock_enum.msg = "Internal server error"

        exception = BaseCustomException(code_enum=mock_enum)

        str_repr = str(exception)
        assert str_repr == "50001: Internal server error"

    def test_get_response(self) -> None:
        """Test get_response method."""
        mock_enum = MagicMock()
        mock_enum.code = 42000
        mock_enum.msg = "Custom error"

        exception = BaseCustomException(code_enum=mock_enum, detail_msg="Extra info")

        response = exception.get_response()
        expected = {"code": 42000, "message": "Custom error(Extra info)"}
        assert response == expected

    def test_get_response_without_detail(self) -> None:
        """Test get_response method without detail message."""
        mock_enum = MagicMock()
        mock_enum.code = 30001
        mock_enum.msg = "Simple error"

        exception = BaseCustomException(code_enum=mock_enum)

        response = exception.get_response()
        expected = {"code": 30001, "message": "Simple error"}
        assert response == expected

    def test_inheritance_from_exception(self) -> None:
        """Test that BaseCustomException properly inherits from Exception."""
        mock_enum = MagicMock()
        mock_enum.code = 10001
        mock_enum.msg = "Test"

        exception = BaseCustomException(code_enum=mock_enum)

        assert isinstance(exception, Exception)
        assert isinstance(exception, BaseCustomException)

    def test_exception_can_be_raised_and_caught(self) -> None:
        """Test that exception can be raised and caught."""
        mock_enum = MagicMock()
        mock_enum.code = 99999
        mock_enum.msg = "Raisable error"

        with pytest.raises(BaseCustomException) as exc_info:
            raise BaseCustomException(code_enum=mock_enum, detail_msg="Test raising")

        caught_exception = exc_info.value
        assert caught_exception.code == 99999
        assert "Raisable error(Test raising)" in caught_exception.message

    def test_with_real_code_enum(self) -> None:
        """Test with real CodeEnum values."""
        exception = BaseCustomException(
            code_enum=CodeEnum.ParameterCheckException,
            detail_msg="Invalid parameter value",
        )

        assert exception.code == CodeEnum.ParameterCheckException.code
        expected_msg = (
            f"{CodeEnum.ParameterCheckException.msg}(Invalid parameter value)"
        )
        assert exception.message == expected_msg


class TestProtocolParamException:
    """Test ProtocolParamException class."""

    def test_init_without_message(self) -> None:
        """Test initialization without message."""
        exception = ProtocolParamException()

        assert exception.code == CodeEnum.ParameterCheckException.code
        assert exception.message == CodeEnum.ParameterCheckException.msg

    def test_init_with_message(self) -> None:
        """Test initialization with message."""
        exception = ProtocolParamException(msg="Invalid request format")

        assert exception.code == CodeEnum.ParameterCheckException.code
        expected_msg = f"{CodeEnum.ParameterCheckException.msg}(Invalid request format)"
        assert exception.message == expected_msg

    def test_init_with_none_message(self) -> None:
        """Test initialization with None message."""
        exception = ProtocolParamException(msg=None)

        assert exception.code == CodeEnum.ParameterCheckException.code
        assert exception.message == CodeEnum.ParameterCheckException.msg

    def test_init_with_empty_message(self) -> None:
        """Test initialization with empty message."""
        exception = ProtocolParamException(msg="")

        assert exception.code == CodeEnum.ParameterCheckException.code
        assert exception.message == CodeEnum.ParameterCheckException.msg

    def test_inheritance(self) -> None:
        """Test inheritance from BaseCustomException."""
        exception = ProtocolParamException("Test message")

        assert isinstance(exception, BaseCustomException)
        assert isinstance(exception, ProtocolParamException)
        assert isinstance(exception, Exception)

    def test_str_and_get_response_methods(self) -> None:
        """Test inherited methods work correctly."""
        exception = ProtocolParamException("Parameter X is invalid")

        # Test str method
        str_repr = str(exception)
        assert str(CodeEnum.ParameterCheckException.code) in str_repr
        assert "Parameter X is invalid" in str_repr

        # Test get_response method
        response = exception.get_response()
        assert response["code"] == CodeEnum.ParameterCheckException.code
        assert "Parameter X is invalid" in response["message"]


class TestServiceException:
    """Test ServiceException class."""

    def test_init_without_message(self) -> None:
        """Test initialization without message."""
        exception = ServiceException()

        assert exception.code == CodeEnum.ServiceException.code
        assert exception.message == CodeEnum.ServiceException.msg

    def test_init_with_message(self) -> None:
        """Test initialization with message."""
        exception = ServiceException(msg="Database connection failed")

        assert exception.code == CodeEnum.ServiceException.code
        expected_msg = f"{CodeEnum.ServiceException.msg}(Database connection failed)"
        assert exception.message == expected_msg

    def test_init_with_none_message(self) -> None:
        """Test initialization with None message."""
        exception = ServiceException(msg=None)

        assert exception.code == CodeEnum.ServiceException.code
        assert exception.message == CodeEnum.ServiceException.msg

    def test_inheritance(self) -> None:
        """Test inheritance from BaseCustomException."""
        exception = ServiceException("Service error")

        assert isinstance(exception, BaseCustomException)
        assert isinstance(exception, ServiceException)
        assert isinstance(exception, Exception)

    def test_exception_handling(self) -> None:
        """Test exception can be properly raised and handled."""
        with pytest.raises(ServiceException) as exc_info:
            raise ServiceException("Critical service failure")

        caught_exception = exc_info.value
        assert caught_exception.code == CodeEnum.ServiceException.code
        assert "Critical service failure" in caught_exception.message


class TestThirdPartyException:
    """Test ThirdPartyException class."""

    def test_init_without_params(self) -> None:
        """Test initialization without parameters."""
        exception = ThirdPartyException()

        assert exception.code == CodeEnum.ThirdPartyServiceFailed.code
        assert exception.message == CodeEnum.ThirdPartyServiceFailed.msg

    def test_init_with_message_only(self) -> None:
        """Test initialization with message only."""
        exception = ThirdPartyException(msg="API request timeout")

        assert exception.code == CodeEnum.ThirdPartyServiceFailed.code
        expected_msg = f"{CodeEnum.ThirdPartyServiceFailed.msg}(API request timeout)"
        assert exception.message == expected_msg

    def test_init_with_custom_code_enum(self) -> None:
        """Test initialization with custom CodeEnum."""
        exception = ThirdPartyException(
            msg="Custom third party error", e=CodeEnum.AIUI_RAGError
        )

        assert exception.code == CodeEnum.AIUI_RAGError.code
        expected_msg = f"{CodeEnum.AIUI_RAGError.msg}(Custom third party error)"
        assert exception.message == expected_msg

    def test_init_with_none_message_custom_enum(self) -> None:
        """Test initialization with None message and custom enum."""
        exception = ThirdPartyException(msg=None, e=CodeEnum.DESK_RAGError)

        assert exception.code == CodeEnum.DESK_RAGError.code
        assert exception.message == CodeEnum.DESK_RAGError.msg

    def test_init_with_none_enum(self) -> None:
        """Test initialization with None enum (should use default)."""
        exception = ThirdPartyException(msg="Test message", e=None)

        assert exception.code == CodeEnum.ThirdPartyServiceFailed.code
        expected_msg = f"{CodeEnum.ThirdPartyServiceFailed.msg}(Test message)"
        assert exception.message == expected_msg

    def test_inheritance(self) -> None:
        """Test inheritance from BaseCustomException."""
        exception = ThirdPartyException("Third party error")

        assert isinstance(exception, BaseCustomException)
        assert isinstance(exception, ThirdPartyException)
        assert isinstance(exception, Exception)

    def test_various_third_party_scenarios(self) -> None:
        """Test various third-party error scenarios."""
        # Scenario 1: AIUI error
        aiui_exception = ThirdPartyException("AIUI API failed", CodeEnum.AIUI_RAGError)
        assert aiui_exception.code == CodeEnum.AIUI_RAGError.code

        # Scenario 2: Desk error
        desk_exception = ThirdPartyException("Desk API failed", CodeEnum.DESK_RAGError)
        assert desk_exception.code == CodeEnum.DESK_RAGError.code

        # Scenario 3: Generic third-party error
        generic_exception = ThirdPartyException("Generic API failed")
        assert generic_exception.code == CodeEnum.ThirdPartyServiceFailed.code


class TestCustomException:
    """Test CustomException class."""

    def test_init_with_code_enum(self) -> None:
        """Test initialization with CodeEnum only."""
        exception = CustomException(e=CodeEnum.ParameterInvalid)

        assert exception.code == CodeEnum.ParameterInvalid.code
        assert exception.message == CodeEnum.ParameterInvalid.msg

    def test_init_with_code_enum_and_message(self) -> None:
        """Test initialization with CodeEnum and message."""
        exception = CustomException(
            e=CodeEnum.FileSplitFailed, msg="File format not supported"
        )

        assert exception.code == CodeEnum.FileSplitFailed.code
        expected_msg = f"{CodeEnum.FileSplitFailed.msg}(File format not supported)"
        assert exception.message == expected_msg

    def test_init_with_none_message(self) -> None:
        """Test initialization with None message."""
        exception = CustomException(e=CodeEnum.ChunkQueryFailed, msg=None)

        assert exception.code == CodeEnum.ChunkQueryFailed.code
        assert exception.message == CodeEnum.ChunkQueryFailed.msg

    def test_inheritance(self) -> None:
        """Test inheritance from BaseCustomException."""
        exception = CustomException(e=CodeEnum.ServiceException, msg="Custom error")

        assert isinstance(exception, BaseCustomException)
        assert isinstance(exception, CustomException)
        assert isinstance(exception, Exception)

    def test_flexible_code_enum_usage(self) -> None:
        """Test usage with different CodeEnum values."""
        # Test with various CodeEnum values
        test_cases = [
            (CodeEnum.ParameterCheckException, "Parameter issue"),
            (CodeEnum.ThirdPartyServiceFailed, "External service down"),
            (CodeEnum.ServiceException, "Internal error"),
        ]

        for code_enum, msg in test_cases:
            exception = CustomException(e=code_enum, msg=msg)
            assert exception.code == code_enum.code
            assert msg in exception.message

    def test_exception_handling_flow(self) -> None:
        """Test complete exception handling flow."""
        with pytest.raises(CustomException) as exc_info:
            raise CustomException(
                e=CodeEnum.FileSplitFailed, msg="Unable to process PDF file"
            )

        caught_exception = exc_info.value
        assert caught_exception.code == CodeEnum.FileSplitFailed.code
        assert "Unable to process PDF file" in caught_exception.message

        # Test response generation
        response = caught_exception.get_response()
        assert response["code"] == CodeEnum.FileSplitFailed.code
        assert "Unable to process PDF file" in response["message"]


class TestExceptionIntegration:
    """Test integration scenarios between exception classes."""

    def test_exception_hierarchy(self) -> None:
        """Test exception hierarchy and isinstance checks."""
        exceptions = [
            ProtocolParamException("Protocol error"),
            ServiceException("Service error"),
            ThirdPartyException("Third party error"),
            CustomException(CodeEnum.ServiceException, "Custom error"),
        ]

        for exception in exceptions:
            # All should be instances of BaseCustomException and Exception
            assert isinstance(exception, BaseCustomException)
            assert isinstance(exception, Exception)

            # All should have required attributes
            assert hasattr(exception, "code")
            assert hasattr(exception, "message")
            assert isinstance(exception.code, int)
            assert isinstance(exception.message, str)

    def test_exception_catching_patterns(self) -> None:
        """Test different exception catching patterns."""
        # Test catching specific exception type
        with pytest.raises(ProtocolParamException):
            raise ProtocolParamException("Protocol error")

        # Test catching base exception type
        with pytest.raises(BaseCustomException):
            raise ServiceException("Service error")

        # Test catching general Exception
        with pytest.raises(Exception):
            raise ThirdPartyException("Third party error")

    def test_exception_response_consistency(self) -> None:
        """Test that all exceptions provide consistent response format."""
        exceptions = [
            ProtocolParamException("Protocol error"),
            ServiceException("Service error"),
            ThirdPartyException("Third party error", CodeEnum.AIUI_RAGError),
            CustomException(CodeEnum.ChunkQueryFailed, "Query failed"),
        ]

        for exception in exceptions:
            response = exception.get_response()

            # All responses should have same structure
            assert isinstance(response, dict)
            assert "code" in response
            assert "message" in response
            assert len(response) == 2  # Only code and message

            # Verify types
            assert isinstance(response["code"], int)
            assert isinstance(response["message"], str)

    def test_exception_string_representation_consistency(self) -> None:
        """Test string representation consistency across exception types."""
        exceptions = [
            ProtocolParamException("Protocol error"),
            ServiceException("Service error"),
            ThirdPartyException("Third party error"),
            CustomException(CodeEnum.ServiceException, "Custom error"),
        ]

        for exception in exceptions:
            str_repr = str(exception)

            # All should follow "code: message" format
            assert ":" in str_repr
            parts = str_repr.split(":", 1)
            assert len(parts) == 2

            # First part should be numeric (code)
            code_part = parts[0].strip()
            assert code_part.isdigit()

            # Second part should be non-empty message
            message_part = parts[1].strip()
            assert len(message_part) > 0

    def test_exception_with_various_message_formats(self) -> None:
        """Test exceptions with various message formats."""
        test_messages = [
            "Simple message",
            "Message with numbers 123",
            "Message with special chars: @#$%",
            "Multi-line\nmessage\nwith\nbreaks",
            "Unicode message: test message 🎉",
            "",  # Empty string
            None,  # None value
        ]

        for msg in test_messages:
            exception = CustomException(CodeEnum.ServiceException, msg)

            # Exception should be creatable and functional
            assert isinstance(exception, BaseCustomException)

            # Should be able to get string representation
            str_repr = str(exception)
            assert isinstance(str_repr, str)

            # Should be able to get response
            response = exception.get_response()
            assert isinstance(response, dict)
            assert "code" in response
            assert "message" in response
