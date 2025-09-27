"""Test URL utility module."""

from plugin.rpa.utils.urls.utl_util import is_valid_url


class TestIsValidUrl:
    """Test cases for is_valid_url function."""

    def test_valid_http_url(self) -> None:
        """Test valid HTTP URLs."""
        assert is_valid_url("http://example.com") is True
        assert is_valid_url("http://www.example.com") is True
        assert is_valid_url("http://example.com/path") is True
        assert is_valid_url("http://example.com:8080") is True
        assert is_valid_url("http://example.com/path?query=value") is True

    def test_valid_https_url(self) -> None:
        """Test valid HTTPS URLs."""
        assert is_valid_url("https://example.com") is True
        assert is_valid_url("https://www.example.com") is True
        assert is_valid_url("https://example.com/path") is True
        assert is_valid_url("https://example.com:443") is True
        assert (
            is_valid_url("https://example.com/path?query=value&another=param") is True
        )

    def test_valid_ip_url(self) -> None:
        """Test valid IP address URLs."""
        assert is_valid_url("http://192.168.1.1") is True
        assert is_valid_url("https://10.0.0.1:8080") is True
        assert is_valid_url("http://127.0.0.1:3000/api") is True

    def test_valid_localhost_url(self) -> None:
        """Test valid localhost URLs."""
        assert is_valid_url("http://localhost") is True
        assert is_valid_url("https://localhost:8080") is True
        assert is_valid_url("http://localhost:3000/path") is True

    def test_valid_other_schemes(self) -> None:
        """Test other valid protocol schemes."""
        assert is_valid_url("ftp://example.com") is True
        assert is_valid_url("file://localhost/path/to/file") is True
        assert is_valid_url("ws://example.com:8080") is True
        assert is_valid_url("wss://example.com/websocket") is True

    def test_invalid_no_scheme(self) -> None:
        """Test invalid URLs without protocol."""
        assert is_valid_url("example.com") is False
        assert is_valid_url("www.example.com") is False
        assert is_valid_url("example.com/path") is False

    def test_invalid_no_netloc(self) -> None:
        """Test invalid URLs without domain."""
        assert is_valid_url("http://") is False
        assert is_valid_url("https://") is False
        assert is_valid_url("ftp://") is False

    def test_invalid_malformed_urls(self) -> None:
        """Test malformed invalid URLs."""
        assert is_valid_url("http:/") is False
        assert is_valid_url("http:example.com") is False
        assert is_valid_url("://example.com") is False
        assert is_valid_url("http//example.com") is False

    def test_invalid_empty_and_none(self) -> None:
        """Test empty values and None cases."""
        assert is_valid_url("") is False
        assert is_valid_url(None) is False
        assert is_valid_url("   ") is False  # Only spaces

    def test_invalid_special_characters(self) -> None:
        """Test invalid URLs with special characters."""
        # Test some characters that might cause parsing errors
        invalid_urls = ["http://[invalid", "http://example .com"]

        for url in invalid_urls:
            try:
                result = is_valid_url(url)
                # If no exception is thrown, result should be False
                assert result is False, f"URL '{url}' should be invalid"
            except (ValueError, TypeError, AttributeError):
                # If exception is thrown, also consider it invalid, which is expected
                pass

    def test_edge_cases_with_fragments_and_queries(self) -> None:
        """Test edge cases with fragments and query parameters."""
        assert is_valid_url("https://example.com#fragment") is True
        assert is_valid_url("https://example.com/path#fragment") is True
        assert is_valid_url("https://example.com/path?a=1&b=2#frag") is True

    def test_unicode_domains(self) -> None:
        """Test Unicode domains."""
        # Internationalized domain names should be considered valid
        assert is_valid_url("https://例え.テスト") is True
        assert is_valid_url("http://xn--r8jz45g.xn--zckzah") is True  # Punycode

    def test_very_long_urls(self) -> None:
        """Test very long URLs."""
        long_path = "a" * 1000
        long_url = f"https://example.com/{long_path}"
        assert is_valid_url(long_url) is True

    def test_urls_with_authentication(self) -> None:
        """Test URLs with authentication information."""
        assert is_valid_url("https://user:pass@example.com") is True
        assert is_valid_url("ftp://user@example.com") is True

    def test_relative_paths_are_invalid(self) -> None:
        """Test relative paths are considered invalid."""
        assert is_valid_url("/path/to/resource") is False
        assert is_valid_url("../relative/path") is False
        assert is_valid_url("./current/path") is False

    def test_data_urls(self) -> None:
        """Test data URLs."""
        assert (
            is_valid_url("data:text/plain;base64,SGVsbG8=") is False
        )  # data URLs have no netloc

    def test_javascript_urls(self) -> None:
        """Test javascript URLs."""
        assert (
            is_valid_url("javascript:alert('hello')") is False
        )  # javascript URLs have no netloc

    def test_case_insensitive_schemes(self) -> None:
        """Test case insensitive protocols."""
        assert is_valid_url("HTTP://example.com") is True
        assert is_valid_url("HTTPS://example.com") is True
        assert is_valid_url("FTP://example.com") is True
