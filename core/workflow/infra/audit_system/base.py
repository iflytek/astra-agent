"""
Base audit system classes and data models.

This module defines the core data structures and base classes used throughout
the audit system for content processing, validation, and result management.
"""

import asyncio
from typing import Any, Dict, Optional

from pydantic import BaseModel, Field
from workflow.exception.e import CustomException
from workflow.extensions.otlp.trace.span import Span
from workflow.infra.audit_system.audit_api.base import ContentType, ContextList, Stage
from workflow.infra.audit_system.enums import Status


class BaseFrameAudit(BaseModel):
    """
    Base frame audit object containing common attributes for audit operations.
    """

    # Content to be audited
    content: str

    # Current audit status of the content
    status: Status = Status.STOP


class InputFrameAudit(BaseFrameAudit):
    """
    Input frame audit object for processing input content.
    """

    content_type: ContentType = ContentType.TEXT
    context_list: list[ContextList] = Field(default_factory=list)


class OutputFrameAudit(BaseFrameAudit):
    """
    Output frame audit object for processing output content.
    """

    # Unique identifier for the audit frame (optional, but must be unique if provided)
    frame_id: str = ""

    stage: Stage

    # Original frame content
    source_frame: Any

    # Whether sentence splitting is not needed
    not_need_submit: bool = False

    # Whether empty frames need to be audited
    none_need_audit: bool = False

    class Config:
        arbitrary_types_allowed = True


class FrameAuditResult(BaseFrameAudit):
    """
    Frame audit result object containing audit status and content.
    """

    # Original frame content
    source_frame: Optional[Any] = None

    error: Optional[CustomException] = None

    class Config:
        arbitrary_types_allowed = True


class AuditContext(BaseModel):
    """
    Audit context maintaining current session state, including:
    - All frame concatenated content
    - Current first sentence cache
    - Whether frame audit is stopped due to a frame audit failure
    """

    chat_sid: str

    # Template ID for managing console-configured audit strategy templates.
    # One template ID is used for one LLM scenario, default uses standard strategy template.
    template_id: str = ""

    # LLM application ID, passthrough parameter for upstream account identification
    # to distinguish different callers.
    chat_app_id: str = ""

    # User ID, passthrough parameter for distinguishing specific users.
    uid: str = ""

    # Output queue storing FrameAuditResult data structures.
    # For streaming output content, this queue needs to be monitored.
    output_queue: asyncio.Queue = Field(default_factory=asyncio.Queue)

    # Audit result exception wrapper
    error: Optional[CustomException] = None

    # Output text fragment index, indicating which fragment the current text is,
    # starting from 1. Business parties are recommended to split fragments by
    # ending punctuation or paragraphs.
    pindex: int = 1

    # Whether the first sentence has been audited
    first_sentence_audited: bool = False

    # Whether blocked by sensitive words in a frame
    frame_blocked: bool = False

    # Content pending audit
    remaining_content: str = ""

    # All audit data
    all_content_frame_ids: list[str] = Field(default_factory=list)
    all_source_frames: Dict[str, OutputFrameAudit] = Field(default_factory=dict)

    # Audited content
    audited_content: str = ""
    # List of frame IDs that have completed audit
    audited_content_frame_ids: list[str] = Field(default_factory=list)

    # Frame IDs that have completed screen display
    frame_ids_on_screen: list[str] = Field(default_factory=list)

    # Last audit type
    last_content_stage: Optional[Stage] = None

    class Config:
        arbitrary_types_allowed = True

    def add_source_content(self, output_frame: OutputFrameAudit) -> None:
        """
        Add original frame content to the audit context.

        This method registers a new output frame in the context, tracking its
        frame ID and storing the frame data for later audit processing.

        :param output_frame: Output frame audit object containing content and metadata
        """
        if output_frame.frame_id not in self.all_content_frame_ids:
            self.all_content_frame_ids.append(output_frame.frame_id)
            self.audited_content_frame_ids.append(output_frame.frame_id)
            self.all_source_frames[output_frame.frame_id] = output_frame

    async def add_audited_content(self, span: Span) -> None:
        """
        Process and output content that has completed audit processing.

        This method iterates through audited content frames, matches them with
        the audited content, and outputs completed frames to the output queue.
        It maintains the order of content processing and tracks frame completion.

        :param span: Span object for tracking request context information and logging
        """
        is_all_consumer = True
        for idx, frame_id in enumerate(self.audited_content_frame_ids):
            output_frame_audit = self.all_source_frames[frame_id]
            if (
                output_frame_audit.content
                == self.audited_content[: len(output_frame_audit.content)]
            ):
                if output_frame_audit.frame_id not in self.frame_ids_on_screen:
                    frame_audit_result = FrameAuditResult(
                        content=output_frame_audit.content,
                        source_frame=output_frame_audit.source_frame,
                    )
                    await self.output_queue_put(frame_audit_result, span)
                self.audited_content = self.audited_content[
                    len(output_frame_audit.content) :
                ]
            else:
                self.audited_content_frame_ids = self.audited_content_frame_ids[idx:]
                is_all_consumer = False
                break
        if is_all_consumer:
            self.audited_content_frame_ids = []

    async def output_queue_put(
        self, frame_audit_result: FrameAuditResult, span: Span
    ) -> None:
        """
        Add audit result to the output queue for downstream processing.

        This method enqueues a completed audit result, logging the content
        and any associated errors for monitoring and debugging purposes.

        :param frame_audit_result: Frame audit result object containing processed content
        :param span: Span object for tracking request context information and logging
        """
        # Log content ready for display
        event = f"Content ready for display: {frame_audit_result.content}"
        if frame_audit_result.error:
            event = f"{event}, This display has risks, risk information: {frame_audit_result.error}"
        span.add_info_event(event)
        await self.output_queue.put(frame_audit_result)
