import base64
import datetime
import hashlib
import hmac
import json
from dataclasses import dataclass
from typing import Any, Dict, Optional

import aiohttp
from pydantic import BaseModel, Field

from common_imports import Span
from exceptions.middleware_exc import MiddlewareExc
from infra import agent_config


def http_date(dt: datetime.datetime) -> str:
    """
    Return a string representation of a date according to RFC 1123
    (HTTP/1.1).

    The supplied date must be in UTC.

    """
    weekday = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"][dt.weekday()]
    month = [
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
    ][dt.month - 1]
    return (
        f"{weekday}, {dt.day:02d} {month} {dt.year:04d} "
        f"{dt.hour:02d}:{dt.minute:02d}:{dt.second:02d} GMT"
    )


def hashlib_256(res: str) -> str:
    m = hashlib.sha256(bytes(res.encode(encoding="utf-8"))).digest()
    result = "SHA256=" + base64.b64encode(m).decode(encoding="utf-8")
    return result


@dataclass
class AuthConfig:  # pylint: disable=too-many-instance-attributes
    """Authentication configuration"""

    host: str
    route: str
    prot: str
    api_key: str
    secret: str
    method: str = "GET"
    algorithm: str = "hmac-sha256"
    http_proto: str = "HTTP/1.1"

    @property
    def url(self) -> str:
        return f"{self.prot}://{self.host}{self.route}"


class APPAuth:

    def __init__(self) -> None:
        self.config = AuthConfig(
            host=agent_config.APP_AUTH_HOST,
            route=agent_config.APP_AUTH_ROUTER,
            prot=agent_config.APP_AUTH_PROT,
            api_key=agent_config.APP_AUTH_API_KEY,
            secret=agent_config.APP_AUTH_SECRET,
        )
        # Set current time
        self.date = http_date(datetime.datetime.utcnow())

    def generate_signature(self, digest: str) -> str:
        signature_str = "host: " + self.config.host + "\n"
        signature_str += "date: " + self.date + "\n"
        signature_str += (
            f"{self.config.method} {self.config.route} {self.config.http_proto}\n"
        )
        signature_str += "digest: " + digest
        signature = hmac.new(
            bytes(self.config.secret, encoding="UTF-8"),
            bytes(signature_str, encoding="UTF-8"),
            digestmod=hashlib.sha256,
        ).digest()
        result = base64.b64encode(signature)
        return result.decode(encoding="utf-8")

    def init_header(self, data: str) -> Dict[str, str]:
        digest = hashlib_256(data)
        sign = self.generate_signature(digest)
        auth_header = (
            f'api_key="{self.config.api_key}", '
            f'algorithm="{self.config.algorithm}", '
            f'headers="host date request-line digest", '
            f'signature="{sign}"'
        )
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "Method": "GET",
            "Host": self.config.host,
            "Date": self.date,
            "Digest": digest,
            "Authorization": auth_header,
        }
        return headers

    async def app_detail(self, app_id: str) -> Optional[Dict[str, Any]]:
        headers = self.init_header("")
        async with aiohttp.ClientSession() as session:
            timeout = aiohttp.ClientTimeout(total=3)
            async with session.get(
                self.config.url,
                params={"app_ids": app_id + ","},
                headers=headers,
                timeout=timeout,
            ) as response:
                response.raise_for_status()
                if response.status == 200:
                    result = await response.json()
                    return dict(result)

                raise MiddlewareExc(
                    40040,
                    "AppId authentication information query failed",
                    on=f"response code is {response.status}",
                )


class MaasAuth(BaseModel):
    app_id: str
    model_name: str

    app_id_not_found_msg: str = Field(
        default="Cannot find appid authentication information"
    )

    async def sk(self, span: Span) -> str:
        with span.start("QueryAppIdSk") as sp:
            app_detail = await APPAuth().app_detail(self.app_id)

            sp.add_info_events(
                {"kong-app-detail": json.dumps(app_detail, ensure_ascii=False)}
            )

            if app_detail is None:
                raise MiddlewareExc(
                    40040,
                    "AppId authentication information query failed",
                    on=self.app_id_not_found_msg,
                )

            if app_detail.get("code") != 0:
                raise MiddlewareExc(
                    40040,
                    "AppId authentication information query failed",
                    on=app_detail.get("message", ""),
                )

            data = app_detail.get("data", [])
            if len(data) == 0:
                raise MiddlewareExc(
                    40040,
                    "AppId authentication information query failed",
                    on=self.app_id_not_found_msg,
                )

            auth_list = data[0].get("auth_list", [])
            if len(auth_list) == 0:
                raise MiddlewareExc(
                    40040,
                    "AppId authentication information query failed",
                    on=self.app_id_not_found_msg,
                )

            api_key = auth_list[0].get("api_key")
            api_secret = auth_list[0].get("api_secret")

            kong_sk = f"{api_key}:{api_secret}"

            sp.add_info_events({"kong-sk": kong_sk})

            return kong_sk
