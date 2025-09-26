from abc import ABC, abstractmethod
from enum import Enum
from typing import Any, List, Literal

from pydantic import BaseModel, Field

from workflow.extensions.otlp.trace.span import Span


class Stage(str, Enum):
    """
    Audit stage enumeration for different processing stages.
    """

    REASONING = "reasoning"  # Reasoning stage
    ANSWER = "answer"  # Answer stage


class ContentType(str, Enum):
    """
    Content type enumeration for different media types.
    """

    TEXT = "text"  # Text content
    IMAGE = "image"  # Image content
    VIDEO = "video"  # Video content
    AUDIO = "audio"  # Audio content


class ResourceList(BaseModel):
    """
    Resource information associated with Q&A, used to reduce context combination risks.

    This model represents multimedia resources that may be associated with user queries
    or assistant responses, providing additional context for audit systems to make
    more accurate security assessments.
    """

    # Unique resource identifier
    data_id: str
    # Resource type for audit, possible values: image, text, audio, video
    content_type: ContentType
    # Resource description information
    res_desc: str
    # OCR text from images
    ocr_text: str


class ContextList(BaseModel):
    """
    Historical conversation information in multi-turn dialogue scenarios,
    used to reduce context combination risks, passed in interactive dialogue order.

    This model captures the conversation history to provide context for audit systems,
    enabling them to detect potential security risks that might emerge from the
    combination of multiple conversation turns.
    """

    # Role to distinguish historical conversations. Possible values: user (user questions),
    # assistant (LLM answers), system (set LLM role)
    role: str
    # Historical audit text information
    content: str
    # Resource information associated with Q&A, used to reduce context combination risks
    resource_list: List[ResourceList] = Field(default_factory=list)


class AuditAPI(ABC):
    """
    Abstract base class for audit API implementations.

    This class defines the interface that all audit API implementations must follow.
    It provides a standardized way to interact with different audit systems for
    content security validation in LLM applications.
    """

    audit_name: str = "BaseAuditAPI"

    @abstractmethod
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
        In LLM content security scenarios, this interface is used to detect the safety
        of user input prompt content and provide corresponding handling suggestions
        for high-risk and medium-low-risk content.

        :param content: Content to be audited, text information that needs to be submitted for audit
        :param chat_sid: Current conversation ID, used to identify an LLM conversation.
                        Note: The chat_sid for this Q&A conversation must remain consistent
        :param span: Span object for tracking request context information
        :param chat_app_id: LLM application ID, passthrough parameter, upstream account identifier
                           assigned to LLM callers for distinguishing callers
        :param uid: User ID, passthrough parameter for distinguishing specific users
        :param template_id: Template ID for managing console-configured audit strategy templates.
                           One template ID is used for one LLM scenario, default uses standard strategy template
        :param context_list: Historical conversation information in multi-turn dialogue scenarios,
                            used to reduce context combination risks, passed in interactive dialogue order
        :param kwargs: Additional keyword arguments
        :return: None
        """
        raise NotImplementedError("Subclasses must implement this method")

    @abstractmethod
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
        In LLM content security scenarios, this interface is used to detect the safety
        of LLM output content and provide corresponding handling suggestions
        for high-risk and medium-low-risk content.

        :param stage: Audit stage, used to distinguish specific stages in different LLM usage scenarios,
                     providing more granular audit control based on different stages. Default value: answer
        :param content: Content to be audited, text that needs to be submitted for audit
        :param pindex: Output text fragment index, indicating which fragment the current text is,
                      starting from 1. Business parties are recommended to split fragments by ending punctuation or paragraphs
        :param span: Span object for tracking request context information
        :param is_pending: Incomplete fragment text content identifier, used for scenarios like first sentence quick display.
                          0: complete fragment (default), 1: incomplete fragment
        :param is_stage_end: Identifier for whether current audit stage fragment is the last fragment.
                            0: not the last segment (default), 1: last segment
        :param is_end: Identifier for whether current fragment is the last fragment.
                      0: not the last segment (default), 1: last segment
        :param chat_sid: Current conversation ID, used to identify an LLM conversation.
                        Note: The chat_sid for this Q&A conversation must remain consistent
        :param chat_app_id: LLM application ID, passthrough parameter, upstream account identifier
                           assigned to LLM callers for distinguishing callers
        :param uid: User ID, passthrough parameter for distinguishing specific users
        :param kwargs: Additional keyword arguments
        :return: None
        """
        raise NotImplementedError("Subclasses must implement this method")

    @abstractmethod
    async def input_media(self, text: str, **kwargs: Any) -> None:
        """
        In LLM content security scenarios, filter, detect and identify user input text,
        images, videos, documents, etc., and process and respond accordingly based on security policies.
        :param text: Text content to be processed
        :param kwargs: Additional keyword arguments
        :return: None
        """
        raise NotImplementedError("Subclasses must implement this method")

    @abstractmethod
    async def output_media(self, text: str, **kwargs: Any) -> None:
        """
        In LLM content security scenarios, filter, detect and identify LLM output images,
        videos, audio, etc., and process and respond accordingly based on security policies.
        :param text: Text content to be processed
        :param kwargs: Additional keyword arguments
        :return: None
        """
        raise NotImplementedError("Subclasses must implement this method")

    @abstractmethod
    async def know_ref(self, text: str, **kwargs: Any) -> None:
        """
        In LLM content security scenarios, filter, detect and identify websites, knowledge bases
        and other data referenced during LLM responses, and process and respond accordingly based on security policies.
        :param text: Text content to be processed
        :param kwargs: Additional keyword arguments
        :return: None
        """
        raise NotImplementedError("Subclasses must implement this method")
