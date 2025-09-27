# encoding=utf-8
"""
HTTP Authentication utilities for API access.

This module provides functions and classes for generating authentication
tokens, signatures, and headers for HTTP and WebSocket API requests.
"""
import base64
import hashlib
import hmac
import json
import os
import time
from datetime import datetime
from time import mktime
from urllib.parse import urlencode
from wsgiref.handlers import format_date_time

from plugin.link.consts import const


def generate_13_digit_timestamp():
    """
    Generate a 13-digit timestamp.

    Returns:
        str: A 13-digit timestamp string (seconds + milliseconds)
    """
    # Get current time in seconds and microseconds
    current_time = time.time()
    seconds = int(current_time)  # Seconds part
    milliseconds = int(
        (current_time - seconds) * 1000
    )  # Milliseconds part, take integer part from 0 to 999
    # Combine into 13-digit timestamp (10 for seconds, 3 for milliseconds)
    timestamp = f"{seconds}{milliseconds:03d}"
    return timestamp


def md5_encode(text):
    """
    Generate MD5 hash for the given text.

    Args:
        text (str): The text to be hashed

    Returns:
        str: MD5 hash digest in hexadecimal format
    """
    md5 = hashlib.md5(text.encode())  # Create md5 object
    md5pwd = md5.hexdigest()  # MD5 encryption
    # print(md5pwd)
    return md5pwd
    # return md5


def public_query_url(url: str, app_id=None, app_key=None):
    """
    Generate a public query URL with authentication parameters.

    Args:
        url (str): Base URL
        app_id: Application ID (unused, retrieved from environment)
        app_key: Application key (unused, retrieved from environment)

    Returns:
        str: URL with appId, token, and timestamp parameters
    """
    app_id = os.getenv(const.HTTP_AUTH_QU_APP_ID_KEY)
    app_key = os.getenv(const.HTTP_AUTH_QU_APP_KEY_KEY)
    timestamp = generate_13_digit_timestamp()
    md5_string = app_id + app_key + timestamp
    token = md5_encode(md5_string)  # MD5
    query_url = f"{url}?appId={app_id}&token={token}&timestamp={timestamp}"
    return query_url


def get_query_url(
    url: str,
    app_id=None,
    app_key=None,
    public_data=None,
    query_data=None,
):
    """
    Generate a query URL with authentication and additional query parameters.

    Args:
        url (str): Base URL
        app_id: Application ID (unused, retrieved from environment)
        app_key: Application key (unused, retrieved from environment)
        public_data (dict, optional): Public query parameters to append
        query_data (dict, optional): Additional query parameters to append

    Returns:
        str: Complete URL with authentication and query parameters
    """
    app_id = os.getenv(const.HTTP_AUTH_QU_APP_ID_KEY)
    app_key = os.getenv(const.HTTP_AUTH_QU_APP_KEY_KEY)
    timestamp = generate_13_digit_timestamp()
    md5_string = app_id + app_key + timestamp
    token = md5_encode(md5_string)  # MD5
    query_url = f"{url}?appId={app_id}&token={token}&timestamp={timestamp}"
    if public_data:
        for k, v in public_data.items():
            query_url += f"&{k}={v}"
    # print(f"public_query_url is: {query_url}")
    if query_data:
        for k, v in query_data.items():
            query_url += f"&{k}={v}"
    return query_url


class AssembleHeaderException(Exception):
    """Exception raised when there's an error assembling authentication headers."""

    def __init__(self, msg):
        self.message = msg


class Url:
    """Simple URL container class to store parsed URL components."""

    def __init__(self, host, path, schema):
        self.host = host
        self.path = path
        self.schema = schema


def parse_url(requset_url):
    """
    Parse a URL into its components.

    Args:
        requset_url (str): The URL to parse

    Returns:
        Url: A Url object containing host, path, and schema components

    Raises:
        AssembleHeaderException: If the URL format is invalid
    """
    stidx = requset_url.index("://")
    host = requset_url[stidx + 3 :]
    schema = requset_url[: stidx + 3]
    try:
        edidx = host.index("/")
    except ValueError:
        raise AssembleHeaderException("invalid request url:" + requset_url)
    if edidx <= 0:
        raise AssembleHeaderException("invalid request url:" + requset_url)
    path = host[edidx:]
    host = host[:edidx]
    u = Url(host, path, schema)
    return u


# build websocket auth request url
def assemble_ws_auth_url(requset_url, method, auth_con_js, body=None):
    """
    Build WebSocket authentication request URL and headers.

    Args:
        requset_url (str): The request URL
        method (str): HTTP method (GET, POST, etc.)
        auth_con_js (dict): Authentication configuration dictionary
        body (dict, optional): Request body for digest calculation

    Returns:
        tuple: (result_url, headers) - The authenticated URL and headers
    """
    app_id = os.getenv(const.HTTP_AUTH_AWAU_APP_ID_KEY)
    api_key = os.getenv(const.HTTP_AUTH_AWAU_API_KEY_KEY)
    api_secret = os.getenv(const.HTTP_AUTH_AWAU_API_SECRET_KEY)
    try:
        u = parse_url(requset_url)
    except ValueError:
        raise AssembleHeaderException("invalid request url:" + requset_url)
    host = u.host
    path = u.path
    now = datetime.now()
    date = format_date_time(mktime(now.timetuple()))
    # print(date)

    # digest requires body for encryption
    signature_input_part = ""  # 1.""    2. digest: + digest
    if auth_con_js.get("is_digest"):
        digest = hashlib_256(body)
        signature_input_part = "\n" + "digest: " + digest

    # date = "Thu, 12 Dec 2019 01:57:27 GMT"
    signature_origin = f"host: {host}\ndate: {date}\n{method.upper()} {path} HTTP/1.1"
    signature_origin += signature_input_part
    # print(signature_origin)
    signature_sha = hmac.new(
        api_secret.encode("utf-8"),
        signature_origin.encode("utf-8"),
        digestmod=hashlib.sha256,
    ).digest()
    signature_sha = base64.b64encode(signature_sha).decode(encoding="utf-8")

    # User input 1."api_key"   2."hmac username"
    authorization_input_part = auth_con_js.get("authorization_input_part")
    authorization_headers_part = "host date request-line"
    if auth_con_js.get("is_digest"):
        authorization_headers_part += " digest"
    authorization_origin = (
        f'{authorization_input_part}="{api_key}", '
        f'algorithm="hmac-sha256", '
        f'headers="{authorization_headers_part}", '
        f'signature="{signature_sha}"'
    )
    authorization = base64.b64encode(authorization_origin.encode("utf-8")).decode(
        encoding="utf-8"
    )
    # print(authorization_origin)

    # Headers can vary
    headers = {
        "content-Type": "application/json",
        "host": host,
        "app_id": app_id,
    }
    if auth_con_js.get("is_digest"):
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "Method": method,
            "Host": host,
            "Date": date,
            "Digest": hashlib_256(body),
            "Authorization": authorization_origin,
        }

    # URL two cases, with and without concatenation
    result_url = requset_url
    if auth_con_js.get("is_url_join"):
        values = {"host": host, "date": date, "authorization": authorization}
        result_url = requset_url + "?" + urlencode(values)

    return result_url, headers


def hashlib_256(res):
    """
    Generate SHA-256 digest with base64 encoding for request body.

    Args:
        res: The request data to hash (typically a dictionary)

    Returns:
        str: SHA-256 digest in the format "SHA-256=<base64_encoded_hash>"
    """
    res_js = json.dumps(res)
    m = hashlib.sha256(bytes(res_js.encode(encoding="utf-8"))).digest()
    result = "SHA-256=" + base64.b64encode(m).decode(encoding="utf-8")
    return result
