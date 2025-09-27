"""Workflow Agent API endpoints."""

from typing import Annotated, Any, AsyncGenerator, cast

from fastapi import APIRouter, Header
from pydantic import ConfigDict
from starlette.responses import StreamingResponse

from api.schemas.completion_chunk import ReasonChatCompletionChunk
from api.schemas.workflow_agent_inputs import CustomCompletionInputs
from api.v1.base_api import CompletionBase
from common_imports import Span
from engine.workflow_agent_runner import WorkflowAgentRunner
from service.builder.workflow_agent_builder import WorkflowAgentRunnerBuilder

workflow_agent_router = APIRouter(prefix="/agent/v1")

headers = {"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}


class CustomChatCompletion(CompletionBase):
    """Custom chat completion for workflow agents."""

    bot_id: str
    uid: str
    question: str
    model_config = ConfigDict(arbitrary_types_allowed=True)
    span: Span

    def __init__(self, inputs: CustomCompletionInputs, **data: Any) -> None:
        super().__init__(inputs=inputs, **data)

    async def build_runner(self, span: Span) -> WorkflowAgentRunner:
        """Build WorkflowAgentRunner"""
        builder = WorkflowAgentRunnerBuilder(
            app_id=self.app_id,
            uid=self.uid,
            span=span,
            inputs=cast(CustomCompletionInputs, self.inputs),
        )
        return await builder.build()

    async def do_complete(self) -> AsyncGenerator[str, None]:
        """Run agent"""

        with self.span.start("WorkflowAgentNode") as sp:
            sp.set_attributes(
                attributes={
                    "app_id": self.app_id,
                    "bot_id": self.bot_id,
                    "uid": self.uid,
                }
            )
            node_trace = await self.build_node_trace(bot_id=self.bot_id, span=sp)
            meter = await self.build_meter(sp)

            # Use parent class run_runner method which includes _finalize_run logic
            async for response in self.run_runner(node_trace, meter, span=sp):
                yield response


@workflow_agent_router.post(
    "/custom/chat/completions",
    description="Agent execution - user mode",
    response_model=None,
)
async def custom_chat_completions(
    x_consumer_username: Annotated[str, Header()],
    completion_inputs: CustomCompletionInputs,
) -> StreamingResponse:
    """Agent execution - user mode

    Args:
        completion_inputs: Request body
        app_id: Application ID
        bot_id: Bot ID
        uid: User ID
        span: Trace object

    Returns:
        Streaming response
    """

    span = Span(app_id=x_consumer_username, uid=completion_inputs.uid)
    completion = CustomChatCompletion(
        app_id=x_consumer_username,
        inputs=completion_inputs,
        log_caller=completion_inputs.meta_data.caller,
        span=span,
        bot_id="",
        uid=completion_inputs.uid,
        question=completion_inputs.get_last_message_content(),
    )

    async def generate() -> AsyncGenerator[str, None]:
        """Generator for streaming response."""
        async for response in completion.do_complete():
            # Convert chunk to JSON string for streaming response
            yield response

    return StreamingResponse(
        generate(),
        media_type="application/json",
        headers=headers,
    )
