"""
HMAC signature generation module for secure API request authentication.
"""

import base64
import hashlib
import hmac
from datetime import datetime
from time import mktime
from urllib.parse import urlencode
from wsgiref.handlers import format_date_time


class AssembleHeaderException(Exception):
    def __init__(self, msg):
        self.message = msg


class Url:
    def __init__(self, host, path, schema):
        self.host = host
        self.path = path
        self.schema = schema


# calculate sha256 and encode to base64
def sha256base64(data):
    sha256 = hashlib.sha256()
    sha256.update(data)
    digest = base64.b64encode(sha256.digest()).decode(encoding="utf-8")
    return digest


def parse_url(requset_url):
    stidx = requset_url.index("://")
    host = requset_url[stidx + 3 :]
    schema = requset_url[: stidx + 3]
    edidx = host.index("/")
    if edidx <= 0:
        raise AssembleHeaderException("invalid request url:" + requset_url)
    path = host[edidx:]
    host = host[:edidx]
    u = Url(host, path, schema)
    return u


# build websocket auth request url
def assemble_auth_url(requset_url, method="GET", api_key="", api_secret=""):
    u = parse_url(requset_url)
    host = u.host
    path = u.path
    now = datetime.now()
    date = format_date_time(mktime(now.timetuple()))
    signature_origin = f"host: {host}\ndate: {date}\n{method} {path} HTTP/1.1"
    signature_sha = hmac.new(
        api_secret.encode("utf-8"),
        signature_origin.encode("utf-8"),
        digestmod=hashlib.sha256,
    ).digest()
    signature_sha = base64.b64encode(signature_sha).decode(encoding="utf-8")
    authorization_origin = (
        f'api_key="{api_key}", algorithm="hmac-sha256", '
        f'headers="host date request-line", signature="{signature_sha}"'
    )
    authorization = base64.b64encode(authorization_origin.encode("utf-8")).decode(
        encoding="utf-8"
    )
    values = {"host": host, "date": date, "authorization": authorization}

    return requset_url + "?" + urlencode(values)
