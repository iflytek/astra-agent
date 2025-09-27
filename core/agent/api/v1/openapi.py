import json
from typing import Annotated, Any, AsyncGenerator

from fastapi import APIRouter, Header
from starlette.responses import StreamingResponse

from api.schemas.agent_response import cur_timestamp
from api.schemas.completion import (
    ReasonChatCompletion,
    ReasonChoice,
    ReasonChoiceMessage,
    ReasonChoiceMessageToolCall,
    ReasonChoiceMessageToolCallFunction,
)
from api.schemas.completion_chunk import ReasonChatCompletionChunk
from api.schemas.openapi_inputs import CompletionInputs
from api.v1.base_api import CompletionBase
from common_imports import Span
from service.builder.openapi_builder import OpenAPIRunnerBuilder
from service.runner.openapi_runner import OpenAPIRunner

openapi_router = APIRouter(prefix="/agent/v1")

headers = {"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}

# key is the actual caller passed by the upstream, value is used for data aggregation
log_callers = {
    "workflow": "chat_workflow_agent",  # Workflow agent call
    "openapi": "chat_open_api",  # External OpenAPI source
    "xingchen-chat": "chat",  # Xingchen Chat
    "xingchen-debug": "chat_debug",  # Xingchen Debug
}


class ChatCompletion(CompletionBase):
    app_id: str
    span: Span

    inputs: CompletionInputs  # pyright: ignore[reportIncompatibleVariableOverride]

    async def stream_completion(
        self,
    ) -> AsyncGenerator[str, None]:
        """Run agent"""

        with self.span.start(self.log_caller) as sp:
            sp.set_attributes(
                attributes={
                    "app_id": self.app_id,
                    "bot_id": self.inputs.bot_id,
                    "uid": self.inputs.uid,
                    "question": self.inputs.get_last_message_content(),
                    "caller": self.inputs.meta_data.caller,
                    "caller_sid": self.inputs.meta_data.caller_sid,
                }
            )

            sp.add_info_events(
                attributes={"request-inputs": self.inputs.model_dump_json()}
            )

            node_trace = await self.build_node_trace(bot_id=self.inputs.bot_id, span=sp)
            meter = await self.build_meter(sp)

            async for response in self.run_runner(node_trace, meter, span=sp):
                sp.add_info_events(
                    attributes={
                        "response-chunk": json.dumps(response, ensure_ascii=False)
                    }
                )

                yield response

    def _initialize_response(self) -> ReasonChatCompletion:
        """Initialize the response object for non-streaming completion"""
        return ReasonChatCompletion(
            id=self.span.sid,
            created=cur_timestamp(),
            model="",
            object="chat.completion",
            choices=[ReasonChoice(message=ReasonChoiceMessage(), finish_reason="stop")],
        )

    def _is_valid_chunk_line(self, line: str) -> bool:
        """Check if the line is a valid chunk data line"""
        return line != "data: [DONE]\n\n" and line.startswith("data: ")

    def _update_reasoning_content(
        self, response: ReasonChatCompletion, chunk: ReasonChatCompletionChunk
    ) -> None:
        """Update reasoning content in the response"""
        if chunk.choices[0].delta.reasoning_content:
            if response.choices[0].message.reasoning_content is None:
                response.choices[0].message.reasoning_content = ""
            response.choices[0].message.reasoning_content += chunk.choices[
                0
            ].delta.reasoning_content

    def _update_content(
        self, response: ReasonChatCompletion, chunk: ReasonChatCompletionChunk
    ) -> None:
        """Update content in the response"""
        if chunk.choices[0].delta.content:
            if response.choices[0].message.content is None:
                response.choices[0].message.content = ""
            response.choices[0].message.content += chunk.choices[0].delta.content

    def _update_tool_calls(
        self, response: ReasonChatCompletion, chunk: ReasonChatCompletionChunk
    ) -> None:
        """Update tool calls in the response"""
        if chunk.choices[0].delta.tool_calls:
            if response.choices[0].message.tool_calls is None:
                response.choices[0].message.tool_calls = []
            for tool_call in chunk.choices[0].delta.tool_calls:
                if tool_call.function is not None:
                    response.choices[0].message.tool_calls.append(
                        ReasonChoiceMessageToolCall(
                            reason=tool_call.reason,
                            function=ReasonChoiceMessageToolCallFunction(
                                arguments=tool_call.function.arguments,
                                name=tool_call.function.name,
                                response=tool_call.function.response,
                            ),
                            type=tool_call.type,
                        )
                    )

    def _update_response_metadata(
        self, response: ReasonChatCompletion, chunk: ReasonChatCompletionChunk
    ) -> None:
        """Update response metadata"""
        if chunk.model:
            response.model = chunk.model
        response.code = chunk.code
        response.message = chunk.message
        if chunk.knowledge_metadata:
            response.knowledge_metadata = chunk.knowledge_metadata

    async def completion(self) -> dict[str, Any]:
        """Non-streaming"""
        response = self._initialize_response()

        async for line in self.stream_completion():
            if not self._is_valid_chunk_line(line):
                continue

            chunk_data = json.loads(line.lstrip("data: ").strip())
            chunk = ReasonChatCompletionChunk(**chunk_data)

            if not chunk.choices:
                continue

            self._update_reasoning_content(response, chunk)
            self._update_content(response, chunk)
            self._update_tool_calls(response, chunk)
            self._update_response_metadata(response, chunk)

        return response.model_dump()

    async def build_runner(self, span: Span) -> OpenAPIRunner:
        """Build agent"""

        with span.start("BuildOpenAPIRunner") as sp:
            runner = await OpenAPIRunnerBuilder(
                app_id=self.app_id, uid=self.inputs.uid, inputs=self.inputs, span=sp
            ).build()

            return runner


@openapi_router.post("/chat/completions")
async def chat_completions(
    x_consumer_username: Annotated[str, Header()], inputs: CompletionInputs
) -> Any:
    span = Span(app_id=x_consumer_username, uid=inputs.uid)
    with span.start("ChatCompletion") as chat_span:
        chat_completion = ChatCompletion(
            app_id=x_consumer_username,
            inputs=inputs,
            log_caller=inputs.meta_data.caller,
            span=chat_span,  # pyright: ignore[reportCallIssue]
        )
        if inputs.stream:
            return StreamingResponse(
                chat_completion.stream_completion(),
                media_type="text/event-stream",
                headers=headers,
            )
        return await chat_completion.completion()
