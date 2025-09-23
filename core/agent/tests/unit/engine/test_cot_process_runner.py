"""
Unit tests for engine.nodes.cot_process.cot_process_runner
"""

from typing import Any, AsyncIterator, List
from unittest.mock import AsyncMock, Mock, patch

import pytest

from api.schemas.agent_response import AgentResponse, CotStep
from api.schemas.llm_message import LLMMessage
from api.schemas.node_trace_patch import NodeTrace
from common_imports import Span
from domain.models.base import BaseLLMModel
from engine.nodes.base import Scratchpad
from engine.nodes.cot_process.cot_process_runner import CotProcessRunner


class TestCotProcessRunner:
    """Test cases for CotProcessRunner."""

    @pytest.fixture
    def mock_llm_model(self) -> Mock:
        """Create mock LLM model."""
        mock_model = Mock(spec=BaseLLMModel)
        mock_model.name = "gpt-3.5-turbo"
        return mock_model

    @pytest.fixture
    def sample_chat_history(self) -> List[LLMMessage]:
        """Sample chat history for testing."""
        return [
            LLMMessage(role="user", content="Hello"),
            LLMMessage(role="assistant", content="Hi there!"),
        ]

    @pytest.fixture
    def sample_scratchpad(self) -> Scratchpad:
        """Create sample scratchpad with steps."""
        scratchpad = Scratchpad()

        # Add regular CoT step
        step1 = CotStep(
            thought="I need to search for information",
            action="search_plugin",
            action_input={"query": "test query"},
            action_output={"result": "search result"},
        )

        # Add finishing CoT step
        step2 = CotStep(
            thought="Now I have enough information to answer", finished_cot=True
        )

        scratchpad.steps = [step1, step2]
        return scratchpad

    @pytest.fixture
    def cot_process_runner(
        self, mock_llm_model: Mock, sample_chat_history: List[LLMMessage]
    ) -> CotProcessRunner:
        """Create CotProcessRunner instance for testing."""
        return CotProcessRunner(
            model=mock_llm_model,
            chat_history=sample_chat_history,
            instruct="You are a helpful assistant.",
            knowledge="Some knowledge base content.",
            question="How can I help you today?",
        )

    @pytest.mark.unit
    def test_cot_process_runner_creation_with_all_fields(
        self, mock_llm_model: Mock, sample_chat_history: List[LLMMessage]
    ) -> None:
        """Test CotProcessRunner creation with all fields."""
        # Act
        runner = CotProcessRunner(
            model=mock_llm_model,
            chat_history=sample_chat_history,
            instruct="Custom instruction",
            knowledge="Knowledge content",
            question="Test question",
        )

        # Assert
        assert runner.model == mock_llm_model
        assert len(runner.chat_history) == 2
        assert runner.instruct == "Custom instruction"
        assert runner.knowledge == "Knowledge content"
        assert runner.question == "Test question"

    @pytest.mark.unit
    def test_cot_process_runner_creation_with_defaults(
        self, mock_llm_model: Mock, sample_chat_history: List[LLMMessage]
    ) -> None:
        """Test CotProcessRunner creation with default values."""
        # Act
        runner = CotProcessRunner(
            model=mock_llm_model, chat_history=sample_chat_history
        )

        # Assert
        assert runner.instruct == ""
        assert runner.knowledge == ""
        assert runner.question == ""

    @pytest.mark.unit
    def test_cot_process_runner_inherits_from_runner_base(
        self, cot_process_runner: CotProcessRunner
    ) -> None:
        """Test CotProcessRunner inherits from RunnerBase."""
        # Assert
        assert hasattr(cot_process_runner, "model")
        assert hasattr(cot_process_runner, "chat_history")
        assert hasattr(cot_process_runner, "cur_time")
        assert hasattr(cot_process_runner, "create_history_prompt")
        assert hasattr(cot_process_runner, "model_general_stream")

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_with_mixed_steps(
        self, cot_process_runner: CotProcessRunner, sample_scratchpad: Scratchpad
    ) -> None:
        """Test run execution with mixed CoT steps."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_node_trace = Mock(spec=NodeTrace)

        # Mock model_general_stream to return some responses
        expected_responses = [
            AgentResponse(
                typ="content", content="Based on the reasoning", model="gpt-3.5-turbo"
            ),
            AgentResponse(
                typ="content",
                content=" process, here's my answer",
                model="gpt-3.5-turbo",
            ),
        ]

        async def mock_stream(
            *_args: Any, **_kw_args: Any  # pylint: disable=unused-argument
        ) -> AsyncIterator[AgentResponse]:
            for response in expected_responses:
                yield response

        with patch(
            "engine.nodes.base.RunnerBase.model_general_stream", side_effect=mock_stream
        ) as mock_method:
            with patch(
                "engine.nodes.base.RunnerBase.cur_time",
                return_value="2024-01-01 12:00:00",
            ):
                with patch(
                    "engine.nodes.base.RunnerBase.create_history_prompt",
                    new_callable=AsyncMock,
                    return_value="Chat history",
                ):

                    # Act
                    responses = []
                    async for response in cot_process_runner.run(
                        sample_scratchpad, mock_span, mock_node_trace
                    ):
                        responses.append(response)

                    # Assert
                    assert len(responses) == 2
                    assert responses[0].content == "Based on the reasoning"
                    assert responses[1].content == " process, here's my answer"

                    # Verify model_general_stream was called with correct arguments
                    mock_method.assert_called_once()
                    call_args = mock_method.call_args
                    messages = call_args[0][0]  # First positional argument

                    assert len(messages) == 2
                    assert messages[0]["role"] == "system"
                    assert messages[1]["role"] == "user"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_constructs_correct_system_prompt(
        self, cot_process_runner: CotProcessRunner, sample_scratchpad: Scratchpad
    ) -> None:
        """Test run constructs system prompt correctly."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_node_trace = Mock(spec=NodeTrace)

        async def mock_stream(
            *_args: Any, **_kw_args: Any  # pylint: disable=unused-argument
        ) -> AsyncIterator[Any]:
            return
            yield  # Make it an async generator

        with patch(
            "engine.nodes.base.RunnerBase.model_general_stream", side_effect=mock_stream
        ) as mock_method:
            with patch(
                "engine.nodes.base.RunnerBase.cur_time",
                return_value="2024-01-01 12:00:00",
            ):
                with patch(
                    "engine.nodes.base.RunnerBase.create_history_prompt",
                    new_callable=AsyncMock,
                    return_value="Chat history",
                ):

                    # Act
                    async for _ in cot_process_runner.run(
                        sample_scratchpad, mock_span, mock_node_trace
                    ):
                        break

                    # Assert
                    call_args = mock_method.call_args
                    messages = call_args[0][0]
                    system_message = messages[0]

                    assert "2024-01-01 12:00:00" in system_message["content"]
                    assert "You are a helpful assistant." in system_message["content"]
                    assert "Some knowledge base content." in system_message["content"]

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_constructs_correct_user_prompt_with_reasoning_process(
        self, cot_process_runner: CotProcessRunner, sample_scratchpad: Scratchpad
    ) -> None:
        """Test run constructs user prompt with reasoning process correctly."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_node_trace = Mock(spec=NodeTrace)

        async def mock_stream(
            *_args: Any, **_kw_args: Any  # pylint: disable=unused-argument
        ) -> AsyncIterator[Any]:
            return
            yield

        with patch(
            "engine.nodes.base.RunnerBase.model_general_stream", side_effect=mock_stream
        ) as mock_method:
            with patch(
                "engine.nodes.base.RunnerBase.cur_time",
                return_value="2024-01-01 12:00:00",
            ):
                with patch(
                    "engine.nodes.base.RunnerBase.create_history_prompt",
                    new_callable=AsyncMock,
                    return_value="Chat history",
                ):

                    # Act
                    async for _ in cot_process_runner.run(
                        sample_scratchpad, mock_span, mock_node_trace
                    ):
                        break

                    # Assert
                    call_args = mock_method.call_args
                    messages = call_args[0][0]
                    user_message = messages[1]

                    # Should contain reasoning process information
                    assert "Chat history" in user_message["content"]
                    assert "How can I help you today?" in user_message["content"]
                    # Should contain step information
                    assert "search_plugin" in user_message["content"]
                    assert "test query" in user_message["content"]

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_with_empty_scratchpad(
        self, cot_process_runner: CotProcessRunner
    ) -> None:
        """Test run execution with empty scratchpad."""
        # Arrange
        empty_scratchpad = Scratchpad()
        empty_scratchpad.steps = []

        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_node_trace = Mock(spec=NodeTrace)

        expected_response = AgentResponse(
            typ="content", content="No reasoning steps provided", model="gpt-3.5-turbo"
        )

        async def mock_stream(
            *_args: Any, **_kw_args: Any  # pylint: disable=unused-argument
        ) -> AsyncIterator[AgentResponse]:
            yield expected_response

        with patch(
            "engine.nodes.base.RunnerBase.model_general_stream", side_effect=mock_stream
        ) as mock_method:
            with patch(
                "engine.nodes.base.RunnerBase.cur_time",
                return_value="2024-01-01 12:00:00",
            ):
                with patch(
                    "engine.nodes.base.RunnerBase.create_history_prompt",
                    new_callable=AsyncMock,
                    return_value="Chat history",
                ):

                    # Act
                    responses = []
                    async for response in cot_process_runner.run(
                        empty_scratchpad, mock_span, mock_node_trace
                    ):
                        responses.append(response)

                    # Assert
                    assert len(responses) == 1
                    assert responses[0].content == "No reasoning steps provided"

                    # User prompt should have empty reasoning process
                    call_args = mock_method.call_args
                    messages = call_args[0][0]
                    user_message = messages[1]
                    # Should still contain the template structure but with empty process
                    assert "Chat history" in user_message["content"]

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_with_only_finished_cot_steps(
        self, cot_process_runner: CotProcessRunner
    ) -> None:
        """Test run execution with only finished CoT steps."""
        # Arrange
        scratchpad = Scratchpad()
        finished_step = CotStep(
            thought="This is my final conclusion", finished_cot=True
        )
        scratchpad.steps = [finished_step]

        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_node_trace = Mock(spec=NodeTrace)

        expected_response = AgentResponse(
            typ="content", content="Final answer", model="gpt-3.5-turbo"
        )

        async def mock_stream(
            *_args: Any, **_kw_args: Any  # pylint: disable=unused-argument
        ) -> AsyncIterator[AgentResponse]:
            yield expected_response

        with patch(
            "engine.nodes.base.RunnerBase.model_general_stream", side_effect=mock_stream
        ) as mock_method:
            with patch(
                "engine.nodes.base.RunnerBase.cur_time",
                return_value="2024-01-01 12:00:00",
            ):
                with patch(
                    "engine.nodes.base.RunnerBase.create_history_prompt",
                    new_callable=AsyncMock,
                    return_value="Chat history",
                ):

                    # Act
                    responses = []
                    async for response in cot_process_runner.run(
                        scratchpad, mock_span, mock_node_trace
                    ):
                        responses.append(response)

                    # Assert
                    assert len(responses) == 1

                    # User prompt should contain the finished step
                    call_args = mock_method.call_args
                    messages = call_args[0][0]
                    user_message = messages[1]
                    assert "This is my final conclusion" in user_message["content"]

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_with_complex_action_inputs_and_outputs(
        self, cot_process_runner: CotProcessRunner
    ) -> None:
        """Test run execution with complex action inputs and outputs."""
        # Arrange
        scratchpad = Scratchpad()
        complex_step = CotStep(
            thought="I need to process complex data",
            action="complex_plugin",
            action_input={
                "nested": {"key": "value"},
                "array": [1, 2, 3],
                "unicode": "test中文",
            },
            action_output={
                "status": "success",
                "data": {"processed": True},
                "message": "处理完成",
            },
        )
        scratchpad.steps = [complex_step]

        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_node_trace = Mock(spec=NodeTrace)

        expected_response = AgentResponse(
            typ="content", content="Complex processing result", model="gpt-3.5-turbo"
        )

        async def mock_stream(
            *_args: Any, **_kw_args: Any  # pylint: disable=unused-argument
        ) -> AsyncIterator[AgentResponse]:
            yield expected_response

        with patch(
            "engine.nodes.base.RunnerBase.model_general_stream", side_effect=mock_stream
        ) as mock_method:
            with patch(
                "engine.nodes.base.RunnerBase.cur_time",
                return_value="2024-01-01 12:00:00",
            ):
                with patch(
                    "engine.nodes.base.RunnerBase.create_history_prompt",
                    new_callable=AsyncMock,
                    return_value="Chat history",
                ):

                    # Act
                    responses = []
                    async for response in cot_process_runner.run(
                        scratchpad, mock_span, mock_node_trace
                    ):
                        responses.append(response)

                    # Assert
                    assert len(responses) == 1

                    # User prompt should contain properly formatted JSON
                    call_args = mock_method.call_args
                    messages = call_args[0][0]
                    user_message = messages[1]

                    # Should contain the complex data properly serialized
                    assert "complex_plugin" in user_message["content"]
                    assert "test中文" in user_message["content"]
                    assert "处理完成" in user_message["content"]

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_with_empty_fields(
        self, mock_llm_model: Mock, sample_chat_history: List[LLMMessage]
    ) -> None:
        """Test run with empty instruct, knowledge, and question."""
        # Arrange
        runner = CotProcessRunner(
            model=mock_llm_model,
            chat_history=sample_chat_history,
            instruct="",
            knowledge="",
            question="",
        )

        scratchpad = Scratchpad()
        scratchpad.steps = []

        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_node_trace = Mock(spec=NodeTrace)

        expected_response = AgentResponse(
            typ="content", content="Response with empty fields", model="gpt-3.5-turbo"
        )

        async def mock_stream(
            *_args: Any, **_kw_args: Any  # pylint: disable=unused-argument
        ) -> AsyncIterator[AgentResponse]:
            yield expected_response

        with patch(
            "engine.nodes.base.RunnerBase.model_general_stream", side_effect=mock_stream
        ) as mock_method:
            with patch(
                "engine.nodes.base.RunnerBase.cur_time",
                return_value="2024-01-01 12:00:00",
            ):
                with patch(
                    "engine.nodes.base.RunnerBase.create_history_prompt",
                    new_callable=AsyncMock,
                    return_value="Chat history",
                ):

                    # Act
                    responses = []
                    async for response in runner.run(
                        scratchpad, mock_span, mock_node_trace
                    ):
                        responses.append(response)

                    # Assert
                    assert len(responses) == 1

                    # System prompt should handle empty fields
                    call_args = mock_method.call_args
                    messages = call_args[0][0]
                    system_message = messages[0]

                    # Empty fields should be replaced in template
                    assert "2024-01-01 12:00:00" in system_message["content"]

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_handles_model_stream_error(
        self, cot_process_runner: CotProcessRunner, sample_scratchpad: Scratchpad
    ) -> None:
        """Test run handles model streaming errors."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_node_trace = Mock(spec=NodeTrace)

        with patch(
            "engine.nodes.base.RunnerBase.model_general_stream",
            side_effect=Exception("Model error"),
        ):
            with patch(
                "engine.nodes.base.RunnerBase.cur_time",
                return_value="2024-01-01 12:00:00",
            ):
                with patch(
                    "engine.nodes.base.RunnerBase.create_history_prompt",
                    new_callable=AsyncMock,
                    return_value="Chat history",
                ):

                    # Act & Assert
                    with pytest.raises(Exception) as exc_info:
                        async for _ in cot_process_runner.run(
                            sample_scratchpad, mock_span, mock_node_trace
                        ):
                            pass

                    assert "Model error" in str(exc_info.value)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_with_unicode_content(self, mock_llm_model: Mock) -> None:
        """Test run with unicode content in fields."""
        # Arrange
        history = [
            LLMMessage(role="user", content="你好"),
            LLMMessage(role="assistant", content="您好！我是AI助手"),
        ]

        runner = CotProcessRunner(
            model=mock_llm_model,
            chat_history=history,
            instruct="请用中文回答",
            knowledge="一些中文知识库内容",
            question="请问您需要什么帮助？",
        )

        scratchpad = Scratchpad()
        unicode_step = CotStep(
            thought="我需要搜索中文信息",
            action="中文插件",
            action_input={"查询": "test查询"},
            action_output={"结果": "搜索结果"},
        )
        scratchpad.steps = [unicode_step]

        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_node_trace = Mock(spec=NodeTrace)

        expected_response = AgentResponse(
            typ="content", content="中文回复", model="gpt-3.5-turbo"
        )

        async def mock_stream(
            *_args: Any, **_kw_args: Any  # pylint: disable=unused-argument
        ) -> AsyncIterator[AgentResponse]:
            yield expected_response

        with patch(
            "engine.nodes.base.RunnerBase.model_general_stream", side_effect=mock_stream
        ) as mock_method:
            with patch(
                "engine.nodes.base.RunnerBase.cur_time",
                return_value="2024-01-01 12:00:00",
            ):
                with patch(
                    "engine.nodes.base.RunnerBase.create_history_prompt",
                    new_callable=AsyncMock,
                    return_value="用户: 你好\n助手: 您好！我是AI助手",
                ):

                    # Act
                    responses = []
                    async for response in runner.run(
                        scratchpad, mock_span, mock_node_trace
                    ):
                        responses.append(response)

                    # Assert
                    assert len(responses) == 1
                    assert responses[0].content == "中文回复"

                    # Verify the messages contain unicode content
                    call_args = mock_method.call_args
                    messages = call_args[0][0]

                    assert "请用中文回答" in messages[0]["content"]
                    assert "一些中文知识库内容" in messages[0]["content"]
                    assert "请问您需要什么帮助？" in messages[1]["content"]
                    assert "中文插件" in messages[1]["content"]

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_run_passes_correct_span_and_trace(
        self, cot_process_runner: CotProcessRunner, sample_scratchpad: Scratchpad
    ) -> None:
        """Test run passes span and node_trace correctly to model_general_stream."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_node_trace = Mock(spec=NodeTrace)

        expected_response = AgentResponse(
            typ="content", content="Test response", model="gpt-3.5-turbo"
        )

        async def mock_stream(
            *_args: Any, **_kw_args: Any  # pylint: disable=unused-argument
        ) -> AsyncIterator[AgentResponse]:
            yield expected_response

        with patch(
            "engine.nodes.base.RunnerBase.model_general_stream", side_effect=mock_stream
        ) as mock_method:
            with patch(
                "engine.nodes.base.RunnerBase.cur_time",
                return_value="2024-01-01 12:00:00",
            ):
                with patch(
                    "engine.nodes.base.RunnerBase.create_history_prompt",
                    new_callable=AsyncMock,
                    return_value="Chat history",
                ):

                    # Act
                    async for _ in cot_process_runner.run(
                        sample_scratchpad, mock_span, mock_node_trace
                    ):
                        break

                    # Assert
                    call_args = mock_method.call_args
                    assert (
                        call_args[0][1] == mock_span_context
                    )  # Second argument should be span context
                    assert (
                        call_args[0][2] == mock_node_trace
                    )  # Third argument should be node_trace
