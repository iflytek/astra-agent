import json
import time
import traceback
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any, AsyncGenerator, List

from pydantic import BaseModel, ConfigDict

from api.schemas.base_inputs import BaseInputs
from api.schemas.completion_chunk import (
    ReasonChatCompletionChunk,
    ReasonChoice,
    ReasonChoiceDelta,
)
from api.schemas.node_trace_patch import NodeTracePatch

# Use unified common package import module
from common_imports import BaseExc, Meter, NodeTrace, Span, TraceStatus
from exceptions.agent_exc import AgentInternalExc, AgentNormalExc
from infra import agent_config


def json_serializer(obj: Any) -> Any:
    """Custom JSON serializer to handle set objects."""
    if isinstance(obj, set):
        return list(obj)
    raise TypeError(f"Object of type {obj.__class__.__name__} is not JSON serializable")


@dataclass
class RunContext:
    """Runtime context parameters"""

    error: BaseExc
    error_log: str
    chunk_logs: List[str]
    span: Span
    node_trace: NodeTrace
    meter: Meter


class CompletionBase(BaseModel, ABC):
    app_id: str
    inputs: BaseInputs
    log_caller: str

    model_config = ConfigDict(arbitrary_types_allowed=True)

    @abstractmethod
    async def build_runner(self, span: Span) -> Any:
        """Subclasses need to implement the logic for building runner"""

    async def build_node_trace(self, bot_id: str, span: Span) -> NodeTracePatch:
        with span.start("BuildNodeTrace") as sp:
            node_trace: NodeTracePatch = NodeTracePatch(
                service_id=bot_id,  # Use bot_id as service_id
                sid=sp.sid,
                app_id=self.app_id,
                uid=self.inputs.uid,
                chat_id=sp.sid,
                sub="Agent",
                caller=self.inputs.meta_data.caller,
                log_caller=self.log_caller,
                question=self.inputs.get_last_message_content(),
            )
            node_trace.record_start()

            sp.add_info_events({"node-trace": node_trace.model_dump_json()})

            return node_trace

    async def build_meter(self, span: Span) -> Meter:

        with span.start("BuildMeter") as sp:
            sp.add_info_events({"app-id": self.app_id, "func": self.log_caller})

            meter = Meter(app_id=self.app_id, func=self.log_caller)
            return meter

    async def _process_chunk(
        self, chunk: Any, chunk_logs: List[str]
    ) -> AsyncGenerator[str, None]:
        """Logic for processing individual chunk"""
        if chunk.object == "chat.completion.log":
            # span.add_info_events(attributes={
            #     "log": json.dumps(chunk.log, ensure_ascii=False)
            # })
            return  # Do not generate chunk output

        if chunk.object == "chat.completion.chunk":
            chunk_logs.append(chunk.model_dump_json())
            yield await self.create_chunk(chunk)
            return

        if chunk.object == "chat.completion.knowledge_metadata":
            if self.log_caller == "chat_open_api":
                return  # Do not generate chunk output

            chunk_logs.append(chunk.model_dump_json())
            yield await self.create_chunk(chunk)

    async def _finalize_run(self, context: RunContext) -> AsyncGenerator[str, None]:
        """Cleanup work after completing the run"""
        if context.error.c != 0:
            context.error.m += f",{context.span.sid}"
            context.span.add_error_events({"traceback": context.error_log})

        stop_chunk = await self.create_stop(context.span, context.error)
        context.chunk_logs.append(stop_chunk.model_dump_json())

        for chunk_log in context.chunk_logs:
            context.span.add_info_events({"response-chunk": chunk_log})

        yield await self.create_chunk(stop_chunk)
        yield await self.create_done()

        if agent_config.UPLOAD_METRICS:
            context.meter.in_error_count(context.error.c)
            # context.meter.in_error_count(
            #     context.error.c, lables={"msg": context.error.m}
            # )

        context.span.set_attributes(attributes={"code": context.error.c})
        context.span.add_info_events({"message": context.error.m})

        context.node_trace.record_end()
        if agent_config.UPLOAD_NODE_TRACE:
            node_trace_log = context.node_trace.upload(
                status=TraceStatus(code=context.error.c, message=context.error.m),
                log_caller=self.log_caller,
                span=context.span,
            )
            context.span.add_info_events(
                {
                    "node-trace": json.dumps(
                        node_trace_log, ensure_ascii=False, default=json_serializer
                    )
                }
            )

    async def run_runner(
        self, node_trace: NodeTrace, meter: Meter, span: Span
    ) -> AsyncGenerator[str, None]:

        with span.start("RunRunner") as sp:
            error: BaseExc = AgentNormalExc()
            error_log: str = ""
            chunk_logs: List[str] = []

            try:
                runner = await self.build_runner(sp)
                if runner is None:
                    raise AgentInternalExc("Failed to build runner")

                async for chunk in runner.run(span=sp, node_trace=node_trace):
                    chunk.id = span.sid
                    async for processed_chunk in self._process_chunk(chunk, chunk_logs):
                        yield processed_chunk

            except BaseExc as e:
                error = e
                error_log = traceback.format_exc()
            except Exception as e:  # pylint: disable=broad-exception-caught
                error = AgentInternalExc(str(e))
                error_log = traceback.format_exc()

            finally:
                context = RunContext(
                    error=error,
                    error_log=error_log,
                    chunk_logs=chunk_logs,
                    span=sp,
                    node_trace=node_trace,
                    meter=meter,
                )
                async for final_chunk in self._finalize_run(context):
                    yield final_chunk

    @staticmethod
    async def create_chunk(chunk: Any) -> str:
        return f"data: {chunk.model_dump_json()}\n\n"

    @staticmethod
    async def create_stop(span: Span, e: BaseExc) -> ReasonChatCompletionChunk:
        chunk = ReasonChatCompletionChunk(
            id=span.sid,
            code=e.c,
            message=e.m,
            choices=[
                ReasonChoice(index=0, finish_reason="stop", delta=ReasonChoiceDelta())
            ],
            created=int(time.time() * 1000),
            model="",
            object="chat.completion.chunk",
        )
        return chunk

    @staticmethod
    async def create_done() -> str:
        return "data: [DONE]\n\n"
