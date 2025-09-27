import asyncio
import base64
import hmac
import os
import uuid
from collections import OrderedDict
from datetime import datetime, timezone
from typing import Any, Dict, List, Literal
from urllib.parse import quote, urlencode  # type: ignore[import-untyped]

import aiohttp  # type: ignore[import-not-found]

from common.audit_system.audit_api.base import AuditAPI, Stage
from common.audit_system.base import ContextList
from common.exceptions.codes import c9021, c9022, c9023
from common.exceptions.errs import AuditServiceException
from common.otlp.trace.span import Span

CONNECT_TIMEOUT = 1

TEXT_READ_TIMEOUT = 6

IMAGE_READ_TIMEOUT = 10

RETRY_COUNT = 2


class ActionEnum:
    """
    定义审核动作枚举类。
    """

    NONE = "none"

    FORTIFY_PROMPT = "fortify_prompt"

    REANSWER = "reanswer"

    SAFE_MODEL = "safe_model"

    SAFE_ANSWER = "safe_answer"

    DISCONTINUE = "discontinue"

    REDLINE = "redline"

    HIDE_CONTINUE = "hide_continue"

    NONREFERENCE = "nonreference"


class IFlyAuditAPI(AuditAPI):
    audit_name = "IFlyAuditAPI"

    def __init__(self) -> None:
        self.app_id = os.getenv("IFLYTEK_AUDIT_APP_ID", "")
        self.access_key_id = os.getenv("IFLYTEK_AUDIT_ACCESS_KEY_ID", "")
        self.access_key_secret = os.getenv("IFLYTEK_AUDIT_ACCESS_KEY_SECRET", "")
        self.hosts = os.getenv(
            "IFLYTEK_AUDIT_HOST", "http://audit-api.xfyun.cn/v1.0"
        ).split(",")

        missing = []
        if not self.app_id:
            missing.append("IFLYTEK_AUDIT_APP_ID")
        if not self.access_key_id:
            missing.append("IFLYTEK_AUDIT_ACCESS_KEY_ID")
        if not self.access_key_secret:
            missing.append("IFLYTEK_AUDIT_ACCESS_KEY_SECRET")

        if missing:
            raise ValueError(f"缺少必要环境变量: {', '.join(missing)}")

    def _signature(self, query_param: dict) -> str:
        sorted_params = OrderedDict(sorted(query_param.items()))

        sorted_params.pop("signature", None)

        builder = []
        for key, value in sorted_params.items():
            if value is not None and value != "":
                encoded_value = quote(value, safe="")
                builder.append(f"{key}={encoded_value}")

        base_string = "&".join(builder)

        mac = hmac.new(
            self.access_key_secret.encode("utf-8"),
            base_string.encode("utf-8"),
            digestmod="sha1",
        )
        signature_bytes = mac.digest()
        return base64.b64encode(signature_bytes).decode("utf-8")

    def _gen_req_url(self, url: str, chat_app_id: str = "", uid: str = "") -> str:
        """
        生成授权请求URL，包含必要的认证参数。
        :param url:
        :param chat_app_id:
        :param uid:
        :return: URL
        """
        now_utc = datetime.now(timezone.utc)
        query_param = {
            "appId": self.app_id,
            "accessKeyId": self.access_key_id,
            "utc": now_utc.strftime("%Y-%m-%dT%H:%M:%S%z"),
            "uuid": str(uuid.uuid4()),
        }
        if chat_app_id:
            query_param["chatAppId"] = chat_app_id
        if uid:
            query_param["uid"] = uid

        signature = self._signature(query_param)
        query_param["signature"] = signature
        return url + "?" + urlencode(query_param)

    async def _post(
        self, path: str, payload: dict, span: Span, chat_app_id: str = "", uid: str = ""
    ) -> dict:
        """
        异步发送POST请求到审核API，并处理响应。
        :param path:
        :param payload:
        :param chat_app_id:
        :param uid:
        :return: 响应结果
        """
        span.add_info_event(f"送审请求体: {payload}")
        for idx, host in enumerate(self.hosts):

            timeout = aiohttp.ClientTimeout(
                sock_connect=CONNECT_TIMEOUT, sock_read=TEXT_READ_TIMEOUT
            )
            current_retry = 1

            while True:
                async with aiohttp.ClientSession(timeout=timeout) as session:
                    try:
                        url = self._gen_req_url(f"{host}{path}", chat_app_id, uid)
                        span.add_info_event(
                            f"请求URL: {url}, 重试次数: {current_retry}/{RETRY_COUNT}"
                        )
                        async with session.post(url, json=payload) as response:
                            cause_error = (
                                f"状态码: {response.status}, "
                                f"响应内容: {await response.text()}"
                            )

                            if response.status != 200:
                                span.add_error_event(cause_error)
                                if current_retry < RETRY_COUNT:
                                    continue
                                else:
                                    raise AuditServiceException(*c9021)(cause_error)

                            resp_json = await response.json()
                            span.add_info_event(f"送审响应体: {resp_json}")

                            code = resp_json.get("code", -1)
                            if int(code) == 0:
                                return resp_json
                            if int(code) == 999999 and current_retry < RETRY_COUNT:
                                continue
                            else:
                                cause_error = (
                                    f"请求失败，状态码: {response.status}, "
                                    f"响应内容: {await response.text()}, "
                                    f"响应体： {resp_json}"
                                )
                                raise AuditServiceException(*c9021)(cause_error)

                    except (aiohttp.ClientError, asyncio.TimeoutError, Exception) as e:
                        span.record_exception(e)
                        if current_retry < RETRY_COUNT:
                            span.add_info_event(f"请求失败第{current_retry}次: {e}")
                            continue
                        else:
                            raise AuditServiceException(*c9021)(str(e))
                    finally:
                        current_retry += 1
        return {}

    async def input_text(
        self,
        content: str,
        chat_sid: str,
        span: Span,
        chat_app_id: str = "",
        uid: str = "",
        template_id: str = "",
        context_list: List[ContextList] = [],
        **kwargs: Any,
    ) -> None:

        payload: Dict[str, Any] = {
            "intention": "dialog",
            "stage": "original_query",
            "content": content,
            "chat_sid": chat_sid,
        }

        if template_id:
            payload["template_id"] = template_id

        payload_context_list = []
        payload_resource_list = []
        if context_list:
            for ctx in context_list:
                if ctx.resource_list:
                    payload_resource_list.append(
                        res.dict() for res in ctx.resource_list  # type: ignore[attr-defined]
                    )
                payload_context_list.append(ctx.dict())

        if payload_context_list:
            payload["context_list"] = payload_context_list
        if payload_resource_list:
            payload["resource_list"] = payload_resource_list

        resp = await self._post(
            "/audit/v3/aichat/input", payload, span, chat_app_id, uid
        )
        if resp.get("data", {}).get("action") != ActionEnum.NONE:
            raise AuditServiceException(*c9022)(f"审核结果异常: {resp}")

    async def output_text(
        self,
        stage: Stage,
        content: str,
        pindex: int,
        span: Span,
        is_pending: Literal[0, 1],
        is_stage_end: Literal[0, 1],
        is_end: Literal[0, 1],
        chat_sid: str,
        chat_app_id: str = "",
        uid: str = "",
        **kwargs: Any,
    ) -> None:
        payload = {
            "intention": "dialog",
            "stage": stage.value,
            "content": content,
            "pindex": pindex,
            "is_pending": is_pending,
            "is_stage_end": is_stage_end,
            "is_end": is_end,
            "chat_sid": chat_sid,
        }
        resp = await self._post(
            "/audit/v3/aichat/output", payload, span, chat_app_id, uid
        )
        if resp.get("data", {}).get("action") != ActionEnum.NONE:
            raise AuditServiceException(*c9023)(f"审核结果异常: {resp}")

    async def input_media(self, text: str, **kwargs: Any) -> None:
        """
        大模型内容安全场景中，对用户输入的文本、图片、视频、文档等进行过滤、检测和识别，并根据安全策略进行相应的处理和响应。
        :param text:
        :param kwargs:
        :return:
        """
        # path = f"/audit/v3/aichat/inputMedia"

        raise NotImplementedError("IFlyAuditAPI.input_media is not implemented yet")

    async def output_media(self, text: str, **kwargs: Any) -> None:
        """
        大模型内容安全场景中，对大模型输出的图片、视频、音频等进行过滤、检测和识别，并根据安全策略进行相应的处理和响应。
        :param text:
        :param kwargs:
        :return:
        """
        # path = f"/audit/v3/aichat/outputMedia"
        raise NotImplementedError("IFlyAuditAPI.output_media is not implemented yet")

    async def know_ref(self, text: str, **kwargs: Any) -> None:
        """
        大模型内容安全场景中，对大模型答复过程中引用的网站、知识库等数据进行过滤、检测和识别，并根据安全策略进行相应的处理和响应。
        :param text:
        :param kwargs:
        :return:
        """
        # path = f"/audit/v3/aichat/knowRef"
        raise NotImplementedError("IFlyAuditAPI.know_ref is not implemented yet")
