"""
Detailed unit tests for exception handling and error scenarios.

This module provides comprehensive testing for all custom exceptions,
error handling patterns, and edge cases in error conditions across
the SparkLink plugin system.
"""

import pytest
from plugin.link.exceptions.sparklink_exceptions import (
    CallThirdApiException,
    SparkLinkAppIdException,
    SparkLinkBaseException,
    SparkLinkFunctionCallException,
    SparkLinkJsonSchemaException,
    SparkLinkLLMException,
    SparkLinkOpenapiSchemaException,
    ToolNotExistsException,
)
from plugin.link.utils.errors.code import ErrCode


class TestSparkLinkBaseException:
    """Test suite for SparkLinkBaseException base class."""

    def test_sparklink_base_exception_init(self):
        """Test SparkLinkBaseException initialization."""
        code = 1001
        err_pre = "TEST_ERROR"
        err = "This is a test error"

        exception = SparkLinkBaseException(code, err_pre, err)

        assert exception.code == code
        assert exception.message == f"{err_pre}: {err}"

    def test_sparklink_base_exception_str_representation(self):
        """Test SparkLinkBaseException string representation."""
        exception = SparkLinkBaseException(1001, "TEST_ERROR", "Test message")

        str_repr = str(exception)
        assert str_repr == "TEST_ERROR: Test message"

    def test_sparklink_base_exception_with_empty_strings(self):
        """Test SparkLinkBaseException with empty strings."""
        exception = SparkLinkBaseException(0, "", "")

        assert exception.code == 0
        assert exception.message == ": "
        assert str(exception) == ": "

    def test_sparklink_base_exception_with_unicode(self):
        """Test SparkLinkBaseException with Unicode characters."""
        code = 1001
        err_pre = "错误前缀"
        err = "这是一个测试错误信息 🚫"

        exception = SparkLinkBaseException(code, err_pre, err)

        assert exception.code == code
        assert exception.message == f"{err_pre}: {err}"
        assert "🚫" in str(exception)

    def test_sparklink_base_exception_with_multiline_error(self):
        """Test SparkLinkBaseException with multiline error message."""
        multiline_error = "Line 1\nLine 2\nLine 3"
        exception = SparkLinkBaseException(1001, "MULTILINE_ERROR", multiline_error)

        assert "\n" in exception.message
        assert "Line 1" in exception.message
        assert "Line 3" in exception.message

    def test_sparklink_base_exception_inheritance(self):
        """Test SparkLinkBaseException inheritance from Exception."""
        exception = SparkLinkBaseException(1001, "TEST", "message")

        assert isinstance(exception, Exception)
        assert isinstance(exception, SparkLinkBaseException)


class TestCallThirdApiException:
    """Test suite for CallThirdApiException."""

    def test_call_third_api_exception_init(self):
        """Test CallThirdApiException initialization."""
        code = ErrCode.THIRD_API_REQUEST_FAILED_ERR.code
        err_pre = ErrCode.THIRD_API_REQUEST_FAILED_ERR.msg
        err = "API endpoint returned 500 error"

        exception = CallThirdApiException(code, err_pre, err)

        assert exception.code == code
        assert exception.message == f"{err_pre}: {err}"
        assert isinstance(exception, SparkLinkBaseException)

    def test_call_third_api_exception_inheritance(self):
        """Test CallThirdApiException inheritance chain."""
        exception = CallThirdApiException(1001, "API_ERROR", "Failed")

        assert isinstance(exception, CallThirdApiException)
        assert isinstance(exception, SparkLinkBaseException)
        assert isinstance(exception, Exception)

    def test_call_third_api_exception_with_http_status(self):
        """Test CallThirdApiException with HTTP status information."""
        code = 500
        err_pre = "HTTP_REQUEST_FAILED"
        err = "Status: 500, Response: Internal Server Error"

        exception = CallThirdApiException(code, err_pre, err)

        assert "500" in exception.message
        assert "Internal Server Error" in exception.message

    def test_call_third_api_exception_with_timeout(self):
        """Test CallThirdApiException with timeout scenarios."""
        exception = CallThirdApiException(
            code=408,
            err_pre="REQUEST_TIMEOUT",
            err="Connection timeout after 30 seconds",
        )

        assert exception.code == 408
        assert "timeout" in exception.message.lower()

    def test_call_third_api_exception_with_network_error(self):
        """Test CallThirdApiException with network-related errors."""
        exception = CallThirdApiException(
            code=503,
            err_pre="NETWORK_ERROR",
            err="DNS resolution failed for api.example.com",
        )

        assert "DNS resolution" in exception.message
        assert "api.example.com" in exception.message


class TestToolNotExistsException:
    """Test suite for ToolNotExistsException."""

    def test_tool_not_exists_exception_init(self):
        """Test ToolNotExistsException initialization."""
        code = ErrCode.TOOL_NOT_EXIST_ERR.code
        err_pre = ErrCode.TOOL_NOT_EXIST_ERR.msg
        err = "Tool 'calculator@v1.0' not found"

        exception = ToolNotExistsException(code, err_pre, err)

        assert exception.code == code
        assert "calculator@v1.0" in exception.message

    def test_tool_not_exists_exception_with_tool_id(self):
        """Test ToolNotExistsException with specific tool ID."""
        exception = ToolNotExistsException(
            code=404,
            err_pre="TOOL_NOT_FOUND",
            err="tool@123456789abcdef does not exist in registry",
        )

        assert "tool@123456789abcdef" in exception.message
        assert "registry" in exception.message

    def test_tool_not_exists_exception_with_version_info(self):
        """Test ToolNotExistsException with version information."""
        exception = ToolNotExistsException(
            code=404,
            err_pre="TOOL_VERSION_NOT_FOUND",
            err="Tool 'search_tool' version '2.0.0' not available",
        )

        assert "search_tool" in exception.message
        assert "2.0.0" in exception.message

    def test_tool_not_exists_exception_inheritance(self):
        """Test ToolNotExistsException inheritance."""
        exception = ToolNotExistsException(404, "TOOL_ERROR", "Not found")

        assert isinstance(exception, ToolNotExistsException)
        assert isinstance(exception, SparkLinkBaseException)


class TestSparkLinkOpenapiSchemaException:
    """Test suite for SparkLinkOpenapiSchemaException."""

    def test_openapi_schema_exception_init(self):
        """Test SparkLinkOpenapiSchemaException initialization."""
        code = ErrCode.OPENAPI_SCHEMA_VALIDATE_ERR.code
        err_pre = ErrCode.OPENAPI_SCHEMA_VALIDATE_ERR.msg
        err = "Invalid OpenAPI schema: missing 'paths' field"

        exception = SparkLinkOpenapiSchemaException(code, err_pre, err)

        assert exception.code == code
        assert "OpenAPI schema" in exception.message
        assert "paths" in exception.message

    def test_openapi_schema_exception_with_validation_details(self):
        """Test SparkLinkOpenapiSchemaException with validation details."""
        validation_error = (
            "$.paths./users.get.responses.200: missing required property 'description'"
        )
        exception = SparkLinkOpenapiSchemaException(
            code=400, err_pre="SCHEMA_VALIDATION_ERROR", err=validation_error
        )

        assert "$.paths" in exception.message
        assert "description" in exception.message

    def test_openapi_schema_exception_with_version_error(self):
        """Test SparkLinkOpenapiSchemaException with version-related error."""
        exception = SparkLinkOpenapiSchemaException(
            code=400,
            err_pre="UNSUPPORTED_OPENAPI_VERSION",
            err="OpenAPI version '2.0' is not supported, use version 3.0.0 or higher",
        )

        assert "2.0" in exception.message
        assert "3.0.0" in exception.message

    def test_openapi_schema_exception_inheritance(self):
        """Test SparkLinkOpenapiSchemaException inheritance."""
        exception = SparkLinkOpenapiSchemaException(400, "SCHEMA_ERROR", "Invalid")

        assert isinstance(exception, SparkLinkOpenapiSchemaException)
        assert isinstance(exception, SparkLinkBaseException)


class TestSparkLinkJsonSchemaException:
    """Test suite for SparkLinkJsonSchemaException."""

    def test_json_schema_exception_init(self):
        """Test SparkLinkJsonSchemaException initialization."""
        code = ErrCode.JSON_SCHEMA_VALIDATE_ERR.code
        err_pre = ErrCode.JSON_SCHEMA_VALIDATE_ERR.msg
        err = "JSON schema validation failed: 'name' is required"

        exception = SparkLinkJsonSchemaException(code, err_pre, err)

        assert exception.code == code
        assert "JSON schema" in exception.message
        assert "name" in exception.message

    def test_json_schema_exception_with_field_path(self):
        """Test SparkLinkJsonSchemaException with specific field path."""
        exception = SparkLinkJsonSchemaException(
            code=400,
            err_pre="JSON_VALIDATION_ERROR",
            err="Field 'payload.tools[0].schema' must be a valid JSON string",
        )

        assert "payload.tools[0].schema" in exception.message
        assert "JSON string" in exception.message

    def test_json_schema_exception_with_type_error(self):
        """Test SparkLinkJsonSchemaException with type validation error."""
        exception = SparkLinkJsonSchemaException(
            code=400,
            err_pre="TYPE_VALIDATION_ERROR",
            err="Expected 'integer' but got 'string' for field 'count'",
        )

        assert "integer" in exception.message
        assert "string" in exception.message
        assert "count" in exception.message


class TestSparkLinkFunctionCallException:
    """Test suite for SparkLinkFunctionCallException."""

    def test_function_call_exception_init(self):
        """Test SparkLinkFunctionCallException initialization."""
        code = 500
        err_pre = "FUNCTION_EXECUTION_ERROR"
        err = "Function 'calculate_sum' failed with division by zero"

        exception = SparkLinkFunctionCallException(code, err_pre, err)

        assert exception.code == code
        assert "calculate_sum" in exception.message
        assert "division by zero" in exception.message

    def test_function_call_exception_with_stack_trace(self):
        """Test SparkLinkFunctionCallException with stack trace information."""
        stack_trace = (
            "Traceback (most recent call last):\n  "
            "   File 'test.py', line 42, in calculate\n"
            "   result = x / y\n"
            "   ZeroDivisionError: division by zero"
        )
        exception = SparkLinkFunctionCallException(
            code=500, err_pre="RUNTIME_ERROR", err=stack_trace
        )

        assert "Traceback" in exception.message
        assert "ZeroDivisionError" in exception.message

    def test_function_call_exception_with_parameter_info(self):
        """Test SparkLinkFunctionCallException with parameter information."""
        exception = SparkLinkFunctionCallException(
            code=400,
            err_pre="INVALID_PARAMETERS",
            err=(
                "Function 'search' called with invalid parameter: "
                "'limit' must be positive integer, got -5",
            )
        )

        assert "search" in exception.message
        assert "limit" in exception.message
        assert "-5" in exception.message


class TestSparkLinkLLMException:
    """Test suite for SparkLinkLLMException."""

    def test_llm_exception_init(self):
        """Test SparkLinkLLMException initialization."""
        code = 502
        err_pre = "LLM_API_ERROR"
        err = "OpenAI API rate limit exceeded"

        exception = SparkLinkLLMException(code, err_pre, err)

        assert exception.code == code
        assert "OpenAI" in exception.message
        assert "rate limit" in exception.message

    def test_llm_exception_with_model_info(self):
        """Test SparkLinkLLMException with model information."""
        exception = SparkLinkLLMException(
            code=400,
            err_pre="MODEL_NOT_AVAILABLE",
            err="Model 'gpt-4-turbo' is not available in region 'us-east-1'",
        )

        assert "gpt-4-turbo" in exception.message
        assert "us-east-1" in exception.message

    def test_llm_exception_with_token_limit(self):
        """Test SparkLinkLLMException with token limit issues."""
        exception = SparkLinkLLMException(
            code=413,
            err_pre="TOKEN_LIMIT_EXCEEDED",
            err="Input token count (8192) exceeds model maximum (4096)",
        )

        assert "8192" in exception.message
        assert "4096" in exception.message

    def test_llm_exception_with_response_parsing(self):
        """Test SparkLinkLLMException with response parsing errors."""
        exception = SparkLinkLLMException(
            code=502,
            err_pre="RESPONSE_PARSING_ERROR",
            err=(
                "Failed to parse LLM response as JSON: "
                "Expecting ',' delimiter at line 3 column 15"
            ),
        )

        assert "JSON" in exception.message
        assert "line 3 column 15" in exception.message


class TestSparkLinkAppIdException:
    """Test suite for SparkLinkAppIdException."""

    def test_app_id_exception_init(self):
        """Test SparkLinkAppIdException initialization."""
        code = 401
        err_pre = "INVALID_APP_ID"
        err = "Application ID '12345678' is not authorized"

        exception = SparkLinkAppIdException(code, err_pre, err)

        assert exception.code == code
        assert "12345678" in exception.message
        assert "authorized" in exception.message

    def test_app_id_exception_with_validation_error(self):
        """Test SparkLinkAppIdException with validation errors."""
        exception = SparkLinkAppIdException(
            code=400,
            err_pre="APP_ID_VALIDATION_ERROR",
            err="Application ID must be 8 digits, got '123abc'",
        )

        assert "8 digits" in exception.message
        assert "123abc" in exception.message

    def test_app_id_exception_with_expiration(self):
        """Test SparkLinkAppIdException with expiration information."""
        exception = SparkLinkAppIdException(
            code=401,
            err_pre="APP_ID_EXPIRED",
            err="Application ID '87654321' expired on 2023-12-31",
        )

        assert "87654321" in exception.message
        assert "expired" in exception.message
        assert "2023-12-31" in exception.message

    def test_app_id_exception_with_rate_limiting(self):
        """Test SparkLinkAppIdException with rate limiting."""
        exception = SparkLinkAppIdException(
            code=429,
            err_pre="RATE_LIMIT_EXCEEDED",
            err="Application ID '11111111' has exceeded rate limit: 100 requests/hour",
        )

        assert "11111111" in exception.message
        assert "rate limit" in exception.message
        assert "100 requests/hour" in exception.message


class TestExceptionInheritanceAndBehavior:
    """Test suite for exception inheritance and polymorphic behavior."""

    def test_all_exceptions_inherit_from_base(self):
        """Test that all custom exceptions inherit from SparkLinkBaseException."""
        exception_classes = [
            CallThirdApiException,
            ToolNotExistsException,
            SparkLinkOpenapiSchemaException,
            SparkLinkJsonSchemaException,
            SparkLinkFunctionCallException,
            SparkLinkLLMException,
            SparkLinkAppIdException,
        ]

        for exc_class in exception_classes:
            exception = exc_class(1001, "TEST", "test message")
            assert isinstance(exception, SparkLinkBaseException)
            assert isinstance(exception, Exception)

    def test_polymorphic_exception_handling(self):
        """Test polymorphic handling of different exception types."""
        exceptions = [
            CallThirdApiException(500, "API_ERROR", "API failed"),
            ToolNotExistsException(404, "TOOL_ERROR", "Tool not found"),
            SparkLinkOpenapiSchemaException(400, "SCHEMA_ERROR", "Invalid schema"),
            SparkLinkJsonSchemaException(400, "JSON_ERROR", "Invalid JSON"),
            SparkLinkFunctionCallException(500, "FUNCTION_ERROR", "Function failed"),
            SparkLinkLLMException(502, "LLM_ERROR", "LLM unavailable"),
            SparkLinkAppIdException(401, "AUTH_ERROR", "Unauthorized"),
        ]

        # Should be able to catch all with base exception
        for exc in exceptions:
            try:
                raise exc
            except SparkLinkBaseException as caught:
                assert caught.code == exc.code
                assert caught.message == exc.message

    def test_exception_chaining_support(self):
        """Test that exceptions support chaining with 'from' clause."""
        original_error = ValueError("Original error")

        try:
            try:
                raise original_error
            except ValueError as e:
                raise CallThirdApiException(
                    code=500,
                    err_pre="WRAPPED_ERROR",
                    err="API call failed due to internal error",
                ) from e
        except CallThirdApiException as caught:
            assert caught.code == 500
            assert "API call failed" in caught.message
            # Python exception chaining
            assert caught.__cause__ == original_error

    def test_exception_in_try_except_blocks(self):
        """Test exception behavior in nested try-except blocks."""

        def raise_nested_exception():
            try:
                raise ToolNotExistsException(404, "TOOL_ERROR", "Inner error")
            except ToolNotExistsException:
                raise CallThirdApiException(500, "API_ERROR", "Outer error")

        with pytest.raises(CallThirdApiException) as exc_info:
            raise_nested_exception()

        assert exc_info.value.code == 500
        assert "Outer error" in exc_info.value.message

    def test_exception_with_complex_error_messages(self):
        """Test exceptions with complex, multi-part error messages."""
        complex_error = {
            "error_type": "ValidationError",
            "field": "payload.tools[0].schema",
            "expected": "valid JSON string",
            "actual": "malformed JSON",
            "line": 15,
            "column": 23,
        }

        error_message = f"Validation failed: {complex_error}"
        exception = SparkLinkJsonSchemaException(
            code=400, err_pre="COMPLEX_VALIDATION_ERROR", err=error_message
        )

        assert "ValidationError" in exception.message
        assert "payload.tools[0].schema" in exception.message
        assert "line" in exception.message


class TestExceptionEdgeCases:
    """Test suite for exception edge cases and boundary conditions."""

    def test_exception_with_none_values(self):
        """Test exception behavior with None values."""
        exception = SparkLinkBaseException(None, None, None)

        assert exception.code is None
        assert exception.message == "None: None"

    def test_exception_with_numeric_error_messages(self):
        """Test exceptions with numeric error messages."""
        exception = SparkLinkBaseException(123, 456, 789)

        assert exception.code == 123
        assert exception.message == "456: 789"

    def test_exception_with_boolean_values(self):
        """Test exceptions with boolean values."""
        exception = SparkLinkBaseException(True, False, True)

        assert exception.code is True
        assert exception.message == "False: True"

    def test_exception_with_very_long_messages(self):
        """Test exceptions with very long error messages."""
        long_message = "A" * 10000  # 10KB message
        exception = SparkLinkBaseException(1001, "LONG_ERROR", long_message)

        assert len(exception.message) > 10000
        assert exception.message.endswith("A" * 100)  # Check it ends with A's

    def test_exception_with_special_characters(self):
        """Test exceptions with special characters in messages."""
        special_chars = "!@#$%^&*()[]{}|;':\",./<>?`~\\+=_-"
        exception = SparkLinkBaseException(1001, special_chars, special_chars)

        assert special_chars in exception.message
        assert len([c for c in special_chars if c in exception.message]) > 10

    def test_exception_str_method_consistency(self):
        """Test that __str__ method is consistent across calls."""
        exception = SparkLinkBaseException(1001, "TEST", "message")

        str1 = str(exception)
        str2 = str(exception)
        str3 = exception.__str__()

        assert str1 == str2 == str3

    def test_exception_attribute_modification(self):
        """Test behavior when exception attributes are modified after creation."""
        exception = SparkLinkBaseException(1001, "ORIGINAL", "original message")
        original_message = exception.message

        # Modify attributes
        exception.code = 2002
        exception.message = "modified message"

        assert exception.code == 2002
        assert exception.message != original_message
        assert str(exception) == "modified message"

    def test_multiple_exception_instances_independence(self):
        """Test that multiple exception instances are independent."""
        exc1 = SparkLinkBaseException(1001, "ERROR1", "message1")
        exc2 = SparkLinkBaseException(2002, "ERROR2", "message2")

        # Modify one
        exc1.code = 9999

        # Other should be unchanged
        assert exc2.code == 2002
        assert exc2.message == "ERROR2: message2"
        assert exc1.code != exc2.code
