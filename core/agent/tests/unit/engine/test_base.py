"""
Unit tests for engine.nodes.base
"""

from datetime import datetime
from typing import Any, AsyncIterator, List
from unittest.mock import AsyncMock, Mock

import pytest

from api.schemas.llm_message import LLMMessage

# Use unified common package import module
from common_imports import NodeData, NodeDataUsage, NodeTrace, Span
from domain.models.base import BaseLLMModel
from engine.nodes.base import RunnerBase, Scratchpad, UpdatedNode


class TestRunnerBase:
    """Test cases for RunnerBase."""

    @pytest.fixture
    def mock_llm_model(self) -> Mock:
        """Create mock LLM model."""
        mock_model = Mock(spec=BaseLLMModel)
        mock_model.name = "gpt-3.5-turbo"
        mock_model.stream = AsyncMock()
        return mock_model

    @pytest.fixture
    def sample_chat_history(self) -> List[LLMMessage]:
        """Sample chat history for testing."""
        return [
            LLMMessage(role="user", content="Hello"),
            LLMMessage(role="assistant", content="Hi there!"),
            LLMMessage(role="user", content="How are you?"),
        ]

    @pytest.fixture
    def runner_base(
        self, mock_llm_model: Mock, sample_chat_history: List[LLMMessage]
    ) -> RunnerBase:
        """Create RunnerBase instance for testing."""
        return RunnerBase(model=mock_llm_model, chat_history=sample_chat_history)

    @pytest.mark.unit
    def test_runner_base_creation(
        self, mock_llm_model: Mock, sample_chat_history: List[LLMMessage]
    ) -> None:
        """Test RunnerBase creation with valid data."""
        # Act
        runner = RunnerBase(model=mock_llm_model, chat_history=sample_chat_history)

        # Assert
        assert runner.model == mock_llm_model
        assert len(runner.chat_history) == 3
        assert runner.chat_history[0].role == "user"
        assert runner.chat_history[1].content == "Hi there!"

    @pytest.mark.unit
    def test_cur_time_format(self) -> None:
        """Test cur_time returns correct format."""
        # Act
        time_str = RunnerBase.cur_time()

        # Assert
        # Should match format "YYYY-MM-DD HH:MM:SS"
        assert len(time_str) == 19
        assert time_str[4] == "-"
        assert time_str[7] == "-"
        assert time_str[10] == " "
        assert time_str[13] == ":"
        assert time_str[16] == ":"

    @pytest.mark.unit
    def test_cur_time_is_current(self) -> None:
        """Test cur_time returns current time."""
        # Arrange
        before = datetime.now()

        # Act
        time_str = RunnerBase.cur_time()

        # Parse the returned time
        returned_time = datetime.strptime(time_str, "%Y-%m-%d %H:%M:%S")
        after = datetime.now()

        # Assert - allow for 1 second difference due to timing precision
        assert (returned_time - before).total_seconds() >= -1
        assert (after - returned_time).total_seconds() >= -1

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_create_history_prompt_with_messages(
        self, runner_base: RunnerBase
    ) -> None:
        """Test create_history_prompt with chat history."""
        # Act
        prompt = await runner_base.create_history_prompt()

        # Assert
        expected_lines = ["User: Hello", "Assistant: Hi there!", "User: How are you?"]
        expected_prompt = "\n".join(expected_lines)
        assert prompt == expected_prompt

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_create_history_prompt_empty_history(
        self, mock_llm_model: Mock
    ) -> None:
        """Test create_history_prompt with empty history."""
        # Arrange
        runner = RunnerBase(model=mock_llm_model, chat_history=[])

        # Act
        prompt = await runner.create_history_prompt()

        # Assert
        assert prompt == "无"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_create_history_prompt_single_message(
        self, mock_llm_model: Mock
    ) -> None:
        """Test create_history_prompt with single message."""
        # Arrange
        history = [LLMMessage(role="user", content="Single message")]
        runner = RunnerBase(model=mock_llm_model, chat_history=history)

        # Act
        prompt = await runner.create_history_prompt()

        # Assert
        assert prompt == "User: Single message"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_model_general_stream_success(self, runner_base: RunnerBase) -> None:
        """Test successful model_general_stream execution."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span.sid = "test_span_id"
        mock_span_context = Mock()

        # Create a proper context manager mock
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_node_trace = Mock(spec=NodeTrace)
        mock_node_trace.trace = []

        # Mock streaming chunks
        mock_chunk1 = Mock()
        mock_chunk1.choices = [Mock()]
        mock_chunk1.choices[0].delta.model_dump.return_value = {"content": "Hello"}
        mock_chunk1.usage = None

        mock_chunk2 = Mock()
        mock_chunk2.choices = [Mock()]
        mock_chunk2.choices[0].delta.model_dump.return_value = {"content": " World"}
        mock_chunk2.usage = Mock()
        mock_chunk2.usage.model_dump.return_value = {
            "total_tokens": 10,
            "prompt_tokens": 5,
            "completion_tokens": 5,
        }

        async def mock_stream(
            *_args: Any, **_kwargs: Any  # pylint: disable=unused-argument
        ) -> AsyncIterator[Any]:
            yield mock_chunk1
            yield mock_chunk2

        runner_base.model.stream = mock_stream  # type: ignore[method-assign]

        messages = [{"role": "user", "content": "Test message"}]

        # Act
        responses = []
        async for response in runner_base.model_general_stream(
            messages, mock_span, mock_node_trace
        ):
            responses.append(response)

        # Assert
        assert len(responses) == 2
        assert responses[0].content == "Hello"
        assert responses[0].typ == "content"
        assert responses[0].model == "gpt-3.5-turbo"
        assert responses[1].content == " World"

        # Verify node trace was updated
        assert len(mock_node_trace.trace) == 1
        trace_node = mock_node_trace.trace[0]
        assert trace_node.node_name == "ModelGeneralStream"
        assert trace_node.node_type == "LLM"
        assert trace_node.data.llm_output == "Hello World"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_model_general_stream_with_reasoning_content(
        self, runner_base: RunnerBase
    ) -> None:
        """Test model_general_stream with reasoning content."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span.sid = "test_span_id"
        mock_span_context = Mock()

        # Create a proper context manager mock
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_node_trace = Mock(spec=NodeTrace)
        mock_node_trace.trace = []

        mock_chunk = Mock()
        mock_chunk.choices = [Mock()]
        mock_chunk.choices[0].delta.model_dump.return_value = {
            "reasoning_content": "Let me think...",
            "content": "Answer",
        }
        mock_chunk.usage = None

        async def mock_stream(
            *_args: Any, **_kwargs: Any  # pylint: disable=unused-argument
        ) -> AsyncIterator[Any]:
            yield mock_chunk

        runner_base.model.stream = mock_stream  # type: ignore[method-assign]

        messages = [{"role": "user", "content": "Test message"}]

        # Act
        responses = []
        async for response in runner_base.model_general_stream(
            messages, mock_span, mock_node_trace
        ):
            responses.append(response)

        # Assert
        assert len(responses) == 2
        assert responses[0].typ == "reasoning_content"
        assert responses[0].content == "Let me think..."
        assert responses[1].typ == "content"
        assert responses[1].content == "Answer"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_model_general_stream_token_usage_tracking(
        self, runner_base: RunnerBase
    ) -> None:
        """Test token usage tracking in model_general_stream."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span.sid = "test_span_id"
        mock_span_context = Mock()

        # Create a proper context manager mock
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_node_trace = Mock(spec=NodeTrace)
        mock_node_trace.trace = []

        mock_chunk = Mock()
        mock_chunk.choices = [Mock()]
        mock_chunk.choices[0].delta.model_dump.return_value = {"content": "Test"}
        mock_chunk.usage = Mock()
        mock_chunk.usage.model_dump.return_value = {
            "total_tokens": 20,
            "prompt_tokens": 10,
            "completion_tokens": 10,
        }

        async def mock_stream(
            *_args: Any, **_kwargs: Any  # pylint: disable=unused-argument
        ) -> AsyncIterator[Any]:
            yield mock_chunk

        runner_base.model.stream = mock_stream  # type: ignore[method-assign]

        messages = [{"role": "user", "content": "Test message"}]

        # Act
        responses = []
        async for response in runner_base.model_general_stream(
            messages, mock_span, mock_node_trace
        ):
            responses.append(response)

        # Assert
        trace_node = mock_node_trace.trace[0]
        assert trace_node.data.usage.total_tokens == 20
        assert trace_node.data.usage.prompt_tokens == 10
        assert trace_node.data.usage.completion_tokens == 10

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_model_general_stream_span_events(
        self, runner_base: RunnerBase
    ) -> None:
        """Test span events are added correctly."""
        # Arrange
        mock_span = Mock(spec=Span)
        mock_span.sid = "test_span_id"
        mock_span_context = Mock()
        mock_span_context.add_info_events = Mock()

        # Create a proper context manager mock
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)

        mock_node_trace = Mock(spec=NodeTrace)
        mock_node_trace.trace = []

        mock_chunk = Mock()
        mock_chunk.choices = [Mock()]
        mock_chunk.choices[0].delta.model_dump.return_value = {
            "reasoning_content": "thinking",
            "content": "answer",
        }
        mock_chunk.usage = None

        async def mock_stream(
            *_args: Any, **_kwargs: Any  # pylint: disable=unused-argument
        ) -> AsyncIterator[Any]:
            yield mock_chunk

        runner_base.model.stream = mock_stream  # type: ignore[method-assign]

        messages = [{"role": "user", "content": "Test message"}]

        # Act
        responses = []
        async for response in runner_base.model_general_stream(
            messages, mock_span, mock_node_trace
        ):
            responses.append(response)

        # Assert
        # Verify span events were added
        assert mock_span_context.add_info_events.call_count == 2
        call_args = [
            call.args[0] for call in mock_span_context.add_info_events.call_args_list
        ]

        # Should have model-think and model-answer events
        thinks_event = next((arg for arg in call_args if "model-think" in arg), None)
        answers_event = next((arg for arg in call_args if "model-answer" in arg), None)

        assert thinks_event is not None
        assert answers_event is not None
        assert thinks_event["model-think"] == "thinking"
        assert answers_event["model-answer"] == "answer"


class TestUpdatedNode:
    """Test cases for UpdatedNode."""

    @pytest.mark.unit
    def test_updated_node_creation(self) -> None:
        """Test UpdatedNode creation with valid data."""
        # Arrange
        node_data = NodeData(
            input={"test": "input"},
            output={"test": "output"},
            config={"test": "config"},
            usage=NodeDataUsage(total_tokens=10, prompt_tokens=5, completion_tokens=5),
            llm_output="Test output",
        )

        # Act
        node = UpdatedNode(
            id="node_001",
            sid="span_001",
            node_id="node_001",
            node_name="Test Node",
            node_type="LLM",
            start_time=1000,
            end_time=2000,
            duration=1000,
            running_status=True,
            data=node_data,
        )

        # Assert
        assert node.id == "node_001"
        assert node.node_name == "Test Node"
        assert node.node_type == "LLM"
        assert node.duration == 1000
        assert node.running_status is True
        assert node.data.llm_output == "Test output"  # pylint: disable=no-member


class TestScratchpad:
    """Test cases for Scratchpad."""

    @pytest.mark.unit
    def test_scratchpad_creation(self) -> None:
        """Test Scratchpad creation."""
        # Act
        scratchpad = Scratchpad()

        # Assert
        assert isinstance(scratchpad, Scratchpad)
        # Add more specific tests based on Scratchpad implementation
