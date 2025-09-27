"""
Audit orchestrator for managing audit operations.

This module provides the main orchestrator class that coordinates audit operations
between different audit strategies and manages the flow of content through the
audit pipeline.
"""

import os

from workflow.extensions.otlp.trace.span import Span

from .base import FrameAuditResult, InputFrameAudit, OutputFrameAudit
from .enums import Status
from .strategy.base_strategy import AuditStrategy


class AuditOrchestrator:
    """
    Unified audit orchestrator for managing audit operations.

    This class serves as the central coordinator for all audit operations,
    delegating specific audit tasks to configured audit strategies while
    maintaining consistent error handling and logging across the system.
    """

    def __init__(self, audit_strategy: AuditStrategy):
        """
        Initialize the audit orchestrator with a specific audit strategy.

        :param audit_strategy: The audit strategy implementation to use for processing content
        """
        self.audit_strategy = audit_strategy

    async def process_output(self, output_frame: OutputFrameAudit, span: Span) -> None:
        """
        Process output content audit logic.

        This method handles the audit processing of output frames, including
        validation of empty frames and delegation to the configured audit strategy
        for content review and processing.

        :param output_frame: Output frame containing content to be audited
        :param span: Span object for tracking request context information and logging
        """
        # Check for existing audit errors before processing
        if self.audit_strategy.context.error:
            raise self.audit_strategy.context.error
        with span.start(
            f"audit_orchestrator.process_output::frame_id:{output_frame.frame_id}"
        ) as context_span:
            context_span.add_info_event(
                f"Frame content for audit: {output_frame.dict()}"
            )

            # If content is empty, return directly
            if (
                output_frame.content == ""
                and output_frame.status != Status.STOP
                and not output_frame.none_need_audit
                or (int(os.getenv("AUDIT_ENABLE", "0")) == 0)
            ):
                context_span.add_info_event(
                    "↑↑↑↑↑↑↑↑↑↑↑ This frame is empty, skipping audit or audit is disabled ↑↑↑↑↑↑↑↑↑↑↑"
                )
                await self.audit_strategy.context.output_queue_put(
                    FrameAuditResult(
                        content=output_frame.content,
                        status=output_frame.status,
                        source_frame=output_frame.source_frame,
                    ),
                    context_span,
                )
                return

            return await self.audit_strategy.output_review(output_frame, context_span)

    async def process_input(self, input_frame: InputFrameAudit, span: Span) -> None:
        """
        Process input content audit logic.

        This method delegates input frame processing to the configured audit strategy
        for content validation and processing.

        :param input_frame: Input frame containing content to be audited
        :param span: Span object for tracking request context information and logging
        """
        if int(os.getenv("AUDIT_ENABLE", "0")) == 0:
            return None

        return await self.audit_strategy.input_review(input_frame, span)
