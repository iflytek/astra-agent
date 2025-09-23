"""应用认证模块单元test."""

import base64
import datetime
import json
from unittest.mock import AsyncMock, MagicMock, Mock, patch

import pytest

from exceptions.middleware_exc import MiddlewareExc
from infra.app_auth import APPAuth, AuthConfig, MaasAuth, hashlib_256, http_date


class TestHttpDate:
    """http_date函数test类."""

    def test_http_date_format(self) -> None:
        """testHTTP日期格式化."""
        # Create fixed time
        test_date = datetime.datetime(2023, 12, 25, 15, 30, 45)

        result = http_date(test_date)

        expected = "Mon, 25 Dec 2023 15:30:45 GMT"
        assert result == expected

    def test_http_date_different_weekdays(self) -> None:
        """test不同星期的日期格式化."""
        test_cases = [
            (datetime.datetime(2023, 12, 25, 0, 0, 0), "Mon"),  # Monday
            (datetime.datetime(2023, 12, 26, 0, 0, 0), "Tue"),  # Tuesday
            (datetime.datetime(2023, 12, 31, 0, 0, 0), "Sun"),  # Sunday
        ]

        for test_date, expected_weekday in test_cases:
            result = http_date(test_date)
            assert result.startswith(expected_weekday)

    def test_http_date_all_months(self) -> None:
        """test所有月份的格式化."""
        months = [
            "Jan",
            "Feb",
            "Mar",
            "Apr",
            "May",
            "Jun",
            "Jul",
            "Aug",
            "Sep",
            "Oct",
            "Nov",
            "Dec",
        ]

        for i, expected_month in enumerate(months, 1):
            test_date = datetime.datetime(2023, i, 1, 0, 0, 0)
            result = http_date(test_date)
            assert expected_month in result

    def test_http_date_zero_padding(self) -> None:
        """test日期零填充."""
        # Test zero-padding for date, hour, minute, second
        test_date = datetime.datetime(2023, 1, 5, 8, 9, 7)
        result = http_date(test_date)

        assert "05 Jan" in result  # Date zero-padding
        assert "08:09:07" in result  # Time zero-padding

    def test_http_date_leap_year(self) -> None:
        """test闰年处理."""
        leap_year_date = datetime.datetime(2024, 2, 29, 12, 0, 0)
        result = http_date(leap_year_date)

        assert "29 Feb 2024" in result
        assert "Thu" in result  # February 29, 2024 is Thursday

    def test_http_date_edge_cases(self) -> None:
        """test边缘情况."""
        # Beginning of year
        new_year = datetime.datetime(2023, 1, 1, 0, 0, 0)
        result_new_year = http_date(new_year)
        assert "01 Jan 2023 00:00:00 GMT" in result_new_year

        # End of year
        year_end = datetime.datetime(2023, 12, 31, 23, 59, 59)
        result_year_end = http_date(year_end)
        assert "31 Dec 2023 23:59:59 GMT" in result_year_end


class TestHashlib256:
    """hashlib_256函数test类."""

    def test_hashlib_256_basic(self) -> None:
        """test基本哈希生成."""
        test_string = "test_data"
        result = hashlib_256(test_string)

        # Verify return format
        assert result.startswith("SHA256=")
        assert len(result) > 10  # Basic length validation

    def test_hashlib_256_empty_string(self) -> None:
        """test空字符串哈希."""
        result = hashlib_256("")

        assert result.startswith("SHA256=")
        # Empty string SHA256 should be fixed value
        expected = "SHA256=47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="
        assert result == expected

    def test_hashlib_256_unicode(self) -> None:
        """testUnicode字符串哈希."""
        unicode_string = "test中文🚀"
        result = hashlib_256(unicode_string)

        assert result.startswith("SHA256=")
        assert len(result) > 10

    def test_hashlib_256_consistency(self) -> None:
        """test哈希一致性."""
        test_string = "consistency_test"

        result1 = hashlib_256(test_string)
        result2 = hashlib_256(test_string)

        assert result1 == result2

    def test_hashlib_256_different_inputs(self) -> None:
        """test不同输入产生不同哈希."""
        input1 = "input1"
        input2 = "input2"

        hash1 = hashlib_256(input1)
        hash2 = hashlib_256(input2)

        assert hash1 != hash2
        assert hash1.startswith("SHA256=")
        assert hash2.startswith("SHA256=")

    def test_hashlib_256_base64_format(self) -> None:
        """testBase64格式正确性."""
        test_string = "test_base64_format"
        result = hashlib_256(test_string)

        # Remove SHA256= prefix
        base64_part = result[7:]  # Remove "SHA256="

        # Base64 encoded string length should be multiple of 4
        assert len(base64_part) % 4 == 0

        # Base64 contains only valid characters
        try:
            decoded = base64.b64decode(base64_part)
            assert len(decoded) == 32  # SHA256 hash length is 32 bytes
        except (ValueError, TypeError):
            pytest.fail("Invalid base64 format")

    def test_hashlib_256_json_data(self) -> None:
        """testJSON数据哈希."""
        json_data = {"key": "value", "number": 123, "nested": {"a": 1}}
        json_string = json.dumps(json_data, sort_keys=True, ensure_ascii=False)

        result = hashlib_256(json_string)

        assert result.startswith("SHA256=")
        # JSON data should produce consistent hash
        result2 = hashlib_256(json_string)
        assert result == result2


class TestAuthConfig:
    """AuthConfigtest类."""

    def test_auth_config_creation(self) -> None:
        """test认证配置创建."""
        config = AuthConfig(
            host="example.com",
            route="/api/auth",
            prot="https",
            api_key="test_key",
            secret="test_secret",
        )

        assert config.host == "example.com"
        assert config.route == "/api/auth"
        assert config.prot == "https"
        assert config.api_key == "test_key"
        assert config.secret == "test_secret"
        assert config.method == "GET"  # Default value
        assert config.algorithm == "hmac-sha256"  # Default value
        assert config.http_proto == "HTTP/1.1"  # Default value

    def test_auth_config_url_property(self) -> None:
        """testURL属性生成."""
        config = AuthConfig(
            host="api.example.com",
            route="/v1/auth",
            prot="https",
            api_key="key",
            secret="secret",
        )

        expected_url = "https://api.example.com/v1/auth"
        assert config.url == expected_url

    def test_auth_config_custom_method(self) -> None:
        """test自定义HTTP方法."""
        config = AuthConfig(
            host="example.com",
            route="/auth",
            prot="http",
            api_key="key",
            secret="secret",
            method="POST",
        )

        assert config.method == "POST"

    def test_auth_config_custom_algorithm(self) -> None:
        """test自定义算法."""
        config = AuthConfig(
            host="example.com",
            route="/auth",
            prot="https",
            api_key="key",
            secret="secret",
            algorithm="hmac-sha512",
        )

        assert config.algorithm == "hmac-sha512"

    def test_auth_config_url_edge_cases(self) -> None:
        """testURL边缘情况."""
        # Route without leading slash
        config1 = AuthConfig(
            host="example.com",
            route="auth",
            prot="https",
            api_key="key",
            secret="secret",
        )
        assert config1.url == "https://example.comauth"

        # Empty route
        config2 = AuthConfig(
            host="example.com", route="", prot="https", api_key="key", secret="secret"
        )
        assert config2.url == "https://example.com"


class TestAPPAuth:
    """APPAuthtest类."""

    def setup_method(self) -> None:
        """Test setup method."""
        # pylint: disable=attribute-defined-outside-init
        # Mock agent_config values for testing
        with patch("infra.app_auth.agent_config") as mock_config:
            mock_config.app_auth_host = "test.host.com"
            mock_config.app_auth_router = "/auth"
            mock_config.app_auth_prot = "https"
            mock_config.app_auth_api_key = "test_api_key"
            mock_config.app_auth_secret = "test_secret"

            self.app_auth = APPAuth()
            self.config = self.app_auth.config

    def test_auth_config_initialization(self) -> None:
        """test配置初始化."""
        assert self.config.host == "test.host.com"
        assert self.config.route == "/auth"
        assert self.config.prot == "https"
        assert self.config.api_key == "test_api_key"
        assert self.config.secret == "test_secret"
        assert isinstance(self.app_auth.date, str)

    def test_date_format_validation(self) -> None:
        """test日期格式验证."""
        # Date should conform to HTTP date format
        date_parts = self.app_auth.date.split()
        assert len(date_parts) == 6  # "Mon, 25 Dec 2023 15:30:45 GMT"
        assert date_parts[5] == "GMT"
        assert date_parts[0].endswith(",")

    def test_generate_signature(self) -> None:
        """test签名生成."""
        test_digest = "test_digest_value"

        # Mock fixed time
        with patch.object(self.app_auth, "date", "Mon, 25 Dec 2023 15:30:45 GMT"):
            signature = self.app_auth.generate_signature(test_digest)

            # Verify signature is not empty and is base64 format
            assert len(signature) > 0
            assert isinstance(signature, str)

            # Verify Base64 format
            try:
                base64.b64decode(signature)
            except (ValueError, TypeError):
                pytest.fail("Invalid base64 signature")

    def test_generate_signature_consistency(self) -> None:
        """test签名生成一致性."""
        test_digest = "consistent_digest"

        with patch.object(self.app_auth, "date", "Mon, 25 Dec 2023 15:30:45 GMT"):
            signature1 = self.app_auth.generate_signature(test_digest)
            signature2 = self.app_auth.generate_signature(test_digest)

            assert signature1 == signature2

    def test_generate_signature_different_digests(self) -> None:
        """test不同摘要产生不同签名."""
        digest1 = "digest1"
        digest2 = "digest2"

        with patch.object(self.app_auth, "date", "Mon, 25 Dec 2023 15:30:45 GMT"):
            signature1 = self.app_auth.generate_signature(digest1)
            signature2 = self.app_auth.generate_signature(digest2)

            assert signature1 != signature2

    def test_init_header(self) -> None:
        """test请求头初始化."""
        test_data = '{"test": "data"}'

        headers = self.app_auth.init_header(test_data)

        # Verify required request headers
        required_headers = [
            "Content-Type",
            "Authorization",
            "Digest",
            "Date",
            "Host",
            "Accept",
            "Method",
        ]
        for header in required_headers:
            assert header in headers

        assert headers["Content-Type"] == "application/json"
        assert headers["Accept"] == "application/json"
        assert headers["Method"] == "GET"
        assert headers["Host"] == "test.host.com"

    def test_init_header_digest_format(self) -> None:
        """test摘要格式."""
        test_data = '{"key": "value"}'

        headers = self.app_auth.init_header(test_data)

        # Verify digest format
        digest = headers["Digest"]
        assert digest.startswith("SHA256=")

    def test_init_header_authorization_format(self) -> None:
        """test认证头格式."""
        test_data = '{"test": "data"}'

        headers = self.app_auth.init_header(test_data)

        auth_header = headers["Authorization"]

        # Verify auth header contains necessary components
        assert 'api_key="test_api_key"' in auth_header
        assert 'algorithm="hmac-sha256"' in auth_header
        assert 'headers="host date request-line digest"' in auth_header
        assert "signature=" in auth_header

    def test_init_header_unicode_data(self) -> None:
        """testUnicode数据处理."""
        unicode_data = '{"message": "test消息🚀", "value": 123}'

        headers = self.app_auth.init_header(unicode_data)

        # Should handle Unicode data correctly
        assert "Digest" in headers
        assert headers["Digest"].startswith("SHA256=")

    @pytest.mark.asyncio
    async def test_app_detail_success(self) -> None:
        """test成功获取应用详情."""
        app_id = "test_app_id"
        expected_response = {"code": 0, "data": "test_data"}

        # Mock response object
        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.json.return_value = expected_response
        mock_response.raise_for_status = AsyncMock()

        # Mock aiohttp ClientSession
        mock_session = MagicMock()
        mock_session.get = MagicMock()
        mock_session.get.return_value.__aenter__ = AsyncMock(return_value=mock_response)
        mock_session.get.return_value.__aexit__ = AsyncMock(return_value=None)
        mock_session.__aenter__ = AsyncMock(return_value=mock_session)
        mock_session.__aexit__ = AsyncMock(return_value=None)

        with patch("infra.app_auth.aiohttp.ClientSession", return_value=mock_session):
            result = await self.app_auth.app_detail(app_id)

            assert result == expected_response
            # Verify request parameters
            call_kwargs = mock_session.get.call_args[1]
            assert call_kwargs["params"]["app_ids"] == "test_app_id,"

    @pytest.mark.asyncio
    async def test_app_detail_non_200_status(self) -> None:
        """test非200状态码处理."""
        app_id = "test_app_id"

        # Mock response object
        mock_response = AsyncMock()
        mock_response.status = 404
        mock_response.raise_for_status = AsyncMock()

        # Mock aiohttp ClientSession
        mock_session = MagicMock()
        mock_session.get = MagicMock()
        mock_session.get.return_value.__aenter__ = AsyncMock(return_value=mock_response)
        mock_session.get.return_value.__aexit__ = AsyncMock(return_value=None)
        mock_session.__aenter__ = AsyncMock(return_value=mock_session)
        mock_session.__aexit__ = AsyncMock(return_value=None)

        with patch("infra.app_auth.aiohttp.ClientSession", return_value=mock_session):
            with pytest.raises(MiddlewareExc, match="response code is 404"):
                await self.app_auth.app_detail(app_id)

    @pytest.mark.asyncio
    async def test_app_detail_request_timeout(self) -> None:
        """test请求超时处理."""
        app_id = "test_app_id"

        # Mock aiohttp ClientSession
        mock_session = MagicMock()
        mock_session.get.side_effect = Exception("Timeout")
        mock_session.__aenter__ = AsyncMock(return_value=mock_session)
        mock_session.__aexit__ = AsyncMock(return_value=None)

        with patch("infra.app_auth.aiohttp.ClientSession", return_value=mock_session):
            with pytest.raises(Exception, match="Timeout"):
                await self.app_auth.app_detail(app_id)

    @pytest.mark.asyncio
    async def test_app_detail_timeout_configuration(self) -> None:
        """test超时配置."""
        app_id = "test_app_id"
        expected_response = {"code": 0, "data": "test_data"}

        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.json.return_value = expected_response
        mock_response.raise_for_status = AsyncMock()

        mock_session = MagicMock()
        mock_session.get = MagicMock()
        mock_session.get.return_value.__aenter__ = AsyncMock(return_value=mock_response)
        mock_session.get.return_value.__aexit__ = AsyncMock(return_value=None)
        mock_session.__aenter__ = AsyncMock(return_value=mock_session)
        mock_session.__aexit__ = AsyncMock(return_value=None)

        with (
            patch("infra.app_auth.aiohttp.ClientSession", return_value=mock_session),
            patch("infra.app_auth.aiohttp.ClientTimeout") as mock_timeout,
        ):

            await self.app_auth.app_detail(app_id)

            # Verify timeout settings
            mock_timeout.assert_called_once_with(total=3)

    @pytest.mark.asyncio
    async def test_app_detail_raise_for_status(self) -> None:
        """testHTTP状态检查."""
        app_id = "test_app_id"

        mock_response = AsyncMock()
        mock_response.status = 500
        mock_response.raise_for_status.side_effect = Exception("HTTP Error 500")

        mock_session = MagicMock()
        mock_session.get = MagicMock()
        mock_session.get.return_value.__aenter__ = AsyncMock(return_value=mock_response)
        mock_session.get.return_value.__aexit__ = AsyncMock(return_value=None)
        mock_session.__aenter__ = AsyncMock(return_value=mock_session)
        mock_session.__aexit__ = AsyncMock(return_value=None)

        with patch("infra.app_auth.aiohttp.ClientSession", return_value=mock_session):
            with pytest.raises(Exception):
                await self.app_auth.app_detail(app_id)

    @pytest.mark.asyncio
    async def test_app_detail_multiple_app_ids(self) -> None:
        """test多个应用ID处理."""
        app_id = "app1,app2,app3"
        expected_response = {"code": 0, "data": ["data1", "data2", "data3"]}

        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.json.return_value = expected_response
        mock_response.raise_for_status = AsyncMock()

        mock_session = MagicMock()
        mock_session.get = MagicMock()
        mock_session.get.return_value.__aenter__ = AsyncMock(return_value=mock_response)
        mock_session.get.return_value.__aexit__ = AsyncMock(return_value=None)
        mock_session.__aenter__ = AsyncMock(return_value=mock_session)
        mock_session.__aexit__ = AsyncMock(return_value=None)

        with patch("infra.app_auth.aiohttp.ClientSession", return_value=mock_session):
            result = await self.app_auth.app_detail(app_id)

            assert result == expected_response
            # Verify parameter format
            call_kwargs = mock_session.get.call_args[1]
            assert call_kwargs["params"]["app_ids"] == "app1,app2,app3,"


class TestMaasAuth:
    """MaasAuthtest类."""

    def setup_method(self) -> None:
        """Test setup method."""
        # pylint: disable=attribute-defined-outside-init
        self.maas_auth = MaasAuth(app_id="test_app_id", model_name="test_model")

    def test_maas_auth_initialization(self) -> None:
        """testMaasAuth初始化."""
        assert self.maas_auth.app_id == "test_app_id"
        assert self.maas_auth.model_name == "test_model"
        assert (
            self.maas_auth.app_id_not_found_msg
            == "Cannot find appid authentication information"
        )

    def test_maas_auth_custom_error_message(self) -> None:
        """test自定义错误消息."""
        custom_msg = "自定义错误消息"
        auth = MaasAuth(
            app_id="test_app", model_name="test_model", app_id_not_found_msg=custom_msg
        )

        assert auth.app_id_not_found_msg == custom_msg

    def test_maas_auth_unicode_support(self) -> None:
        """testUnicode支持."""
        unicode_auth = MaasAuth(app_id="中文应用ID", model_name="中文模型名称🚀")

        assert unicode_auth.app_id == "中文应用ID"
        assert unicode_auth.model_name == "中文模型名称🚀"

    @pytest.mark.asyncio
    async def test_sk_dev_mode_x1_model(self) -> None:
        """test开发模式X1模型."""
        mock_span = Mock()
        mock_span.start = Mock()
        mock_span.start.return_value.__enter__ = Mock()
        mock_span.start.return_value.__exit__ = Mock()
        mock_sub_span = Mock()
        mock_sub_span.add_info_events = Mock()
        mock_span.start.return_value.__enter__.return_value = mock_sub_span

        with patch("infra.app_auth.agent_config") as mock_config:
            mock_config.is_dev.return_value = True
            mock_config.spark_x1_model_name = "test_model"
            mock_config.spark_x1_model_sk = "x1_test_sk"

            result = await self.maas_auth.sk(mock_span)

            assert result == "x1_test_sk"
            mock_sub_span.add_info_events.assert_called_once_with(
                {"x1-default-sk": "x1_test_sk"}
            )

    @pytest.mark.asyncio
    async def test_sk_dev_mode_default_model(self) -> None:
        """test开发模式默认模型."""
        mock_span = Mock()
        mock_span.start = Mock()
        mock_span.start.return_value.__enter__ = Mock()
        mock_span.start.return_value.__exit__ = Mock()
        mock_sub_span = Mock()
        mock_sub_span.add_info_events = Mock()
        mock_span.start.return_value.__enter__.return_value = mock_sub_span

        with patch("infra.app_auth.agent_config") as mock_config:
            mock_config.is_dev.return_value = True
            mock_config.spark_x1_model_name = "different_model"
            mock_config.default_llm_sk = "default_test_sk"

            result = await self.maas_auth.sk(mock_span)

            assert result == "default_test_sk"
            mock_sub_span.add_info_events.assert_called_once_with(
                {"maas-default-sk": "default_test_sk"}
            )

    @pytest.mark.asyncio
    async def test_sk_production_mode_success(self) -> None:
        """test生产模式成功获取SK."""
        mock_span = Mock()
        mock_span.start = Mock()
        mock_span.start.return_value.__enter__ = Mock()
        mock_span.start.return_value.__exit__ = Mock()
        mock_sub_span = Mock()
        mock_sub_span.add_info_events = Mock()
        mock_span.start.return_value.__enter__.return_value = mock_sub_span

        # Mock application details response
        app_detail_response = {
            "code": 0,
            "data": [
                {
                    "auth_list": [
                        {"api_key": "test_api_key", "api_secret": "test_api_secret"}
                    ]
                }
            ],
        }

        with patch("infra.app_auth.agent_config") as mock_config:
            mock_config.is_dev.return_value = False
            with patch("infra.app_auth.APPAuth") as mock_app_auth_class:
                mock_app_auth = Mock()
                mock_app_auth.app_detail = AsyncMock(return_value=app_detail_response)
                mock_app_auth_class.return_value = mock_app_auth

                result = await self.maas_auth.sk(mock_span)

            assert result == "test_api_key:test_api_secret"
            # Verify span event recording
            assert mock_sub_span.add_info_events.call_count == 2

    @pytest.mark.asyncio
    async def test_sk_production_mode_app_not_found(self) -> None:
        """test生产模式应用未找到."""
        mock_span = Mock()
        mock_span.start = Mock()
        mock_span.start.return_value.__enter__ = Mock()
        mock_span.start.return_value.__exit__ = Mock(return_value=None)
        mock_sub_span = Mock()
        mock_sub_span.add_info_events = Mock()
        mock_span.start.return_value.__enter__.return_value = mock_sub_span

        with patch("infra.app_auth.agent_config") as mock_config:
            mock_config.is_dev.return_value = False
            with patch("infra.app_auth.APPAuth") as mock_app_auth_class:
                mock_app_auth = Mock()
                mock_app_auth.app_detail = AsyncMock(return_value=None)
                mock_app_auth_class.return_value = mock_app_auth

                with pytest.raises(MiddlewareExc):
                    await self.maas_auth.sk(mock_span)

    @pytest.mark.asyncio
    async def test_maas_detail_success(self) -> None:
        """test成功获取Maas详情."""
        api_key = "test_api_key"
        api_secret = "test_api_secret"
        expected_sk = "test_secret_key"

        # Mock response object
        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.json.return_value = {"code": 0, "data": expected_sk}
        mock_response.raise_for_status = AsyncMock()

        # Mock aiohttp ClientSession
        mock_session = MagicMock()
        mock_session.post = MagicMock()
        mock_session.post.return_value.__aenter__ = AsyncMock(
            return_value=mock_response
        )
        mock_session.post.return_value.__aexit__ = AsyncMock(return_value=None)
        mock_session.__aenter__ = AsyncMock(return_value=mock_session)
        mock_session.__aexit__ = AsyncMock(return_value=None)

        with (
            patch("infra.app_auth.aiohttp.ClientSession", return_value=mock_session),
            patch("infra.app_auth.agent_config") as mock_config,
        ):
            mock_config.maas_sk_auth_url = "https://test.maas.url"

            result = await self.maas_auth.maas_detail(api_key, api_secret)

            assert result == expected_sk

            # Verify request data
            call_kwargs = mock_session.post.call_args[1]
            expected_data = {
                "appId": "test_app_id",
                "apiKey": api_key,
                "apiSecret": api_secret,
                "version": "maas",
                "serviceId": "xingchen-agent",
            }
            assert call_kwargs["json"] == expected_data

    @pytest.mark.asyncio
    async def test_maas_detail_error_response(self) -> None:
        """testMaas详情错误响应."""
        api_key = "test_api_key"
        api_secret = "test_api_secret"

        # Mock response object
        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.json.return_value = {"code": 1, "message": "Auth failed"}
        mock_response.raise_for_status = AsyncMock()

        # Mock aiohttp ClientSession
        mock_session = MagicMock()
        mock_session.post = MagicMock()
        mock_session.post.return_value.__aenter__ = AsyncMock(
            return_value=mock_response
        )
        mock_session.post.return_value.__aexit__ = AsyncMock(return_value=None)
        mock_session.__aenter__ = AsyncMock(return_value=mock_session)
        mock_session.__aexit__ = AsyncMock(return_value=None)

        with (
            patch("infra.app_auth.aiohttp.ClientSession", return_value=mock_session),
            patch("infra.app_auth.agent_config") as mock_config,
        ):
            mock_config.maas_sk_auth_url = "https://test.maas.url"

            with pytest.raises(
                MiddlewareExc, match="Cannot find appid authentication information"
            ):
                await self.maas_auth.maas_detail(api_key, api_secret)

    @pytest.mark.asyncio
    async def test_maas_detail_non_200_status(self) -> None:
        """testMaas详情非200状态码."""
        api_key = "test_api_key"
        api_secret = "test_api_secret"

        mock_response = AsyncMock()
        mock_response.status = 500
        mock_response.raise_for_status = AsyncMock()

        mock_session = MagicMock()
        mock_session.post = MagicMock()
        mock_session.post.return_value.__aenter__ = AsyncMock(
            return_value=mock_response
        )
        mock_session.post.return_value.__aexit__ = AsyncMock(return_value=None)
        mock_session.__aenter__ = AsyncMock(return_value=mock_session)
        mock_session.__aexit__ = AsyncMock(return_value=None)

        with (
            patch("infra.app_auth.aiohttp.ClientSession", return_value=mock_session),
            patch("infra.app_auth.agent_config") as mock_config,
        ):
            mock_config.maas_sk_auth_url = "https://test.maas.url"

            with pytest.raises(MiddlewareExc, match="response code is 500"):
                await self.maas_auth.maas_detail(api_key, api_secret)

    @pytest.mark.asyncio
    async def test_maas_detail_timeout_configuration(self) -> None:
        """testMaas详情超时配置."""
        api_key = "test_api_key"
        api_secret = "test_api_secret"

        mock_response = AsyncMock()
        mock_response.status = 200
        mock_response.json.return_value = {"code": 0, "data": "sk"}
        mock_response.raise_for_status = AsyncMock()

        mock_session = MagicMock()
        mock_session.post = MagicMock()
        mock_session.post.return_value.__aenter__ = AsyncMock(
            return_value=mock_response
        )
        mock_session.post.return_value.__aexit__ = AsyncMock(return_value=None)
        mock_session.__aenter__ = AsyncMock(return_value=mock_session)
        mock_session.__aexit__ = AsyncMock(return_value=None)

        with (
            patch("infra.app_auth.aiohttp.ClientSession", return_value=mock_session),
            patch("infra.app_auth.agent_config") as mock_config,
            patch("infra.app_auth.aiohttp.ClientTimeout") as mock_timeout,
        ):
            mock_config.maas_sk_auth_url = "https://test.maas.url"

            await self.maas_auth.maas_detail(api_key, api_secret)

            # Verify timeout settings
            mock_timeout.assert_called_once_with(total=3)

    @pytest.mark.asyncio
    async def test_maas_detail_request_exception(self) -> None:
        """testMaas详情请求异常."""
        api_key = "test_api_key"
        api_secret = "test_api_secret"

        mock_session = MagicMock()
        mock_session.post.side_effect = Exception("Network error")
        mock_session.__aenter__ = AsyncMock(return_value=mock_session)
        mock_session.__aexit__ = AsyncMock(return_value=None)

        with (
            patch("infra.app_auth.aiohttp.ClientSession", return_value=mock_session),
            patch("infra.app_auth.agent_config") as mock_config,
        ):
            mock_config.maas_sk_auth_url = "https://test.maas.url"

            with pytest.raises(Exception, match="Network error"):
                await self.maas_auth.maas_detail(api_key, api_secret)

    def test_maas_auth_model_serialization(self) -> None:
        """testMaasAuth模型序列化."""
        auth_dict = self.maas_auth.model_dump()

        assert isinstance(auth_dict, dict)
        assert auth_dict["app_id"] == "test_app_id"
        assert auth_dict["model_name"] == "test_model"
        assert (
            auth_dict["app_id_not_found_msg"]
            == "Cannot find appid authentication information"
        )

    def test_maas_auth_field_validation(self) -> None:
        """testMaasAuth字段验证."""
        # Test required fields
        auth = MaasAuth(app_id="", model_name="")
        assert auth.app_id == ""
        assert auth.model_name == ""

        # Test field types
        auth = MaasAuth(app_id="test", model_name="model")
        assert isinstance(auth.app_id, str)
        assert isinstance(auth.model_name, str)
        assert isinstance(auth.app_id_not_found_msg, str)
