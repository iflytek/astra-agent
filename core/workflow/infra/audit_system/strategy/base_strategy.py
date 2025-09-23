from abc import ABC, abstractmethod
from typing import List

from workflow.extensions.otlp.trace.span import Span
from ..audit_api.base import AuditAPI
from ..base import AuditContext, InputFrameAudit, OutputFrameAudit


class AuditStrategy(ABC):
    """
    Abstract base class that defines the interface for audit strategies.

    This class provides the foundation for implementing different content
    audit strategies, including input and output content review mechanisms.
    """

    def __init__(
        self,
        chat_sid: str,
        audit_apis: List[AuditAPI],
        template_id: str = "",
        chat_app_id: str = "",
        uid: str = "",
    ) -> None:
        """
        Initialize the audit strategy.

        :param chat_sid: Chat session ID for identifying the current chat session
        :param audit_apis: List of audit API instances to be called for content review
        :param template_id: Template ID for managing console-configured audit strategy templates.
                           Each LLM scenario uses one template ID. Default uses standard strategy template
        :param chat_app_id: LLM application ID, passed through parameter for identifying the caller
                           assigned by upstream to the LLM calling party
        :param uid: User ID, passed through parameter for identifying specific users
        """
        self.context = AuditContext(
            chat_sid=chat_sid, template_id=template_id, chat_app_id=chat_app_id, uid=uid
        )
        self.audit_apis = audit_apis

    @abstractmethod
    async def input_review(self, input_frame: InputFrameAudit, span: Span) -> None:
        """
        Input content review logic that subclasses must implement.

        :param input_frame: Input frame containing content to be audited
        :param span: Span object for tracking request context information
        :return: None
        """
        raise NotImplementedError("Subclasses must implement this method")

    @abstractmethod
    async def output_review(self, output_frame: OutputFrameAudit, span: Span) -> None:
        """
        Output content review logic that subclasses must implement.

        :param output_frame: Output frame containing content to be audited
        :param span: Span object for tracking request context information
        :return: None
        """
        raise NotImplementedError("Subclasses must implement this method")
