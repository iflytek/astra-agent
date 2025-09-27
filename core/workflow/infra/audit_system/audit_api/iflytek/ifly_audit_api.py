import asyncio
import base64
import hmac
import os
import uuid
from collections import OrderedDict
from datetime import datetime, timezone
from typing import Any, Dict, List, Literal
from urllib.parse import quote, urlencode

import aiohttp
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.exception.errors.third_api_code import ThirdApiCodeEnum
from workflow.extensions.otlp.trace.span import Span
from workflow.infra.audit_system.audit_api.base import AuditAPI, Stage
from workflow.infra.audit_system.base import ContextList

# Connection timeout in seconds for establishing HTTP connections
CONNECT_TIMEOUT = 1

# Text content read timeout in seconds for audit API requests
TEXT_READ_TIMEOUT = 6

# Image content read timeout in seconds for media audit API requests
IMAGE_READ_TIMEOUT = 10

# Maximum number of retry attempts for failed requests
RETRY_COUNT = 2


class ActionEnum:
    """
    Audit action enumeration class.

    Defines the possible actions that can be taken by the audit system
    based on the security assessment results of content.
    """

    # Audit result is normal, process continues normally
    NONE = "none"

    # Enhanced prompt, provides prompt optimization information,
    # generates new safe response information based on new prompt
    FORTIFY_PROMPT = "fortify_prompt"

    # Re-answer, LLM output content has risks, generates new safe response information
    # based on new prompt or new model
    REANSWER = "reanswer"

    # Safe LLM diversion, uses the safe LLM returned by the interface as the target LLM
    # to regenerate answer content
    SAFE_MODEL = "safe_model"

    # Safe reply, uses the default reply returned by the interface as answer content for display (not supported yet)
    SAFE_ANSWER = "safe_answer"

    # Refuse to answer and terminate multi-turn dialogue
    DISCONTINUE = "discontinue"

    # Red line mandatory answer, uses the red line mandatory answer field returned by the interface
    # as answer content for display and terminates multi-turn dialogue (not supported yet)
    REDLINE = "redline"

    # Content not displayed, current LLM output content is not displayed, subsequent process continues.
    # Example scenario: model output thinking content identified as risky, thinking content not displayed,
    # answer process continues normally
    HIDE_CONTINUE = "hide_continue"

    # Content not referenced, current content with risks is not referenced when answering
    NONREFERENCE = "nonreference"


class IFlyAuditAPI(AuditAPI):
    """
    IFlyTek audit API implementation for content security.

    This class provides the concrete implementation of the AuditAPI interface
    for IFlyTek's content security audit service. It handles authentication,
    request formatting, and response processing for various audit operations.
    """

    audit_name = "IFlyAuditAPI"

    def __init__(self) -> None:
        """
        Initialize IFlyTek audit API with environment variables.

        Loads configuration from environment variables including app ID, access keys,
        and host endpoints. Validates that all required configuration is present.

        :raises ValueError: If required environment variables are missing
        """
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

        if missing and int(os.getenv("AUDIT_ENABLE", "0")) == 1:
            raise ValueError(
                f"Missing required environment variables: {', '.join(missing)}"
            )

    def _signature(self, query_param: dict) -> str:
        """
        Generate HMAC-SHA1 signature for request authentication.

        Creates a cryptographic signature using HMAC-SHA1 algorithm based on
        the sorted query parameters. This signature is used to authenticate
        requests to the IFlyTek audit API.

        :param query_param: Query parameters dictionary to be signed
        :return: Base64 encoded signature string for API authentication
        """
        # Use ordered dictionary to simulate TreeMap (sorted by key)
        sorted_params = OrderedDict(sorted(query_param.items()))

        # Remove signature parameter
        sorted_params.pop("signature", None)

        # Construct base string
        builder = []
        for key, value in sorted_params.items():
            if value is not None and value != "":
                encoded_value = quote(
                    value, safe=""
                )  # Equivalent to URLEncoder.encode(..., "UTF-8")
                builder.append(f"{key}={encoded_value}")

        base_string = "&".join(builder)
        # print(f"baseString：{base_string}")

        # HMAC-SHA1 signature and Base64 encoding
        mac = hmac.new(
            self.access_key_secret.encode("utf-8"),
            base_string.encode("utf-8"),
            digestmod="sha1",
        )
        signature_bytes = mac.digest()
        return base64.b64encode(signature_bytes).decode("utf-8")

    def _gen_req_url(self, url: str, chat_app_id: str = "", uid: str = "") -> str:
        """
        Generate authorized request URL with necessary authentication parameters.

        Constructs a complete URL with all required authentication parameters
        including app ID, access key, timestamp, UUID, and HMAC signature.

        :param url: Base URL for the API endpoint
        :param chat_app_id: Chat application ID for request identification
        :param uid: User ID for request identification
        :return: Complete URL with authentication parameters appended as query string
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
        Asynchronously send POST request to audit API and handle response.

        Sends authenticated POST requests to the IFlyTek audit API with retry logic
        and comprehensive error handling. Supports multiple host endpoints for
        high availability.

        :param path: API endpoint path to append to the base URL
        :param payload: Request payload data to send in JSON format
        :param span: Span object for tracking request context information and logging
        :param chat_app_id: Chat application ID for request identification
        :param uid: User ID for request identification
        :return: Response result dictionary containing audit results
        :raises CustomException: If all retry attempts fail or API returns error status
        """
        span.add_info_event(f"Audit request body: {payload}")
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
                            f"Request URL: {url}, retry count: {current_retry}/{RETRY_COUNT}"
                        )
                        async with session.post(url, json=payload) as response:
                            cause_error = (
                                f"Status code: {response.status}, "
                                f"Response content: {await response.text()}"
                            )

                            if response.status != 200:
                                span.add_error_event(cause_error)
                                if current_retry < RETRY_COUNT:
                                    continue
                                else:
                                    raise CustomException(
                                        CodeEnum.AUDIT_SERVER_ERROR,
                                        cause_error=cause_error,
                                    )

                            resp_json = await response.json()
                            span.add_info_event(f"Audit response body: {resp_json}")

                            code = resp_json.get("code", -1)
                            if int(code) == ThirdApiCodeEnum.SUCCESS.code:
                                return resp_json
                            if (
                                int(code) == ThirdApiCodeEnum.AUDIT_ERROR.code
                                and current_retry < RETRY_COUNT
                            ):
                                continue
                            else:
                                cause_error = (
                                    f"Request failed, status code: {response.status}, "
                                    f"Response content: {await response.text()}, "
                                    f"Response body: {resp_json}"
                                )
                                raise CustomException(
                                    CodeEnum.AUDIT_SERVER_ERROR, cause_error=cause_error
                                )

                    except (aiohttp.ClientError, asyncio.TimeoutError, Exception) as e:
                        span.record_exception(e)
                        if current_retry < RETRY_COUNT:
                            span.add_info_event(
                                f"Request failed for the {current_retry}th time: {e}"
                            )
                            continue
                        else:
                            raise CustomException(
                                CodeEnum.AUDIT_SERVER_ERROR, cause_error=str(e)
                            ) from e
                    finally:
                        current_retry += 1
        raise ValueError("Audit post error")

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
        """
        Audit user input text content for security compliance.

        Sends user input text to the IFlyTek audit API for security assessment.
        Raises an exception if the content is deemed unsafe or requires special handling.

        :param content: User input text content to be audited
        :param chat_sid: Unique conversation session identifier
        :param span: Span object for request tracking and logging
        :param chat_app_id: Application identifier for audit context
        :param uid: User identifier for audit context
        :param template_id: Audit template ID for custom security policies
        :param context_list: Historical conversation context for multi-turn dialogue
        :param kwargs: Additional keyword arguments
        :raises CustomException: If audit result indicates unsafe content
        """

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
                        res.dict() for res in ctx.resource_list
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
            raise CustomException(
                CodeEnum.AUDIT_INPUT_ERROR,
                cause_error=f"Audit result abnormal: {resp}",
            )

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
        """
        Audit LLM output text content for security compliance.

        Sends LLM-generated text content to the IFlyTek audit API for security assessment.
        Supports streaming content with fragment indexing and stage management.

        :param stage: Audit stage (reasoning or answer) for context-specific processing
        :param content: LLM output text content to be audited
        :param pindex: Fragment index indicating position in streaming content
        :param span: Span object for request tracking and logging
        :param is_pending: Flag indicating if this is an incomplete fragment (0=complete, 1=incomplete)
        :param is_stage_end: Flag indicating if this is the last fragment of the current stage
        :param is_end: Flag indicating if this is the final fragment of the entire response
        :param chat_sid: Unique conversation session identifier
        :param chat_app_id: Application identifier for audit context
        :param uid: User identifier for audit context
        :param kwargs: Additional keyword arguments
        :raises CustomException: If audit result indicates unsafe content
        """
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
            raise CustomException(
                CodeEnum.AUDIT_OUTPUT_ERROR,
                cause_error=f"Audit result abnormal: {resp}",
            )

    async def input_media(self, text: str, **kwargs: Any) -> None:
        """
        In LLM content security scenarios, filter, detect and identify user input text,
        images, videos, documents, etc., and process and respond accordingly based on security policies.
        :param text: Text content to be processed
        :param kwargs: Additional keyword arguments
        :return: None
        """
        # path = f"/audit/v3/aichat/inputMedia"

        # TODO: To be implemented
        raise NotImplementedError("IFlyAuditAPI.input_media is not implemented yet")

    async def output_media(self, text: str, **kwargs: Any) -> None:
        """
        In LLM content security scenarios, filter, detect and identify LLM output images,
        videos, audio, etc., and process and respond accordingly based on security policies.
        :param text: Text content to be processed
        :param kwargs: Additional keyword arguments
        :return: None
        """
        # path = f"/audit/v3/aichat/outputMedia"
        # TODO: To be implemented
        raise NotImplementedError("IFlyAuditAPI.output_media is not implemented yet")

    async def know_ref(self, text: str, **kwargs: Any) -> None:
        """
        In LLM content security scenarios, filter, detect and identify websites, knowledge bases
        and other data referenced during LLM responses, and process and respond accordingly based on security policies.
        :param text: Text content to be processed
        :param kwargs: Additional keyword arguments
        :return: None
        """
        # path = f"/audit/v3/aichat/knowRef"
        # TODO: To be implemented
        raise NotImplementedError("IFlyAuditAPI.know_ref is not implemented yet")
