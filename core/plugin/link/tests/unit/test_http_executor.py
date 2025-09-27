"""
Detailed unit tests for HTTP executor module.

This module provides comprehensive function-level testing for HttpRun class
including authentication methods, security validations, blacklist checking,
and HTTP request execution with proper error handling scenarios.
"""

import os
from unittest.mock import AsyncMock, Mock, patch

import pytest
from plugin.link.consts import const
from plugin.link.exceptions.sparklink_exceptions import CallThirdApiException
from plugin.link.infra.tool_exector.process import HttpRun
from plugin.link.utils.errors.code import ErrCode


class TestHttpRunInit:
    """Test suite for HttpRun initialization."""

    def test_init_with_complete_parameters(self):
        """Test HttpRun initialization with all parameters."""
        server = "https://api.example.com"
        method = "GET"
        path = {"id": "123"}
        query = {"q": "search"}
        header = {"Authorization": "Bearer token"}
        body = {"data": "test"}

        http_run = HttpRun(server, method, path, query, header, body)

        assert http_run.server == server
        assert http_run.method == method
        assert http_run.path == path
        assert http_run.query == query
        assert http_run.header == header
        assert http_run.body == body

    def test_init_with_openapi_schema_md5_auth(self):
        """Test HttpRun initialization with MD5 authentication in OpenAPI schema."""
        openapi_schema = {
            "paths": {
                "/test": {
                    "get": {
                        "parameters": [
                            {
                                "in": "header",
                                "name": "Authorization",
                                "schema": {"default": "MD5"},
                            }
                        ]
                    }
                }
            }
        }

        http_run = HttpRun(
            "https://api.example.com",
            "GET",
            {},
            {},
            {},
            {},
            open_api_schema=openapi_schema,
        )

        assert http_run._is_authorization_md5 is True

    def test_init_with_openapi_schema_official_api(self):
        """Test HttpRun initialization with official API marking."""
        openapi_schema = {"info": {"title": "Official API", "x-is-official": True}}

        http_run = HttpRun(
            "https://api.example.com",
            "GET",
            {},
            {},
            {},
            {},
            open_api_schema=openapi_schema,
        )

        assert http_run._is_official is True

    def test_init_with_exception_handling(self):
        """Test HttpRun initialization gracefully handles exceptions."""
        # Invalid schema that might cause exceptions
        invalid_schema = "invalid_json"

        http_run = HttpRun(
            "https://api.example.com",
            "GET",
            {},
            {},
            {},
            {},
            open_api_schema=invalid_schema,
        )

        # Should default to False when exceptions occur
        assert http_run._is_authorization_md5 is False
        assert http_run._is_auth_hmac is False
        assert http_run._is_official is False
        assert http_run._is_in_blacklist is False


class TestHttpRunDoCall:
    """Test suite for HttpRun do_call method."""

    def setup_aiohttp_mock(
        self, mock_session_class, response_text='{"result": "success"}', status=200
    ):
        """Helper method to set up aiohttp mocks with proper async context managers."""
        # Create mock response
        mock_response = AsyncMock()
        mock_response.text = AsyncMock(return_value=response_text)
        mock_response.status = status

        # Create mock session with proper async context manager protocol
        mock_session = Mock()

        # Mock for session.request() that returns an async context manager
        class MockRequestContextManager:
            def __init__(self, response):
                self.response = response

            async def __aenter__(self):
                return self.response

            async def __aexit__(self, exc_type, exc_val, exc_tb):
                return None

        # Create a Mock that can track calls and also return the context manager
        mock_request_method = Mock()

        def mock_request(*args, **kwargs):
            mock_request_method(*args, **kwargs)  # Track the call
            return MockRequestContextManager(mock_response)

        mock_session.request = mock_request
        mock_session.request.assert_called_once = mock_request_method.assert_called_once

        # Store the mock_request_method for access to call_args
        mock_session._request_mock = mock_request_method

        # Mock for ClientSession() that returns an async context manager
        class MockSessionContextManager:
            def __init__(self, session):
                self.session = session

            async def __aenter__(self):
                return self.session

            async def __aexit__(self, exc_type, exc_val, exc_tb):
                return None

        mock_session_class.return_value = MockSessionContextManager(mock_session)
        return mock_session

    @pytest.fixture
    def mock_span(self):
        """Mock span for tracing."""
        span = Mock()
        span_context = Mock()
        span.start.return_value.__enter__ = Mock(return_value=span_context)
        span.start.return_value.__exit__ = Mock(return_value=None)
        span.add_error_event = Mock()
        return span

    @pytest.fixture
    def basic_http_run(self):
        """Basic HttpRun instance for testing."""
        return HttpRun(
            server="https://api.example.com",
            method="GET",
            path={},
            query={},
            header={},
            body={},
        )

    @pytest.mark.asyncio
    async def test_do_call_success(self, basic_http_run, mock_span):
        """Test successful HTTP call execution."""
        with patch("aiohttp.ClientSession") as mock_session_class:
            self.setup_aiohttp_mock(mock_session_class, '{"result": "success"}', 200)
            result = await basic_http_run.do_call(mock_span)
            assert result == '{"result": "success"}'

    @pytest.mark.asyncio
    async def test_do_call_blacklist_error(self, mock_span):
        """Test do_call raises exception when server is blacklisted."""
        http_run = HttpRun(
            server="https://blacklisted.example.com",
            method="GET",
            path={},
            query={},
            header={},
            body={},
        )
        # Force blacklist status
        http_run._is_in_blacklist = True

        with pytest.raises(CallThirdApiException) as exc_info:
            await http_run.do_call(mock_span)

        assert exc_info.value.code == ErrCode.SERVER_VALIDATE_ERR.code
        assert "黑名单" in str(exc_info.value.message)

    @pytest.mark.asyncio
    async def test_do_call_with_path_construction(self, mock_span):
        """Test do_call with path parameter construction."""
        http_run = HttpRun(
            server="https://api.example.com",
            method="GET",
            path={"endpoint": "/users/123"},
            query={},
            header={},
            body={},
        )

        with patch("aiohttp.ClientSession") as mock_session_class:
            mock_session = self.setup_aiohttp_mock(
                mock_session_class, '{"user": "data"}', 200
            )

            _ = await http_run.do_call(mock_span)

            # Verify URL construction worked
            mock_session.request.assert_called_once()
            call_args = mock_session._request_mock.call_args
            assert "/users/123" in call_args[0][1]  # URL should contain path

    @pytest.mark.asyncio
    async def test_do_call_with_md5_authentication(self, mock_span):
        """Test do_call with MD5 authentication flow."""
        http_run = HttpRun(
            server="https://api.example.com",
            method="GET",
            path={},
            query={"param": "value"},
            header={},
            body={},
        )
        http_run._is_authorization_md5 = True

        with patch(
            "infra.tool_exector.process.public_query_url"
        ) as mock_public_query, patch("aiohttp.ClientSession") as mock_session_class:

            mock_public_query.return_value = "https://api.example.com?auth=md5"
            _ = self.setup_aiohttp_mock(
                mock_session_class, '{"authenticated": true}', 200
            )

            result = await http_run.do_call(mock_span)

            mock_public_query.assert_called_once()
            assert result == '{"authenticated": true}'

    @pytest.mark.asyncio
    async def test_do_call_with_hmac_authentication(self, mock_span):
        """Test do_call with HMAC authentication flow."""
        http_run = HttpRun(
            server="https://api.example.com",
            method="POST",
            path={},
            query={},
            header={"Authorization": 'HMAC: {"key": "value"}'},
            body={"data": "test"},
        )
        http_run._is_auth_hmac = True
        http_run.auth_con_js = {"key": "value"}

        with patch(
            "infra.tool_exector.process.assemble_ws_auth_url"
        ) as mock_assemble_auth, patch("aiohttp.ClientSession") as mock_session_class:

            mock_assemble_auth.return_value = (
                "https://api.example.com?hmac=auth",
                {"Auth": "hmac"},
            )
            _ = self.setup_aiohttp_mock(
                mock_session_class, '{"hmac_authenticated": true}', 200
            )

            result = await http_run.do_call(mock_span)

            mock_assemble_auth.assert_called_once()
            assert result == '{"hmac_authenticated": true}'

    @pytest.mark.asyncio
    async def test_do_call_with_query_parameters(self, mock_span):
        """Test do_call with standard query parameters."""
        http_run = HttpRun(
            server="https://api.example.com",
            method="GET",
            path={},
            query={"search": "test", "limit": "10"},
            header={},
            body={},
        )

        with patch("aiohttp.ClientSession") as mock_session_class:
            mock_session = self.setup_aiohttp_mock(
                mock_session_class, '{"results": []}', 200
            )

            _ = await http_run.do_call(mock_span)

            # Verify URL contains query parameters
            call_args = mock_session._request_mock.call_args
            url = call_args[0][1]
            assert "search=test" in url
            assert "limit=10" in url

    @pytest.mark.asyncio
    async def test_do_call_http_error_non_official(self, basic_http_run, mock_span):
        """Test do_call HTTP error handling for non-official API."""
        with patch("aiohttp.ClientSession") as mock_session_class:
            self.setup_aiohttp_mock(mock_session_class, "Internal Server Error", 500)

            with pytest.raises(CallThirdApiException) as exc_info:
                await basic_http_run.do_call(mock_span)

            assert exc_info.value.code == ErrCode.THIRD_API_REQUEST_FAILED_ERR.code
            assert "500" in str(exc_info.value.message)

    @pytest.mark.asyncio
    async def test_do_call_http_error_official_api(self, mock_span):
        """Test do_call HTTP error handling for official API."""
        http_run = HttpRun(
            server="https://api.example.com",
            method="GET",
            path={},
            query={},
            header={},
            body={},
        )
        http_run._is_official = True

        with patch("aiohttp.ClientSession") as mock_session_class:
            mock_session = AsyncMock()
            mock_response = AsyncMock()
            mock_response.text.return_value = "Official API Error"
            mock_response.status = 400

            mock_session.request.return_value.__aenter__ = AsyncMock(
                return_value=mock_response
            )
            mock_session.request.return_value.__aexit__ = AsyncMock(return_value=None)
            mock_session_class.return_value.__aenter__ = AsyncMock(
                return_value=mock_session
            )
            mock_session_class.return_value.__aexit__ = AsyncMock(return_value=None)

            with pytest.raises(CallThirdApiException) as exc_info:
                await http_run.do_call(mock_span)

            assert exc_info.value.code == ErrCode.OFFICIAL_API_REQUEST_FAILED_ERR.code

    @pytest.mark.asyncio
    async def test_do_call_network_exception_non_official(
        self, basic_http_run, mock_span
    ):
        """Test do_call network exception handling for non-official API."""
        with patch("aiohttp.ClientSession") as mock_session_class:
            mock_session = AsyncMock()
            mock_session.request.side_effect = Exception("Connection timeout")
            mock_session_class.return_value.__aenter__ = AsyncMock(
                return_value=mock_session
            )
            mock_session_class.return_value.__aexit__ = AsyncMock(return_value=None)

            with pytest.raises(CallThirdApiException) as exc_info:
                await basic_http_run.do_call(mock_span)

            assert exc_info.value.code == ErrCode.THIRD_API_REQUEST_FAILED_ERR.code
            mock_span.add_error_event.assert_called_once()

    @pytest.mark.asyncio
    async def test_do_call_network_exception_official_api(self, mock_span):
        """Test do_call network exception handling for official API."""
        http_run = HttpRun(
            server="https://api.example.com",
            method="GET",
            path={},
            query={},
            header={},
            body={},
        )
        http_run._is_official = True

        with patch("aiohttp.ClientSession") as mock_session_class:
            mock_session = AsyncMock()
            mock_session.request.side_effect = Exception("Official API timeout")
            mock_session_class.return_value.__aenter__ = AsyncMock(
                return_value=mock_session
            )
            mock_session_class.return_value.__aexit__ = AsyncMock(return_value=None)

            with pytest.raises(CallThirdApiException) as exc_info:
                await http_run.do_call(mock_span)

            assert exc_info.value.code == ErrCode.OFFICIAL_API_REQUEST_FAILED_ERR.code

    @pytest.mark.asyncio
    async def test_do_call_header_cleanup(self, mock_span):
        """Test do_call removes @type from headers."""
        http_run = HttpRun(
            server="https://api.example.com",
            method="GET",
            path={},
            query={},
            header={"@type": "application/json", "Authorization": "Bearer token"},
            body={},
        )

        with patch("aiohttp.ClientSession") as mock_session_class:
            self.setup_aiohttp_mock(mock_session_class, '{"clean_headers": true}', 200)

            await http_run.do_call(mock_span)

            # Verify @type was removed from headers
            assert "@type" not in http_run.header
            assert "Authorization" in http_run.header


class TestHttpRunStaticMethods:
    """Test suite for HttpRun static methods."""

    def test_is_authorization_md5_true(self):
        """Test is_authorization_md5 returns True for MD5 auth schema."""
        schema = {
            "paths": {
                "/api/test": {
                    "get": {
                        "parameters": [
                            {
                                "in": "header",
                                "name": "Authorization",
                                "schema": {"default": "MD5"},
                            }
                        ]
                    }
                }
            }
        }

        result = HttpRun.is_authorization_md5(schema)
        assert result is True

    def test_is_authorization_md5_false_no_md5(self):
        """Test is_authorization_md5 returns False when no MD5 auth."""
        schema = {
            "paths": {
                "/api/test": {
                    "get": {
                        "parameters": [
                            {
                                "in": "header",
                                "name": "Authorization",
                                "schema": {"default": "Bearer"},
                            }
                        ]
                    }
                }
            }
        }

        result = HttpRun.is_authorization_md5(schema)
        assert result is False

    def test_is_authorization_md5_false_no_schema(self):
        """Test is_authorization_md5 returns False for None schema."""
        result = HttpRun.is_authorization_md5(None)
        assert result is False

    def test_is_authorization_md5_false_empty_schema(self):
        """Test is_authorization_md5 returns False for empty schema."""
        result = HttpRun.is_authorization_md5({})
        assert result is False

    def test_is_authorization_hmac_true(self):
        """Test is_authorization_hmac returns True for HMAC auth."""
        header = {"Authorization": 'HMAC: {"key": "value", "secret": "test"}'}

        is_hmac, auth_config = HttpRun.is_authorization_hmac(header)

        assert is_hmac is True
        assert auth_config["key"] == "value"
        assert auth_config["secret"] == "test"

    def test_is_authorization_hmac_false_no_hmac(self):
        """Test is_authorization_hmac returns False for non-HMAC auth."""
        header = {"Authorization": "Bearer token123"}

        is_hmac, auth_config = HttpRun.is_authorization_hmac(header)

        assert is_hmac is False
        assert auth_config == object

    def test_is_authorization_hmac_false_empty_header(self):
        """Test is_authorization_hmac returns False for empty header."""
        is_hmac, auth_config = HttpRun.is_authorization_hmac({})

        assert is_hmac is False
        assert auth_config == object

    def test_is_authorization_hmac_false_no_header(self):
        """Test is_authorization_hmac returns False for None header."""
        is_hmac, auth_config = HttpRun.is_authorization_hmac(None)

        assert is_hmac is False
        assert auth_config == object

    def test_is_official_true(self):
        """Test is_official returns True for official API."""
        schema = {
            "info": {"title": "Official API", "version": "1.0.0", "x-is-official": True}
        }

        result = HttpRun.is_official(schema)
        assert result is True

    def test_is_official_false_not_official(self):
        """Test is_official returns False when not marked official."""
        schema = {
            "info": {
                "title": "Third Party API",
                "version": "1.0.0",
                "x-is-official": False,
            }
        }

        result = HttpRun.is_official(schema)
        assert result is False

    def test_is_official_false_no_flag(self):
        """Test is_official returns False when no official flag."""
        schema = {"info": {"title": "Regular API", "version": "1.0.0"}}

        result = HttpRun.is_official(schema)
        assert result is False

    def test_is_official_false_no_schema(self):
        """Test is_official returns False for None schema."""
        result = HttpRun.is_official(None)
        assert result is False


class TestHttpRunBlacklistMethods:
    """Test suite for HttpRun blacklist validation methods."""

    @patch.dict(os.environ, {const.DOMAIN_BLACK_LIST_KEY: "evil.com,malicious.net"})
    def test_is_in_black_domain_true(self):
        """Test is_in_black_domain returns True for blacklisted domain."""
        url = "https://evil.com/api/test"

        result = HttpRun.is_in_black_domain(url)
        assert result is True

    @patch.dict(os.environ, {const.DOMAIN_BLACK_LIST_KEY: "evil.com,malicious.net"})
    def test_is_in_black_domain_false_safe_domain(self):
        """Test is_in_black_domain returns False for safe domain."""
        url = "https://safe.example.com/api/test"

        result = HttpRun.is_in_black_domain(url)
        assert result is False

    @patch.dict(os.environ, {const.DOMAIN_BLACK_LIST_KEY: ""})
    def test_is_in_black_domain_false_empty_blacklist(self):
        """Test is_in_black_domain returns False when blacklist is empty."""
        url = "https://any.domain.com/api/test"

        result = HttpRun.is_in_black_domain(url)
        assert result is False

    def test_is_in_black_domain_false_no_env_var(self):
        """Test is_in_black_domain returns False when env var not set."""
        with patch.dict(os.environ, {}, clear=True):
            url = "https://any.domain.com/api/test"

            result = HttpRun.is_in_black_domain(url)
            assert result is False

    @patch.dict(os.environ, {const.DOMAIN_BLACK_LIST_KEY: "EVIL.COM,Malicious.NET"})
    def test_is_in_black_domain_case_insensitive(self):
        """Test is_in_black_domain is case insensitive."""
        url = "https://evil.com/api/test"

        result = HttpRun.is_in_black_domain(url)
        assert result is True

    @patch.dict(
        os.environ,
        {
            const.SEGMENT_BLACK_LIST_KEY: "192.168.1.0/24,10.0.0.0/8",
            const.IP_BLACK_LIST_KEY: "127.0.0.1,192.168.1.100",
        },
    )
    def test_is_in_blacklist_domain_check(self):
        """Test is_in_blacklist performs domain blacklist check first."""
        with patch.object(HttpRun, "is_in_black_domain", return_value=True):
            url = "https://evil.com/api/test"

            result = HttpRun.is_in_blacklist(url)
            assert result is True

    @patch.dict(
        os.environ,
        {
            const.SEGMENT_BLACK_LIST_KEY: "192.168.1.0/24,10.0.0.0/8",
            const.IP_BLACK_LIST_KEY: "127.0.0.1,192.168.1.100",
        },
    )
    def test_is_in_blacklist_ip_blacklist(self):
        """Test is_in_blacklist checks IP blacklist."""
        with patch.object(HttpRun, "is_in_black_domain", return_value=False):
            url = "https://127.0.0.1:8080/api/test"

            result = HttpRun.is_in_blacklist(url)
            assert result is True

    @patch.dict(
        os.environ,
        {
            const.SEGMENT_BLACK_LIST_KEY: "192.168.1.0/24,10.0.0.0/8",
            const.IP_BLACK_LIST_KEY: "127.0.0.1,192.168.1.100",
        },
    )
    def test_is_in_blacklist_network_segment(self):
        """Test is_in_blacklist checks network segment blacklist."""
        with patch.object(HttpRun, "is_in_black_domain", return_value=False):
            url = "https://192.168.1.50:8080/api/test"

            result = HttpRun.is_in_blacklist(url)
            assert result is True

    @patch.dict(
        os.environ,
        {
            const.SEGMENT_BLACK_LIST_KEY: "192.168.1.0/24,10.0.0.0/8",
            const.IP_BLACK_LIST_KEY: "127.0.0.1,192.168.1.100",
        },
    )
    def test_is_in_blacklist_false_safe_ip(self):
        """Test is_in_blacklist returns False for safe IP."""
        with patch.object(HttpRun, "is_in_black_domain", return_value=False):
            url = "https://8.8.8.8:443/api/test"

            result = HttpRun.is_in_blacklist(url)
            assert result is False

    @patch.dict(
        os.environ,
        {
            const.SEGMENT_BLACK_LIST_KEY: "192.168.1.0/24",
            const.IP_BLACK_LIST_KEY: "127.0.0.1",
        },
    )
    def test_is_in_blacklist_with_port(self):
        """Test is_in_blacklist handles URLs with ports correctly."""
        with patch.object(HttpRun, "is_in_black_domain", return_value=False):
            url = "https://127.0.0.1:8080/api/test"

            result = HttpRun.is_in_blacklist(url)
            assert result is True

    @patch.dict(
        os.environ,
        {
            const.SEGMENT_BLACK_LIST_KEY: "192.168.1.0/24",
            const.IP_BLACK_LIST_KEY: "127.0.0.1",
        },
    )
    def test_is_in_blacklist_invalid_ip_format(self):
        """Test is_in_blacklist handles invalid IP format gracefully."""
        with patch.object(HttpRun, "is_in_black_domain", return_value=False):
            url = "https://not-an-ip/api/test"

            result = HttpRun.is_in_blacklist(url)
            assert result is False

    @patch.dict(
        os.environ,
        {
            const.SEGMENT_BLACK_LIST_KEY: "192.168.1.0/24",
            const.IP_BLACK_LIST_KEY: "127.0.0.1",
        },
    )
    def test_is_in_blacklist_empty_url(self):
        """Test is_in_blacklist handles empty URL."""
        with patch.object(HttpRun, "is_in_black_domain", return_value=False):
            result = HttpRun.is_in_blacklist("")
            assert result is False

    @patch.dict(
        os.environ,
        {
            const.SEGMENT_BLACK_LIST_KEY: "192.168.1.0/24",
            const.IP_BLACK_LIST_KEY: "127.0.0.1",
        },
    )
    def test_is_in_blacklist_malformed_url(self):
        """Test is_in_blacklist handles malformed URL."""
        with patch.object(HttpRun, "is_in_black_domain", return_value=False):
            url = "not-a-valid-url"

            result = HttpRun.is_in_blacklist(url)
            assert result is False


class TestHttpRunEdgeCases:
    """Test suite for HttpRun edge cases and boundary conditions."""

    def test_is_authorization_hmac_malformed_json(self):
        """Test is_authorization_hmac handles malformed JSON gracefully."""
        header = {"Authorization": "HMAC: invalid-json-format"}

        # This should handle the exception gracefully
        is_hmac, auth_config = HttpRun.is_authorization_hmac(header)

        # Depending on implementation, this might return False or raise
        # We test that it doesn't crash the application
        assert isinstance(is_hmac, bool)

    def test_is_authorization_hmac_missing_colon(self):
        """Test is_authorization_hmac handles missing colon separator."""
        header = {"Authorization": "HMAC-no-colon"}

        # This should handle the exception gracefully
        try:
            is_hmac, auth_config = HttpRun.is_authorization_hmac(header)
            assert is_hmac is False
        except ValueError:
            # Acceptable to raise ValueError for malformed format
            pass

    def test_is_authorization_hmac_empty_authorization(self):
        """Test is_authorization_hmac handles empty authorization value."""
        header = {"Authorization": ""}

        is_hmac, auth_config = HttpRun.is_authorization_hmac(header)

        assert is_hmac is False
        assert auth_config == object

    @patch.dict(
        os.environ,
        {
            const.SEGMENT_BLACK_LIST_KEY: "invalid-network-format",
            const.IP_BLACK_LIST_KEY: "127.0.0.1",
        },
    )
    def test_is_in_blacklist_invalid_network_format(self):
        """Test is_in_blacklist handles invalid network format in env var."""
        with patch.object(HttpRun, "is_in_black_domain", return_value=False):
            url = "https://8.8.8.8/api/test"

            # Should handle invalid network format gracefully
            try:
                result = HttpRun.is_in_blacklist(url)
                # If no exception, result should be boolean
                assert isinstance(result, bool)
            except Exception:
                # Acceptable to raise exception for invalid config
                pass

    def test_init_with_complex_nested_exceptions(self):
        """Test HttpRun init handles complex nested exceptions."""

        # Create scenario that might cause nested exceptions
        class ExceptionRaisingDict(dict):
            def get(self, key, default=None):
                raise Exception("Nested exception in schema access")

        complex_schema = ExceptionRaisingDict()

        # Should not crash despite nested exceptions
        http_run = HttpRun(
            "https://api.example.com",
            "GET",
            {},
            {},
            {},
            {},
            open_api_schema=complex_schema,
        )

        # All flags should default to False
        assert http_run._is_authorization_md5 is False
        assert http_run._is_auth_hmac is False
        assert http_run._is_official is False
        assert http_run._is_in_blacklist is False
