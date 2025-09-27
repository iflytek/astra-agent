"""
HMAC Authentication utility module for building authenticated requests.
"""

import base64
import hashlib
import hmac
from datetime import datetime
from time import mktime
from urllib import parse
from urllib.parse import urlencode, urlparse
from wsgiref.handlers import format_date_time


class HMACAuth:
    """Class for HMAC authentication related operations."""

    @staticmethod
    def build_auth_request_url(
        request_url: str, method: str = "GET", api_key: str = "", api_secret: str = ""
    ) -> str:
        """
        Build authenticated request URL with HMAC signature.

        Args:
            request_url: The request URL
            method: HTTP method (GET/POST/etc.)
            api_key: API key
            api_secret: API secret

        Returns:
            str: Authenticated request URL with query parameters
        """
        values = HMACAuth.build_auth_params(request_url, method, api_key, api_secret)
        return request_url + "?" + urlencode(values)

    @staticmethod
    def build_auth_params(
        request_url: str, method: str = "GET", api_key: str = "", api_secret: str = ""
    ) -> dict:
        """
        Build authentication parameters for HMAC signature.

        Args:
            request_url: The request URL
            method: HTTP method (GET/POST/etc.)
            api_key: API key
            api_secret: API secret

        Returns:
            dict: Authentication parameters
        """
        url_result = parse.urlparse(request_url)
        date = format_date_time(mktime(datetime.now().timetuple()))
        signature_origin = (
            f"host: {url_result.hostname}\n"
            f"date: {date}\n{method} {url_result.path} HTTP/1.1"
        )
        signature_sha_bytes = hmac.new(
            api_secret.encode("utf-8"),
            signature_origin.encode("utf-8"),
            digestmod=hashlib.sha256,
        ).digest()
        signature_sha = base64.b64encode(signature_sha_bytes).decode(encoding="utf-8")
        authorization_origin = (
            f'api_key="{api_key}", algorithm="hmac-sha256", '
            f'headers="host date request-line", signature="{signature_sha}"'
        )
        authorization = base64.b64encode(authorization_origin.encode("utf-8")).decode(
            encoding="utf-8"
        )
        values = {
            "host": url_result.hostname,
            "date": date,
            "authorization": authorization,
        }
        return values

    @staticmethod
    def build_auth_header(
        request_url: str, method: str = "GET", api_key: str = "", api_secret: str = ""
    ) -> dict:
        """
        Build authentication headers for HMAC signature.

        Args:
            request_url: The request URL
            method: HTTP method (GET/POST/etc.)
            api_key: API key
            api_secret: API secret

        Returns:
            dict: Authentication headers
        """
        url_result = urlparse(request_url)
        host = url_result.hostname
        path = url_result.path
        now = datetime.now()
        date = format_date_time(mktime(now.timetuple()))
        m = hashlib.sha256(bytes("".encode(encoding="utf-8"))).digest()
        digest = "SHA256=" + base64.b64encode(m).decode(encoding="utf-8")

        signature_str = f"host: {host}\n"
        signature_str += f"date: {date}\n"
        signature_str += f"{method} {path} HTTP/1.1\n"
        signature_str += f"digest: {digest}"

        signature = hmac.new(
            bytes(api_secret, encoding="UTF-8"),
            bytes(signature_str, encoding="UTF-8"),
            digestmod=hashlib.sha256,
        ).digest()
        sign = base64.b64encode(signature).decode(encoding="utf-8")

        auth_header = (
            f'api_key="{api_key}", algorithm="hmac-sha256", '
            f'headers="host date request-line digest", signature="{sign}"'
        )

        headers = {
            "Method": method,
            "Host": host,
            "Date": date,
            "Digest": digest,
            "Authorization": auth_header,
        }
        return headers
