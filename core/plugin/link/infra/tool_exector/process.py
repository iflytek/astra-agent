"""HTTP request processing module for tool execution.

This module provides HTTP request execution functionality with various
authentication methods and security validations.
"""

import ipaddress
import json
import os
import re
from urllib.parse import quote, urljoin, urlparse, urlunparse

import aiohttp
from plugin.link.consts import const
from plugin.link.exceptions.sparklink_exceptions import CallThirdApiException
from plugin.link.infra.tool_exector.http_auth import (
    assemble_ws_auth_url,
    public_query_url,
)
from plugin.link.utils.errors.code import ErrCode


class HttpRun:
    """HTTP request executor with authentication and security validation.

    Handles various authentication methods including MD5 and HMAC,
    validates against blacklists, and executes HTTP requests safely.

    Instance Attributes Organization:

    Request Configuration:
        - server: Target server URL
        - method: HTTP method (GET, POST, etc.)
        - path: Request path components
        - query: Query parameters dictionary
        - header: HTTP headers dictionary
        - body: Request body data

    Authentication State:
        - _is_authorization_md5: Boolean flag for MD5 auth detection
        - _is_auth_hmac: Boolean flag for HMAC auth detection
        - auth_con_js: HMAC authentication configuration object

    Security Validation:
        - _is_official: Boolean flag marking official API status
        - _is_in_blacklist: Boolean flag for blacklist validation result

    All attributes serve specific roles in HTTP request processing,
    authentication handling, and security validation workflows.
    """

    def __init__(self, server, method, path, query, header, body, open_api_schema=None):
        self.server = server
        self.method = method
        self.path = path
        self.query = query
        self.header = header
        self.body = body
        try:
            self._is_authorization_md5 = HttpRun.is_authorization_md5(open_api_schema)
        except Exception:
            self._is_authorization_md5 = False
        try:
            self._is_auth_hmac, self.auth_con_js = HttpRun.is_authorization_hmac(
                self.header
            )
        except Exception:
            self._is_auth_hmac = False
            self.auth_con_js = object
        try:
            self._is_official = HttpRun.is_official(open_api_schema)
        except Exception:
            self._is_official = False
        try:
            self._is_in_blacklist = HttpRun.is_in_blacklist(self.server)
        except Exception:
            self._is_in_blacklist = False

    def _validate_blacklist(self):
        """Validate server is not blacklisted.

        Raises:
            CallThirdApiException: When server is blacklisted
        """
        if self._is_in_blacklist:
            raise CallThirdApiException(
                code=ErrCode.SERVER_VALIDATE_ERR.code,
                err_pre=ErrCode.SERVER_VALIDATE_ERR.msg,
                err="Request tool path hostname is in blacklist",
            )

    def _build_url(self):
        """Build request URL with authentication and query parameters.

        Returns:
            str: Complete URL for the request
        """
        url = self.server

        # URL path construction
        path_res = [frag for _, frag in self.path.items()]
        if self.path:
            url = urljoin(url, path_res[0])

        # Authentication method selection and URL construction
        if self._is_authorization_md5:
            url = public_query_url(url)
            if self.query:
                url = url + "&" + "&".join([f"{k}={v}" for k, v in self.query.items()])
        elif self._is_auth_hmac:
            url, headers = assemble_ws_auth_url(
                url, self.method, self.auth_con_js, self.body
            )
            self.header = headers
        else:
            if self.query:
                url = url + "?" + "&".join([f"{k}={v}" for k, v in self.query.items()])

        return url

    def _get_error_codes(self):
        """Get appropriate error codes based on API type.

        Returns:
            tuple: (error_code, error_message_prefix)
        """
        if self._is_official:
            return (
                ErrCode.OFFICIAL_API_REQUEST_FAILED_ERR.code,
                ErrCode.OFFICIAL_API_REQUEST_FAILED_ERR.msg,
            )
        return (
            ErrCode.THIRD_API_REQUEST_FAILED_ERR.code,
            ErrCode.THIRD_API_REQUEST_FAILED_ERR.msg,
        )

    async def _execute_request(self, url, span_context):
        """Execute the HTTP request.

        Args:
            url: Request URL
            span_context: Tracing span context

        Returns:
            tuple: (response_text, status_code)
        """
        try:
            self.header.pop("@type")
        except Exception:
            pass

        encoded_url = quote(url, safe="/:?=&")
        span_context.add_info_event(f"raw_url: {url}, encoded_url: {encoded_url}")
        span_context.add_info_event(
            f"encoded_url: {encoded_url}, header: {self.header}, " f"body: {self.body}"
        )

        kwargs = {
            "headers": self.header if self.header else None,
            "json": self.body if self.body else None,
        }

        async with aiohttp.ClientSession() as session:
            async with session.request(self.method, encoded_url, **kwargs) as response:
                response_text = await response.text()
                status_code = response.status

        span_context.add_info_event(f"{status_code}")
        span_context.add_info_event(f"{response_text}")

        return response_text, status_code

    async def do_call(self, span):
        """Execute the HTTP request with proper authentication and validation.

        Args:
            span: Tracing span for request monitoring

        Returns:
            str: Response text from the HTTP request

        Raises:
            CallThirdApiException: When request fails or server is blacklisted
        """
        self._validate_blacklist()
        url = self._build_url()

        with span.start(func_name="http_run") as span_context:
            try:
                third_result, status_code = await self._execute_request(
                    url, span_context
                )
            except Exception as err:
                span.add_error_event(str(err))
                code_return, err_pre_return = self._get_error_codes()
                raise CallThirdApiException(
                    code=code_return, err_pre=err_pre_return, err=err
                ) from err

        if status_code != 200:
            err_reason = (
                f"Request error code: {status_code}, error message {third_result}"
            )
            code_return, err_pre_return = self._get_error_codes()
            raise CallThirdApiException(
                code=code_return, err_pre=err_pre_return, err=err_reason
            )

        return third_result

    @staticmethod
    def is_authorization_md5(open_api_schema):
        """Check if the API uses MD5 authorization.

        Args:
            open_api_schema: OpenAPI schema definition

        Returns:
            bool: True if MD5 authorization is used
        """
        if open_api_schema:
            paths = open_api_schema.get("paths")
            for _, get_dict in paths.items():
                parameters = get_dict["get"]["parameters"]
                for para in parameters:
                    if (
                        para["in"] == "header"
                        and para["name"] == "Authorization"
                        and para["schema"]["default"] == "MD5"
                    ):
                        return True
        return False

    @staticmethod
    def is_authorization_hmac(header):
        """Check if the request uses HMAC authorization.

        Args:
            header: Request headers dictionary

        Returns:
            tuple: (is_hmac, auth_config) - boolean and config object
        """
        if header:
            authorization = header.get("Authorization")
            if authorization and len(authorization) != 0:
                try:
                    ix = authorization.index(":")
                    auth_prefix = authorization[:ix]
                    if auth_prefix == "HMAC":
                        auth_con = authorization[ix + 1 :].strip()
                        try:
                            auth_con_js = json.loads(auth_con)
                            return True, auth_con_js
                        except json.JSONDecodeError:
                            # Handle malformed JSON gracefully
                            return False, object
                except ValueError:
                    # Handle missing colon gracefully
                    return False, object

        return False, object

    @staticmethod
    def is_official(open_api_schema):
        """Check if the API is marked as official.

        Args:
            open_api_schema: OpenAPI schema definition

        Returns:
            bool: True if API is official
        """
        if open_api_schema:
            info = open_api_schema.get("info")
            if info.get("x-is-official"):
                return True

        return False

    @staticmethod
    def is_in_black_domain(url: str):
        """Check if URL domain is in the domain blacklist.

        Args:
            url: URL to check

        Returns:
            bool: True if domain is blacklisted
        """
        # Get environment variable and handle unset or empty cases
        black_list_str = os.getenv(const.DOMAIN_BLACK_LIST_KEY, "")
        if not black_list_str:
            return False

        # Split blacklist string into list
        domain_black_list = [
            domain.strip().lower() for domain in black_list_str.split(",")
        ]

        # Convert URL to lowercase to avoid case issues
        url_lower = url.lower()

        # Check if blacklisted domains are in URL
        for black_domain in domain_black_list:
            # Ensure matching complete domain names, not substrings
            if black_domain.lower() in url_lower:
                return True

        return False

    @staticmethod
    def _get_blacklist_config():
        """Get blacklist configuration from environment variables.

        Returns:
            tuple: (segment_blacklist, ip_blacklist)
        """
        segment_black_list = []
        for black_i in os.getenv(const.SEGMENT_BLACK_LIST_KEY).split(","):
            segment_black_list.append(ipaddress.ip_network(black_i))
        ip_black_list = os.getenv(const.IP_BLACK_LIST_KEY).split(",")
        return segment_black_list, ip_black_list

    @staticmethod
    def _extract_ip_from_url(url):
        """Extract IP address from URL.

        Args:
            url: URL to extract IP from

        Returns:
            str or None: IP address if found, None otherwise
        """
        if not url:
            return None

        match = re.search(r"://([^/?#]+)", url)
        if not match:
            return None

        host = match.group(1)
        # Handle cases that might include port numbers
        if ":" in host:
            return host.split(":")[0]
        return host

    @staticmethod
    def _is_ip_blacklisted(ip, ip_black_list, segment_black_list):
        """Check if IP is in blacklist or blacklisted network segments.

        Args:
            ip: IP address to check
            ip_black_list: List of blacklisted IPs
            segment_black_list: List of blacklisted network segments

        Returns:
            bool: True if IP is blacklisted
        """
        # Check IP blacklist
        for i_ip in ip_black_list:
            if ip == i_ip:
                return True

        # Check network segment validation
        try:
            ip_obj = ipaddress.ip_address(ip)
            for subnet in segment_black_list:
                if ip_obj in subnet:
                    return True
            return False
        except ValueError:
            return False

    @staticmethod
    def is_in_blacklist(url):
        """Check if URL is in IP or network segment blacklist.

        Args:
            url: URL to validate against blacklists

        Returns:
            bool: True if URL is blacklisted
        """
        # Domain-based blacklist validation
        if HttpRun.is_in_black_domain(str(url)):
            return True

        # URL parsing and normalization
        parsed = urlparse(url)
        url = urlunparse((parsed.scheme, parsed.hostname, parsed.path, "", "", ""))

        # Extract IP from URL
        ip = HttpRun._extract_ip_from_url(url)
        if not ip:
            return False

        # Get blacklist configuration
        segment_black_list, ip_black_list = HttpRun._get_blacklist_config()

        # Check if IP is blacklisted
        return HttpRun._is_ip_blacklisted(ip, ip_black_list, segment_black_list)
