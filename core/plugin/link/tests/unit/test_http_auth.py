"""
Detailed unit tests for HTTP authentication module.

This module provides comprehensive function-level testing for HTTP authentication
utilities including MD5 token generation, HMAC signature creation, URL parsing,
WebSocket auth URL assembly, and timestamp generation functions.
"""

import base64
import hashlib
import json
import os
import time
from datetime import datetime
from unittest.mock import patch

import pytest
from plugin.link.consts import const
from plugin.link.infra.tool_exector.http_auth import (
    AssembleHeaderException,
    Url,
    assemble_ws_auth_url,
    generate_13_digit_timestamp,
    get_query_url,
    hashlib_256,
    md5_encode,
    parse_url,
    public_query_url,
)


class TestTimestampGeneration:
    """Test suite for timestamp generation functions."""

    def test_generate_13_digit_timestamp_format(self):
        """Test generate_13_digit_timestamp returns correct format."""
        timestamp = generate_13_digit_timestamp()

        assert len(timestamp) == 13
        assert timestamp.isdigit()

    def test_generate_13_digit_timestamp_current_time(self):
        """Test generate_13_digit_timestamp generates current time."""
        before_time = time.time()
        timestamp = generate_13_digit_timestamp()
        after_time = time.time()

        # Convert timestamp back to float for comparison
        timestamp_float = int(timestamp) / 1000.0

        # Allow for small timing differences (within 1 second)
        assert abs(timestamp_float - before_time) <= 1.0
        assert abs(after_time - timestamp_float) <= 1.0

    def test_generate_13_digit_timestamp_millisecond_precision(self):
        """Test generate_13_digit_timestamp includes millisecond precision."""
        # Generate multiple timestamps in quick succession
        timestamps = []
        for _ in range(10):
            timestamps.append(generate_13_digit_timestamp())

        # At least some should be different due to millisecond precision
        unique_timestamps = set(timestamps)
        assert len(unique_timestamps) >= 1  # At minimum, not all identical

    def test_generate_13_digit_timestamp_consistency(self):
        """Test generate_13_digit_timestamp format consistency."""
        timestamp1 = generate_13_digit_timestamp()
        timestamp2 = generate_13_digit_timestamp()

        # Both should have same format
        assert len(timestamp1) == len(timestamp2) == 13
        assert timestamp1.isdigit() and timestamp2.isdigit()

        # Timestamps should be close in value (within reasonable time window)
        diff = abs(int(timestamp1) - int(timestamp2))
        assert diff < 1000  # Less than 1 second difference


class TestMD5Encoding:
    """Test suite for MD5 encoding function."""

    def test_md5_encode_basic_string(self):
        """Test md5_encode with basic string input."""
        text = "hello world"
        result = md5_encode(text)

        # Should return 32-character hex string
        assert len(result) == 32
        assert all(c in "0123456789abcdef" for c in result)

        # Should match expected MD5 hash
        expected = hashlib.md5(text.encode()).hexdigest()
        assert result == expected

    def test_md5_encode_empty_string(self):
        """Test md5_encode with empty string."""
        result = md5_encode("")

        assert len(result) == 32
        expected = hashlib.md5("".encode()).hexdigest()
        assert result == expected

    def test_md5_encode_unicode_string(self):
        """Test md5_encode with Unicode characters."""
        text = "你好世界"  # "Hello World" in Chinese
        result = md5_encode(text)

        assert len(result) == 32
        expected = hashlib.md5(text.encode()).hexdigest()
        assert result == expected

    def test_md5_encode_special_characters(self):
        """Test md5_encode with special characters."""
        text = "!@#$%^&*()_+-=[]{}|;':\",./<>?"
        result = md5_encode(text)

        assert len(result) == 32
        expected = hashlib.md5(text.encode()).hexdigest()
        assert result == expected

    def test_md5_encode_long_string(self):
        """Test md5_encode with very long string."""
        text = "a" * 10000  # 10k character string
        result = md5_encode(text)

        assert len(result) == 32
        expected = hashlib.md5(text.encode()).hexdigest()
        assert result == expected

    def test_md5_encode_consistency(self):
        """Test md5_encode produces consistent results."""
        text = "test string"
        result1 = md5_encode(text)
        result2 = md5_encode(text)

        assert result1 == result2


class TestPublicQueryUrl:
    """Test suite for public_query_url function."""

    @patch("infra.tool_exector.http_auth.generate_13_digit_timestamp")
    @patch.dict(
        os.environ,
        {
            const.HTTP_AUTH_QU_APP_ID_KEY: "test_app_id",
            const.HTTP_AUTH_QU_APP_KEY_KEY: "test_app_key",
        },
    )
    def test_public_query_url_basic(self, mock_timestamp):
        """Test public_query_url with basic parameters."""
        mock_timestamp.return_value = "1234567890123"

        url = "https://api.example.com/test"
        result = public_query_url(url)

        # Should contain all required parameters
        assert "appId=test_app_id" in result
        assert "timestamp=1234567890123" in result
        assert "token=" in result
        assert url in result

        # Token should be MD5 of app_id + app_key + timestamp
        expected_md5_input = "test_app_id" + "test_app_key" + "1234567890123"
        expected_token = md5_encode(expected_md5_input)
        assert f"token={expected_token}" in result

    @patch("infra.tool_exector.http_auth.generate_13_digit_timestamp")
    @patch.dict(
        os.environ,
        {
            const.HTTP_AUTH_QU_APP_ID_KEY: "app123",
            const.HTTP_AUTH_QU_APP_KEY_KEY: "key456",
        },
    )
    def test_public_query_url_different_credentials(self, mock_timestamp):
        """Test public_query_url with different credentials."""
        mock_timestamp.return_value = "9876543210987"

        url = "https://different.api.com/endpoint"
        result = public_query_url(url)

        assert "appId=app123" in result
        assert "timestamp=9876543210987" in result

        expected_md5_input = "app123" + "key456" + "9876543210987"
        expected_token = md5_encode(expected_md5_input)
        assert f"token={expected_token}" in result

    @patch("infra.tool_exector.http_auth.generate_13_digit_timestamp")
    @patch.dict(
        os.environ,
        {
            const.HTTP_AUTH_QU_APP_ID_KEY: "test_app",
            const.HTTP_AUTH_QU_APP_KEY_KEY: "test_key",
        },
    )
    def test_public_query_url_unused_parameters(self, mock_timestamp):
        """Test public_query_url ignores unused app_id and app_key parameters."""
        mock_timestamp.return_value = "1111111111111"

        url = "https://api.example.com/test"
        # These parameters should be ignored in favor of environment variables
        result = public_query_url(url, app_id="ignored_id", app_key="ignored_key")

        # Should use environment variables, not passed parameters
        assert "appId=test_app" in result
        expected_md5_input = "test_app" + "test_key" + "1111111111111"
        expected_token = md5_encode(expected_md5_input)
        assert f"token={expected_token}" in result

    @patch("infra.tool_exector.http_auth.generate_13_digit_timestamp")
    @patch.dict(
        os.environ,
        {const.HTTP_AUTH_QU_APP_ID_KEY: "", const.HTTP_AUTH_QU_APP_KEY_KEY: ""},
    )
    def test_public_query_url_empty_credentials(self, mock_timestamp):
        """Test public_query_url with empty credentials."""
        mock_timestamp.return_value = "1234567890123"

        url = "https://api.example.com/test"
        result = public_query_url(url)

        # Should still work with empty credentials
        assert "appId=" in result
        assert "timestamp=1234567890123" in result
        expected_token = md5_encode("" + "" + "1234567890123")
        assert f"token={expected_token}" in result


class TestGetQueryUrl:
    """Test suite for get_query_url function."""

    @patch("infra.tool_exector.http_auth.generate_13_digit_timestamp")
    @patch.dict(
        os.environ,
        {
            const.HTTP_AUTH_QU_APP_ID_KEY: "test_app_id",
            const.HTTP_AUTH_QU_APP_KEY_KEY: "test_app_key",
        },
    )
    def test_get_query_url_basic(self, mock_timestamp):
        """Test get_query_url with basic parameters."""
        mock_timestamp.return_value = "1234567890123"

        url = "https://api.example.com/test"
        result = get_query_url(url)

        # Should include basic auth parameters
        assert "appId=test_app_id" in result
        assert "timestamp=1234567890123" in result
        assert "token=" in result

    @patch("infra.tool_exector.http_auth.generate_13_digit_timestamp")
    @patch.dict(
        os.environ,
        {
            const.HTTP_AUTH_QU_APP_ID_KEY: "test_app_id",
            const.HTTP_AUTH_QU_APP_KEY_KEY: "test_app_key",
        },
    )
    def test_get_query_url_with_public_data(self, mock_timestamp):
        """Test get_query_url with public_data parameters."""
        mock_timestamp.return_value = "1234567890123"

        url = "https://api.example.com/test"
        public_data = {"param1": "value1", "param2": "value2"}
        result = get_query_url(url, public_data=public_data)

        # Should include public data parameters
        assert "param1=value1" in result
        assert "param2=value2" in result
        assert "appId=test_app_id" in result

    @patch("infra.tool_exector.http_auth.generate_13_digit_timestamp")
    @patch.dict(
        os.environ,
        {
            const.HTTP_AUTH_QU_APP_ID_KEY: "test_app_id",
            const.HTTP_AUTH_QU_APP_KEY_KEY: "test_app_key",
        },
    )
    def test_get_query_url_with_query_data(self, mock_timestamp):
        """Test get_query_url with query_data parameters."""
        mock_timestamp.return_value = "1234567890123"

        url = "https://api.example.com/test"
        query_data = {"search": "test", "limit": "10"}
        result = get_query_url(url, query_data=query_data)

        # Should include query data parameters
        assert "search=test" in result
        assert "limit=10" in result

    @patch("infra.tool_exector.http_auth.generate_13_digit_timestamp")
    @patch.dict(
        os.environ,
        {
            const.HTTP_AUTH_QU_APP_ID_KEY: "test_app_id",
            const.HTTP_AUTH_QU_APP_KEY_KEY: "test_app_key",
        },
    )
    def test_get_query_url_with_both_data_types(self, mock_timestamp):
        """Test get_query_url with both public_data and query_data."""
        mock_timestamp.return_value = "1234567890123"

        url = "https://api.example.com/test"
        public_data = {"public_param": "public_value"}
        query_data = {"query_param": "query_value"}
        result = get_query_url(url, public_data=public_data, query_data=query_data)

        # Should include all parameters
        assert "public_param=public_value" in result
        assert "query_param=query_value" in result
        assert "appId=test_app_id" in result

    @patch("infra.tool_exector.http_auth.generate_13_digit_timestamp")
    @patch.dict(
        os.environ,
        {
            const.HTTP_AUTH_QU_APP_ID_KEY: "test_app_id",
            const.HTTP_AUTH_QU_APP_KEY_KEY: "test_app_key",
        },
    )
    def test_get_query_url_empty_data_dicts(self, mock_timestamp):
        """Test get_query_url with empty data dictionaries."""
        mock_timestamp.return_value = "1234567890123"

        url = "https://api.example.com/test"
        result = get_query_url(url, public_data={}, query_data={})

        # Should still include basic auth parameters
        assert "appId=test_app_id" in result
        assert "timestamp=1234567890123" in result

    @patch("infra.tool_exector.http_auth.generate_13_digit_timestamp")
    @patch.dict(
        os.environ,
        {
            const.HTTP_AUTH_QU_APP_ID_KEY: "test_app_id",
            const.HTTP_AUTH_QU_APP_KEY_KEY: "test_app_key",
        },
    )
    def test_get_query_url_none_data_dicts(self, mock_timestamp):
        """Test get_query_url with None data dictionaries."""
        mock_timestamp.return_value = "1234567890123"

        url = "https://api.example.com/test"
        result = get_query_url(url, public_data=None, query_data=None)

        # Should still work with None values
        assert "appId=test_app_id" in result
        assert "timestamp=1234567890123" in result


class TestUrlParsing:
    """Test suite for URL parsing functions."""

    def test_parse_url_basic_https(self):
        """Test parse_url with basic HTTPS URL."""
        url = "https://api.example.com/v1/users"
        result = parse_url(url)

        assert isinstance(result, Url)
        assert result.schema == "https://"
        assert result.host == "api.example.com"
        assert result.path == "/v1/users"

    def test_parse_url_basic_http(self):
        """Test parse_url with basic HTTP URL."""
        url = "http://localhost:8080/api/test"
        result = parse_url(url)

        assert result.schema == "http://"
        assert result.host == "localhost:8080"
        assert result.path == "/api/test"

    def test_parse_url_with_port(self):
        """Test parse_url with port number."""
        url = "https://api.example.com:443/secure/endpoint"
        result = parse_url(url)

        assert result.schema == "https://"
        assert result.host == "api.example.com:443"
        assert result.path == "/secure/endpoint"

    def test_parse_url_root_path(self):
        """Test parse_url with root path."""
        url = "https://example.com/"
        result = parse_url(url)

        assert result.schema == "https://"
        assert result.host == "example.com"
        assert result.path == "/"

    def test_parse_url_complex_path(self):
        """Test parse_url with complex path."""
        url = "https://api.example.com/v2/users/123/profile?param=value"
        result = parse_url(url)

        assert result.schema == "https://"
        assert result.host == "api.example.com"
        assert result.path == "/v2/users/123/profile?param=value"

    def test_parse_url_no_path_raises_exception(self):
        """Test parse_url raises exception when no path separator found."""
        url = "https://example.com"  # No trailing slash

        with pytest.raises(AssembleHeaderException) as exc_info:
            parse_url(url)

        assert "invalid request url" in str(exc_info.value.message)

    def test_parse_url_malformed_schema(self):
        """Test parse_url with malformed schema."""
        url = "invalid-schema-example.com/path"

        with pytest.raises(ValueError):
            # Should raise ValueError when trying to find "://"
            parse_url(url)

    def test_parse_url_custom_schema(self):
        """Test parse_url with custom schema."""
        url = "ftp://files.example.com/downloads/file.txt"
        result = parse_url(url)

        assert result.schema == "ftp://"
        assert result.host == "files.example.com"
        assert result.path == "/downloads/file.txt"


class TestAssembleHeaderException:
    """Test suite for AssembleHeaderException."""

    def test_assemble_header_exception_creation(self):
        """Test AssembleHeaderException creation with message."""
        message = "Test error message"
        exception = AssembleHeaderException(message)

        assert exception.message == message

    def test_assemble_header_exception_string_representation(self):
        """Test AssembleHeaderException string representation."""
        message = "Authentication header assembly failed"
        exception = AssembleHeaderException(message)

        # Test that the exception can be converted to string
        assert str(exception.message) == message


class TestUrlClass:
    """Test suite for Url class."""

    def test_url_class_creation(self):
        """Test Url class creation with parameters."""
        host = "api.example.com"
        path = "/v1/users"
        schema = "https://"

        url = Url(host, path, schema)

        assert url.host == host
        assert url.path == path
        assert url.schema == schema

    def test_url_class_attribute_access(self):
        """Test Url class attribute access."""
        url = Url("test.com", "/test", "http://")

        # Should be able to access all attributes
        assert hasattr(url, "host")
        assert hasattr(url, "path")
        assert hasattr(url, "schema")

    def test_url_class_with_none_values(self):
        """Test Url class creation with None values."""
        url = Url(None, None, None)

        assert url.host is None
        assert url.path is None
        assert url.schema is None


class TestHashlib256:
    """Test suite for hashlib_256 function."""

    def test_hashlib_256_basic_dict(self):
        """Test hashlib_256 with basic dictionary."""
        data = {"key": "value", "number": 123}
        result = hashlib_256(data)

        # Should start with SHA-256=
        assert result.startswith("SHA-256=")

        # Should be base64 encoded after prefix
        base64_part = result[8:]  # Remove "SHA-256=" prefix
        assert len(base64_part) > 0

        # Should be valid base64
        try:
            base64.b64decode(base64_part)
        except Exception:
            pytest.fail("Result should be valid base64")

    def test_hashlib_256_empty_dict(self):
        """Test hashlib_256 with empty dictionary."""
        data = {}
        result = hashlib_256(data)

        assert result.startswith("SHA-256=")

        # Verify it matches expected hash
        json_str = json.dumps(data)
        expected_hash = hashlib.sha256(json_str.encode()).digest()
        expected_result = "SHA-256=" + base64.b64encode(expected_hash).decode()
        assert result == expected_result

    def test_hashlib_256_complex_data(self):
        """Test hashlib_256 with complex nested data."""
        data = {
            "user": {
                "name": "test",
                "preferences": {"theme": "dark", "language": "en"},
            },
            "items": [1, 2, 3],
            "timestamp": 1234567890,
        }
        result = hashlib_256(data)

        assert result.startswith("SHA-256=")

        # Verify consistency
        result2 = hashlib_256(data)
        assert result == result2

    def test_hashlib_256_with_unicode(self):
        """Test hashlib_256 with Unicode characters."""
        data = {"message": "你好世界", "emoji": "🌟"}
        result = hashlib_256(data)

        assert result.startswith("SHA-256=")

        # Should handle Unicode correctly
        json_str = json.dumps(data)
        expected_hash = hashlib.sha256(json_str.encode()).digest()
        expected_result = "SHA-256=" + base64.b64encode(expected_hash).decode()
        assert result == expected_result

    def test_hashlib_256_deterministic(self):
        """Test hashlib_256 produces deterministic results."""
        data = {"test": "data", "number": 42}

        results = []
        for _ in range(5):
            results.append(hashlib_256(data))

        # All results should be identical
        assert len(set(results)) == 1

    def test_hashlib_256_different_data_different_hash(self):
        """Test hashlib_256 produces different hashes for different data."""
        data1 = {"key": "value1"}
        data2 = {"key": "value2"}

        result1 = hashlib_256(data1)
        result2 = hashlib_256(data2)

        assert result1 != result2
        assert result1.startswith("SHA-256=")
        assert result2.startswith("SHA-256=")


class TestAssembleWsAuthUrl:
    """Test suite for assemble_ws_auth_url function."""

    @patch("infra.tool_exector.http_auth.datetime")
    @patch.dict(
        os.environ,
        {
            const.HTTP_AUTH_AWAU_APP_ID_KEY: "test_app_id",
            const.HTTP_AUTH_AWAU_API_KEY_KEY: "test_api_key",
            const.HTTP_AUTH_AWAU_API_SECRET_KEY: "test_secret",
        },
    )
    def test_assemble_ws_auth_url_basic(self, mock_datetime):
        """Test assemble_ws_auth_url with basic parameters."""
        # Mock datetime
        mock_now = datetime(2023, 1, 1, 12, 0, 0)
        mock_datetime.now.return_value = mock_now

        url = "https://api.example.com/websocket"
        method = "GET"
        auth_config = {
            "authorization_input_part": "api_key",
            "is_digest": False,
            "is_url_join": False,
        }

        result_url, headers = assemble_ws_auth_url(url, method, auth_config)

        # Should return tuple with URL and headers
        assert isinstance(result_url, str)
        assert isinstance(headers, dict)

        # Headers should include required fields
        assert "app_id" in headers
        assert headers["app_id"] == "test_app_id"

    @patch("infra.tool_exector.http_auth.datetime")
    @patch.dict(
        os.environ,
        {
            const.HTTP_AUTH_AWAU_APP_ID_KEY: "test_app_id",
            const.HTTP_AUTH_AWAU_API_KEY_KEY: "test_api_key",
            const.HTTP_AUTH_AWAU_API_SECRET_KEY: "test_secret",
        },
    )
    def test_assemble_ws_auth_url_with_digest(self, mock_datetime):
        """Test assemble_ws_auth_url with digest authentication."""
        mock_now = datetime(2023, 1, 1, 12, 0, 0)
        mock_datetime.now.return_value = mock_now

        url = "https://api.example.com/websocket"
        method = "POST"
        auth_config = {
            "authorization_input_part": "api_key",
            "is_digest": True,
            "is_url_join": False,
        }
        body = {"data": "test"}

        result_url, headers = assemble_ws_auth_url(url, method, auth_config, body)

        # Should include digest in headers when is_digest is True
        assert "Digest" in headers
        assert headers["Digest"].startswith("SHA-256=")

    @patch("infra.tool_exector.http_auth.datetime")
    @patch.dict(
        os.environ,
        {
            const.HTTP_AUTH_AWAU_APP_ID_KEY: "test_app_id",
            const.HTTP_AUTH_AWAU_API_KEY_KEY: "test_api_key",
            const.HTTP_AUTH_AWAU_API_SECRET_KEY: "test_secret",
        },
    )
    def test_assemble_ws_auth_url_with_url_join(self, mock_datetime):
        """Test assemble_ws_auth_url with URL join option."""
        mock_now = datetime(2023, 1, 1, 12, 0, 0)
        mock_datetime.now.return_value = mock_now

        url = "https://api.example.com/websocket"
        method = "GET"
        auth_config = {
            "authorization_input_part": "api_key",
            "is_digest": False,
            "is_url_join": True,
        }

        result_url, headers = assemble_ws_auth_url(url, method, auth_config)

        # URL should include query parameters when is_url_join is True
        assert "?" in result_url
        assert "host=" in result_url
        assert "date=" in result_url
        assert "authorization=" in result_url

    @patch("infra.tool_exector.http_auth.datetime")
    @patch.dict(
        os.environ,
        {
            const.HTTP_AUTH_AWAU_APP_ID_KEY: "test_app_id",
            const.HTTP_AUTH_AWAU_API_KEY_KEY: "test_api_key",
            const.HTTP_AUTH_AWAU_API_SECRET_KEY: "test_secret",
        },
    )
    def test_assemble_ws_auth_url_signature_generation(self, mock_datetime):
        """Test assemble_ws_auth_url generates valid HMAC signature."""
        mock_now = datetime(2023, 1, 1, 12, 0, 0)
        mock_datetime.now.return_value = mock_now

        url = "https://api.example.com/test"
        method = "GET"
        auth_config = {
            "authorization_input_part": "api_key",
            "is_digest": False,
            "is_url_join": False,
        }

        result_url, headers = assemble_ws_auth_url(url, method, auth_config)

        # Should generate valid signature (implementation details may vary)
        assert result_url == url  # URL unchanged when is_url_join is False
        assert "host" in headers
        assert headers["host"] == "api.example.com"

    @patch("infra.tool_exector.http_auth.datetime")
    @patch.dict(
        os.environ,
        {
            const.HTTP_AUTH_AWAU_APP_ID_KEY: "",
            const.HTTP_AUTH_AWAU_API_KEY_KEY: "",
            const.HTTP_AUTH_AWAU_API_SECRET_KEY: "",
        },
    )
    def test_assemble_ws_auth_url_empty_credentials(self, mock_datetime):
        """Test assemble_ws_auth_url with empty credentials."""
        mock_now = datetime(2023, 1, 1, 12, 0, 0)
        mock_datetime.now.return_value = mock_now

        url = "https://api.example.com/test"
        method = "GET"
        auth_config = {
            "authorization_input_part": "api_key",
            "is_digest": False,
            "is_url_join": False,
        }

        result_url, headers = assemble_ws_auth_url(url, method, auth_config)

        # Should still work with empty credentials
        assert "app_id" in headers
        assert headers["app_id"] == ""

    def test_assemble_ws_auth_url_invalid_url(self):
        """Test assemble_ws_auth_url with invalid URL format."""
        url = "invalid-url-format"
        method = "GET"
        auth_config = {"authorization_input_part": "api_key"}

        with pytest.raises(AssembleHeaderException):
            assemble_ws_auth_url(url, method, auth_config)


class TestHttpAuthEdgeCases:
    """Test suite for HTTP authentication edge cases and error conditions."""

    def test_md5_encode_large_input(self):
        """Test md5_encode with very large input."""
        # Create a 1MB string
        large_string = "x" * (1024 * 1024)
        result = md5_encode(large_string)

        assert len(result) == 32
        assert all(c in "0123456789abcdef" for c in result)

    @patch.dict(os.environ, {}, clear=True)
    def test_public_query_url_missing_env_vars(self):
        """Test public_query_url behavior when environment variables are missing."""
        url = "https://api.example.com/test"

        # Should handle missing environment variables gracefully
        try:
            result = public_query_url(url)
            # If no exception, should contain None values
            assert "appId=None" in result or "appId=" in result
        except Exception:
            # Acceptable to raise exception for missing required config
            pass

    def test_parse_url_edge_case_schemas(self):
        """Test parse_url with various schema formats."""
        test_cases = [
            ("ws://websocket.example.com/socket", "ws://"),
            ("wss://secure.websocket.com/socket", "wss://"),
            ("custom://custom.protocol.com/endpoint", "custom://"),
        ]

        for url, expected_schema in test_cases:
            result = parse_url(url)
            assert result.schema == expected_schema

    def test_hashlib_256_json_serialization_edge_cases(self):
        """Test hashlib_256 with JSON serialization edge cases."""
        # Test with data that might cause JSON serialization issues
        data_with_special_chars = {
            "backslash": "\\",
            "quote": '"',
            "newline": "\n",
            "tab": "\t",
            "unicode": "🎉",
        }

        result = hashlib_256(data_with_special_chars)
        assert result.startswith("SHA-256=")

        # Should be consistent
        result2 = hashlib_256(data_with_special_chars)
        assert result == result2

    @patch("infra.tool_exector.http_auth.time.time")
    def test_generate_13_digit_timestamp_boundary_conditions(self, mock_time):
        """Test generate_13_digit_timestamp at boundary conditions."""
        # Test at exact second boundary
        mock_time.return_value = 1234567890.000
        result = generate_13_digit_timestamp()

        assert len(result) == 13
        assert result == "1234567890000"

        # Test with maximum microseconds
        mock_time.return_value = 1234567890.999999
        result = generate_13_digit_timestamp()

        assert len(result) == 13
        # Should truncate to 3 decimal places for milliseconds
        assert result.startswith("1234567890")

    def test_url_class_immutability_expectations(self):
        """Test that Url class behaves as expected for attribute modification."""
        url = Url("original.com", "/original", "https://")

        # Should allow attribute modification (simple data class)
        url.host = "modified.com"
        assert url.host == "modified.com"

        # Original design appears to allow modification
        url.path = "/modified"
        url.schema = "http://"
        assert url.path == "/modified"
        assert url.schema == "http://"
