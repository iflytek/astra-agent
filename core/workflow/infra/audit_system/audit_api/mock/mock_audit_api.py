import logging
from typing import Any, Dict, List, Literal

from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
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
RETRY_COUNT = 1


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


class MockAuditAPI(AuditAPI):
    """
    Mock audit API implementation for testing purposes.

    This class provides a mock implementation of the AuditAPI interface for
    development and testing scenarios. It simulates audit responses without
    making actual API calls to external services.
    """

    audit_name = "MockAuditAPI"

    hosts = ["http://mock-audit-api.com"]

    def _authorization(
        self, url: str, method: str, chat_app_id: str = "", uid: str = ""
    ) -> str:
        """
        Generate authorized request URL with necessary authentication parameters.

        Mock implementation that simply returns the original URL without
        any authentication processing for testing purposes.

        :param url: Base URL for the request
        :param method: HTTP method (not used in mock implementation)
        :param chat_app_id: Chat application ID (not used in mock implementation)
        :param uid: User ID (not used in mock implementation)
        :return: Original URL without modifications
        """
        return url

    async def _post(
        self, path: str, payload: dict, chat_app_id: str = "", uid: str = ""
    ) -> dict:
        """
        Asynchronously send POST request to audit API and handle response.

        Mock implementation that simulates audit API responses for testing.
        Returns safe content for normal text and unsafe action for content
        containing the word "敏感" (sensitive).

        :param path: API endpoint path (not used in mock implementation)
        :param payload: Request payload containing content to be audited
        :param chat_app_id: Chat application ID (not used in mock implementation)
        :param uid: User ID (not used in mock implementation)
        :return: Mock response result with audit action and content
        """
        logging.info(f"\nMockAuditAPI._post payload: {payload}")
        return {
            "data": {
                "action": (
                    ActionEnum.NONE
                    if "敏感" not in payload.get("content", "")
                    else ActionEnum.DISCONTINUE
                ),
                "content": payload.get("content", ""),
                "stage": Stage.ANSWER.value,
            },
            "code": CodeEnum.Success.code,
            "message": "Mock response message",
        }

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
        Mock audit for user input text content.

        Simulates the audit process for user input text by sending a mock request
        and raising an exception if the content is deemed unsafe.

        :param content: User input text content to be audited
        :param chat_sid: Unique conversation session identifier
        :param span: Span object for request tracking and logging
        :param chat_app_id: Application identifier for audit context
        :param uid: User identifier for audit context
        :param template_id: Audit template ID for custom security policies
        :param context_list: Historical conversation context for multi-turn dialogue
        :param kwargs: Additional keyword arguments
        :raises CustomException: If mock audit result indicates unsafe content
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

        resp = await self._post("/audit/v3/aichat/input", payload, chat_app_id, uid)
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
        Mock audit for LLM output text content.

        Simulates the audit process for LLM-generated text content by sending a mock request
        and raising an exception if the content is deemed unsafe.

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
        :raises CustomException: If mock audit result indicates unsafe content
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
        resp = await self._post("/audit/v3/aichat/output", payload, chat_app_id, uid)
        logging.info(f"\nMockAuditAPI.output_text resp: {resp}")
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
        raise NotImplementedError("MockAuditAPI.input_media is not implemented yet")

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
        raise NotImplementedError("MockAuditAPI.output_media is not implemented yet")

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
        raise NotImplementedError("MockAuditAPI.know_ref is not implemented yet")
