from typing import AsyncIterator

from pydantic import Field

from api.schemas.agent_response import AgentResponse

# Use unified common package import module
from common_imports import NodeTrace, Span
from engine.nodes.base import RunnerBase
from engine.nodes.chat.chat_prompt import CHAT_SYSTEM_TEMPLATE, CHAT_USER_TEMPLATE


class ChatRunner(RunnerBase):
    chat_history: list
    instruct: str = Field(default="")
    knowledge: str = Field(default="")
    question: str = Field(default="")

    async def run(
        self, span: Span, node_trace: NodeTrace
    ) -> AsyncIterator[AgentResponse]:
        with span.start("RunChatAgent") as sp:

            system_prompt = (
                CHAT_SYSTEM_TEMPLATE.replace("{now}", self.cur_time())
                .replace("{instruct}", self.instruct)
                .replace("{knowledge}", self.knowledge)
            )
            user_prompt = CHAT_USER_TEMPLATE.replace(
                "{chat_history}", await self.create_history_prompt()
            ).replace("{question}", self.question)

            messages = [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ]

            async for chunk in self.model_general_stream(messages, sp, node_trace):
                yield chunk
