import datetime
import json
import time
from typing import Any, AsyncIterator, List

from pydantic import BaseModel, Field

from api.schemas.agent_response import AgentResponse, CotStep
from api.schemas.llm_message import LLMMessage

# Use unified common package import module
from common_imports import Node, NodeData, NodeDataUsage, NodeTrace, Span
from domain.models.base import BaseLLMModel


class UpdatedNode(Node):
    node_name: str = Field(default="")


class RunnerBase(BaseModel):
    model: BaseLLMModel
    chat_history: list[LLMMessage]

    @staticmethod
    def cur_time() -> str:
        now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        return now

    async def create_history_prompt(self) -> str:
        history_lines = [
            f"{history.role.title()}: {history.content}"
            for history in self.chat_history
        ]

        return "\n".join(history_lines) or "无"

    async def model_general_stream(
        self, messages: list, span: Span, node_trace: NodeTrace
    ) -> AsyncIterator[AgentResponse]:

        with span.start("RunModelStream") as sp:

            thinks = ""
            answers = ""
            # node assignment
            node_id = ""
            node_sid = span.sid
            node_node_id = span.sid
            node_type = "LLM"
            node_name = "ModelGeneralStream"
            node_start_time = int(round(time.time() * 1000))
            node_running_status = True
            node_data_input = {
                "model_general_stream_input": json.dumps(messages, ensure_ascii=False)
            }
            node_data_output: dict[str, Any] = {}
            node_data_config: dict[str, Any] = {}
            node_data_usage = NodeDataUsage()
            async for chunk in self.model.stream(messages, True, sp):
                delta = chunk.choices[0].delta.model_dump()
                reasoning_content = delta.get("reasoning_content")
                content = delta.get("content")

                node_data_usage.completion_tokens = 0
                node_data_usage.prompt_tokens = 0
                node_data_usage.total_tokens = 0
                if chunk.usage and chunk.usage.model_dump().get("total_tokens") != 0:
                    usage_data = chunk.usage.model_dump()
                    node_data_usage.completion_tokens = usage_data.get(
                        "completion_tokens", 0
                    )
                    node_data_usage.prompt_tokens = usage_data.get("prompt_tokens", 0)
                    node_data_usage.total_tokens = usage_data.get("total_tokens", 0)

                if reasoning_content:
                    yield AgentResponse(
                        typ="reasoning_content",
                        content=reasoning_content,
                        model=self.model.name,
                    )
                    thinks += reasoning_content
                if content:
                    yield AgentResponse(
                        typ="content", content=content, model=self.model.name
                    )
                    answers += content

            node_end_time = int(round(time.time() * 1000))
            data_llm_output = answers
            node_trace.trace.append(
                UpdatedNode(
                    id=node_id,
                    sid=node_sid,
                    node_id=node_node_id,
                    node_name=node_name,
                    node_type=node_type,
                    start_time=node_start_time,
                    end_time=node_end_time,
                    duration=node_end_time - node_start_time,
                    running_status=node_running_status,
                    llm_output=data_llm_output,
                    data=NodeData(
                        input=node_data_input if node_data_input else {},
                        output=node_data_output if node_data_output else {},
                        config=node_data_config if node_data_config else {},
                        usage=node_data_usage,
                    ),
                )
            )

            sp.add_info_events({"model-think": thinks})
            sp.add_info_events({"model-answer": answers})

            # yield AgentResponse(
            #     typ="log",
            #     content=[{"messages": messages, "think": thinks, "answer": answers}],
            #     model=self.model.name
            # )


class Scratchpad(BaseModel):
    steps: List[CotStep] = Field(default_factory=list)

    async def template(self) -> str:
        step_templates = []
        for step in self.steps:
            action_input_text = json.dumps(step.action_input, ensure_ascii=False)
            action_output_text = json.dumps(step.action_output, ensure_ascii=False)
            step_template = (
                f"Thought: {step.thought}\n"
                f"Action: {step.action}\n"
                f"Action Input: {action_input_text}\n"
                f"Observation: {action_output_text}"
            )
            step_templates.append(step_template)
        return "\n".join(step_templates)
