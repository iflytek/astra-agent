import asyncio
from typing import Literal, cast

from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.trace.span import Span

from ..audit_api.base import ContentType, Stage
from ..base import FrameAuditResult, InputFrameAudit, OutputFrameAudit
from ..enums import Status
from ..strategy.base_strategy import AuditStrategy
from ..utils import ALL_SENTENCE_LEN, Sentence

WORKFLOW_MAX_SENTENCE_LEN = 1500


class TextAuditStrategy(AuditStrategy):
    """
    Text audit strategy implementation including frame audit and first sentence audit.

    This strategy handles text content review for both input and output frames,
    supporting different audit modes based on content characteristics.
    """

    async def input_review(self, input_frame: InputFrameAudit, span: Span) -> None:
        """
        Input content review logic for text content.

        :param input_frame: Input frame containing text content to be audited
        :param span: Span object for tracking request context information
        :return: None
        """
        # Text content audit
        if input_frame.content_type == ContentType.TEXT:
            for audit_api in self.audit_apis:
                # Call audit API for content review
                await audit_api.input_text(
                    content=input_frame.content,
                    chat_sid=self.context.chat_sid,
                    span=span,
                    chat_app_id=self.context.chat_app_id,
                    uid=self.context.uid,
                    template_id=self.context.template_id,
                    context_list=input_frame.context_list,
                )
        else:
            raise ValueError("Unsupported content type for input review")

    async def output_review(self, output_frame: OutputFrameAudit, span: Span) -> None:
        """
        Text output review logic, divided into first sentence audit and sentence-by-sentence audit.

        :param output_frame: Output frame containing content to be audited
        :param span: Span object for tracking request context information
        :return: None
        """

        if not self.context.last_content_stage:
            self.context.last_content_stage = output_frame.stage

        if self.context.last_content_stage == output_frame.stage:
            self.context.remaining_content += output_frame.content

        self.context.add_source_content(output_frame)

        # If first sentence not audited, perform intermediate frame audit
        if not self.context.first_sentence_audited:
            span.add_info_event("↓↓↓↓↓↓↓↓↓↓↓ First Sentence Audit ↓↓↓↓↓↓↓↓↓↓↓")
            await self._first_sentence_audit(output_frame, span)

        # After first sentence audit, proceed with sentence-by-sentence audit
        else:
            span.add_info_event("↓↓↓↓↓↓↓↓↓↓↓ Sentence-by-Sentence Audit ↓↓↓↓↓↓↓↓↓↓↓")
            await self._sentence_audit(output_frame, span)

    async def _first_sentence_audit(
        self, output_frame: OutputFrameAudit, span: Span
    ) -> None:
        """
        First sentence audit logic.

        1. When transitioning between answer and reasoning_content, set the corresponding
           is_stage_end parameter to 1
        2. When transitioning between answer and reasoning_content, if the returned content
           contains neither ending punctuation nor non-ending punctuation, concatenate the
           previous answer or reasoning_content to form the first sentence or sentence-by-sentence
           audit. This situation may result in missed audits. For example, "sensitive words"
           would be displayed normally.

        :param output_frame: Output frame containing content to be audited
        :param span: Span object for tracking request context information
        :return: None
        """

        # First sentence judgment conditions: has ending punctuation, or content length exceeds threshold,
        # or current frame audit stage differs from last audit stage
        first_sentence_conditions = (
            Sentence.has_end_symbol(self.context.remaining_content)
            or len(self.context.remaining_content) > WORKFLOW_MAX_SENTENCE_LEN
            or self.context.last_content_stage != output_frame.stage
            or output_frame.status == Status.STOP
        )

        if self.context.last_content_stage == output_frame.stage:
            fallback_length = (
                WORKFLOW_MAX_SENTENCE_LEN
                if output_frame.status != Status.STOP
                else ALL_SENTENCE_LEN
            )
        else:
            fallback_length = ALL_SENTENCE_LEN

        # If intermediate frame is audited, subsequent frames will not be audited until first frame is obtained
        if self.context.frame_blocked:
            span.add_info_event(
                "Intermediate frame audited, subsequent frames not audited until first frame"
            )
            if first_sentence_conditions:
                await self.__first_sentence_audit(output_frame, span, fallback_length)
            else:
                pass

        # Extract non-ending punctuation marks for audit
        elif first_sentence_conditions:
            await self.__first_sentence_audit(output_frame, span, fallback_length)

        # If first sentence audit conditions are not met, continue concatenating content for intermediate frame audit
        else:
            span.add_info_event(
                "First sentence audit conditions not met, continue concatenating content for intermediate frame audit"
            )
            frame_audit_result = FrameAuditResult(
                content=output_frame.content, source_frame=output_frame.source_frame
            )
            span.add_info_event(f"Audit frame content: {output_frame}")
            try:
                for audit_api in self.audit_apis:
                    span.add_info_event(f"Current audit API: {audit_api.audit_name}")
                    await audit_api.output_text(
                        stage=output_frame.stage,
                        content=self.context.remaining_content,
                        pindex=self.context.pindex,
                        span=span,
                        is_pending=1,  # Fragment audit
                        is_stage_end=0,  # Current stage not ended
                        is_end=0,  # Overall not ended
                        chat_sid=self.context.chat_sid,
                        chat_app_id=self.context.chat_app_id,
                        uid=self.context.uid,
                    )
                self.context.frame_ids_on_screen.append(output_frame.frame_id)
                self.context.pindex += 1
                await self.context.output_queue_put(frame_audit_result, span)
            except Exception:
                self.context.frame_blocked = True

        if self.context.last_content_stage != output_frame.stage:
            self.context.last_content_stage = output_frame.stage
            await self.output_review(output_frame, span)

    async def __first_sentence_audit(
        self, output_frame: OutputFrameAudit, span: Span, fallback_length: int
    ) -> None:
        span.add_info_event(
            "First sentence audit conditions met, proceeding with first sentence audit"
        )
        # If current frame does not need sentence splitting, audit all content
        if output_frame.not_need_submit:
            sentences = [self.context.remaining_content]
            self.context.remaining_content = ""
        else:
            sentences, self.context.remaining_content = Sentence.find_valid_sentence(
                self.context.remaining_content, fallback_length=fallback_length
            )
        sentences = (
            [""]
            if (
                (
                    # End frame or reasoning and answer frames change, but sentence content is empty,
                    # need to provide an empty sentence for audit
                    output_frame.status == Status.STOP
                    or self.context.last_content_stage != output_frame.stage
                )
                and not sentences
            )
            else sentences
        )
        span.add_info_event("Sentence splitting results:")
        span.add_info_events(
            {
                "sentences": sentences,
                "remaining_content": self.context.remaining_content,
            }
        )
        # For first sentence audit, pindex needs to be reset to 1
        self.context.pindex = 1
        await self._audit_api_output_text_async(sentences, output_frame, span)
        self.context.first_sentence_audited = True

    async def _sentence_audit(self, output_frame: OutputFrameAudit, span: Span) -> None:
        """
        Sentence-by-sentence audit logic.

        :param output_frame: Output frame containing content to be audited
        :param span: Span object for tracking request context information
        :return: None
        """

        # Audit content
        if (
            Sentence.has_end_symbol(self.context.remaining_content)
            or len(self.context.remaining_content) > WORKFLOW_MAX_SENTENCE_LEN
            or self.context.last_content_stage != output_frame.stage
            or output_frame.status == Status.STOP
        ):
            sentences = []

            # If current frame does not need sentence splitting, audit all content
            if output_frame.not_need_submit:
                sentences = [self.context.remaining_content]
                self.context.remaining_content = ""

            # If current frame audit stage differs from last audit stage, need to split remaining text and audit all
            elif (
                self.context.last_content_stage != output_frame.stage
                or output_frame.status == Status.STOP
            ):
                while True:
                    sentences_temp, self.context.remaining_content = (
                        Sentence.find_valid_sentence(
                            self.context.remaining_content, WORKFLOW_MAX_SENTENCE_LEN
                        )
                    )
                    sentences.extend(sentences_temp)
                    if not self.context.remaining_content:
                        break
                # Current frame audit stage differs from last audit stage, and no audit content, need to add empty frame for is_stage_end audit
                if not sentences:
                    sentences.append("")
            else:
                sentences, self.context.remaining_content = (
                    Sentence.find_valid_sentence(
                        self.context.remaining_content, WORKFLOW_MAX_SENTENCE_LEN
                    )
                )

            # End frame audit content may be empty, need to add empty string
            if (
                output_frame.status == Status.STOP
                and not sentences
                and not self.context.remaining_content
            ):
                sentences.append("")

            await self._audit_api_output_text_async(sentences, output_frame, span)

        # After all inconsistent stages are audited, audit the current audit frame
        if self.context.last_content_stage != output_frame.stage:
            self.context.last_content_stage = output_frame.stage
            await self.output_review(output_frame, span)

    async def _audit_api_output_text_async(
        self, sentences: list[str], output_frame: OutputFrameAudit, span: Span
    ) -> None:
        """
        Asynchronous text output audit API call.

        :param sentences: List of sentences to be audited
        :param output_frame: Current frame information
        :param span: Span object for tracking request context information
        :return: None
        """
        current_status = output_frame.status
        pindex = self.context.pindex
        audit_tasks = []

        for idx, sentence in enumerate(sentences):
            # If current frame is end frame, then the last sentence needs to be marked as end frame
            if current_status == Status.STOP:
                output_frame.status = (
                    Status.STOP if idx == len(sentences) - 1 else Status.NONE
                )

            audit_tasks.append(
                asyncio.create_task(
                    self._audit_api_output_text(
                        output_frame.stage,
                        sentence,
                        span,
                        pindex + idx + 1,
                        current_status=output_frame.status,
                    ),
                )
            )
        _ = await asyncio.gather(*audit_tasks)
        self.context.pindex += len(sentences)
        self.context.audited_content += "".join(sentences)
        for _ in range(len(sentences)):
            await self.context.add_audited_content(span)

    async def _audit_api_output_text(
        self,
        current_stage: Stage,
        need_audit_content: str,
        span: Span,
        pindex: int,
        current_status: Status = Status.NONE,
    ) -> None:
        """
        Text output audit API call.

        :param current_stage: Current audit stage
        :param need_audit_content: Content that needs to be audited
        :param span: Span object for tracking request context information
        :param pindex: Position index for the content
        :param current_status: Current status of the frame
        :return: None
        """
        if self.context.error:
            span.add_info_event(
                f"Audit context error: {self.context.error}, subsequent frames will not be audited"
            )
            return

        span.add_info_event(f"Current audit content: {need_audit_content}")
        frame_audit_result = FrameAuditResult(
            content=need_audit_content, status=current_status
        )

        is_end: Literal[0, 1] = 0
        is_stage_end: Literal[0, 1] = 0

        if self.context.last_content_stage == current_stage:
            is_end = 1 if current_status == Status.STOP else 0

            if current_status == Status.STOP:
                is_stage_end = 1

        if self.context.last_content_stage != current_stage:
            is_stage_end = 1

        try:
            for audit_api in self.audit_apis:
                span.add_info_event(f"Current audit API: {audit_api.audit_name}")
                last_content_stage = cast(Stage, self.context.last_content_stage)
                await audit_api.output_text(
                    stage=(
                        current_stage
                        if last_content_stage == current_stage
                        else last_content_stage
                    ),
                    content=need_audit_content,
                    pindex=pindex,
                    span=span,
                    is_pending=0,  # First sentence audit does not need to be marked as incomplete
                    is_stage_end=is_stage_end,
                    is_end=is_end,
                    chat_sid=self.context.chat_sid,
                    chat_app_id=self.context.chat_app_id,
                    uid=self.context.uid,
                )
        except CustomException as e:
            frame_audit_result.error = e
            span.add_error_event(f"Audit API call exception: {str(e)}")
            await self.context.output_queue_put(frame_audit_result, span)
        except Exception as e:
            span.add_error_event(f"Audit API call exception: {str(e)}")
            frame_audit_result.error = CustomException(
                CodeEnum.AUDIT_ERROR, cause_error=f"Audit result exception: {str(e)}"
            )
            await self.context.output_queue_put(frame_audit_result, span)
        finally:
            if frame_audit_result.error:
                self.context.error = frame_audit_result.error
