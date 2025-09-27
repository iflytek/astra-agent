import json
from typing import AsyncIterator

from pydantic import Field

from api.schemas.agent_response import AgentResponse

# Use unified common package import module
from common_imports import NodeTrace, Span
from domain.models.base import BaseLLMModel
from engine.nodes.base import RunnerBase, Scratchpad
from engine.nodes.cot_process.cot_process_prompt import (
    COT_PROCESS_LAST_USER_STEP_TEMPLATE,
    COT_PROCESS_SYSTEM_TEMPLATE,
    COT_PROCESS_USER_STEP_TEMPLATE,
    COT_PROCESS_USER_TEMPLATE,
)


class CotProcessRunner(RunnerBase):
    model: BaseLLMModel
    chat_history: list
    instruct: str = Field(default="")
    knowledge: str = Field(default="")
    question: str = Field(default="")

    async def run(
        self,
        scratchpad: Scratchpad,
        span: Span,
        node_trace: NodeTrace,
    ) -> AsyncIterator[AgentResponse]:
        """使用cot过程进行思考回答"""

        with span.start("RunCotProcessAgent") as sp:

            system_prompt = (
                COT_PROCESS_SYSTEM_TEMPLATE.replace("{now}", self.cur_time())
                .replace("{instruct}", self.instruct)
                .replace("{knowledge}", self.knowledge)
            )
            reasoning_process = []

            for i, step in enumerate(scratchpad.steps, start=1):
                if step.finished_cot:
                    step_template = COT_PROCESS_LAST_USER_STEP_TEMPLATE.replace(
                        "{no}", str(i)
                    ).replace("{think}", step.thought)
                else:
                    action_input_text = json.dumps(
                        step.action_input, ensure_ascii=False
                    )
                    action_output_text = json.dumps(
                        step.action_output, ensure_ascii=False
                    )
                    step_template = (
                        COT_PROCESS_USER_STEP_TEMPLATE.replace("{no}", str(i))
                        .replace("{think}", step.thought)
                        .replace("{action}", step.action)
                        .replace("{action_input}", action_input_text)
                        .replace("{action_output}", action_output_text)
                    )
                reasoning_process.append(step_template)

            process_text = "\n".join(reasoning_process)
            user_prompt = (
                COT_PROCESS_USER_TEMPLATE.replace("{reasoning_process}", process_text)
                .replace("{chat_history}", await self.create_history_prompt())
                .replace("{question}", self.question)
            )

            messages = [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ]

            async for chunk in self.model_general_stream(messages, sp, node_trace):
                yield chunk
