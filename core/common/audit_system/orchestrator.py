"""
orchestrator.py
"""

from typing import TYPE_CHECKING

from common.audit_system.base import FrameAuditResult, InputFrameAudit, OutputFrameAudit
from common.audit_system.enums import Status
from common.audit_system.strategy.base_strategy import AuditStrategy

if TYPE_CHECKING:
    from common.otlp.trace.span import Span


class AuditOrchestrator:
    """
    审核统一调度器。
    """

    def __init__(self, audit_strategy: AuditStrategy):
        self.audit_strategy = audit_strategy

    async def process_output(
        self, output_frame: OutputFrameAudit, span: "Span"
    ) -> None:
        """
        处理输出内容的审核逻辑。
        :param output_frame:
        :param span:
        :return:
        """
        # print(output_frame)
        if self.audit_strategy.context.error:
            raise self.audit_strategy.context.error
        with span.start(
            f"audit_orchestrator.process_output::frame_id:{output_frame.frame_id}"
        ) as context_span:
            context_span.add_info_event(f"送审帧内容：{output_frame.dict()}")

            if (
                output_frame.content == ""
                and output_frame.status != Status.STOP
                and not output_frame.none_need_audit
            ):
                context_span.add_info_event(
                    "↑↑↑↑↑↑↑↑↑↑↑ 该帧为空针，不送审 ↑↑↑↑↑↑↑↑↑↑↑"
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

    async def process_input(self, input_frame: InputFrameAudit, span: "Span") -> None:
        """
        处理输出内容的审核逻辑。
        :param input_frame:
        :param span:
        :return:
        """
        return await self.audit_strategy.input_review(input_frame, span)
