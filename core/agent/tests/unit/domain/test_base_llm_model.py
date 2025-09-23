"""基础LLM模型单元test模块."""

import asyncio
from typing import Any, AsyncIterator, Dict, List
from unittest.mock import AsyncMock, Mock, patch

import pytest

from domain.models.base import BaseLLMModel


class TestBaseLLMModel:
    """BaseLLMModeltest类."""

    def setup_method(self) -> None:
        """test方法初始化."""
        # create mock AsyncOpenAI client
        self.mock_llm = AsyncMock()  # pylint: disable=attribute-defined-outside-init
        self.model_name = "test_model"  # pylint: disable=attribute-defined-outside-init

        # use model_construct to bypass Pydantic validation
        self.model = BaseLLMModel.model_construct(  # pylint: disable=attribute-defined-outside-init
            name=self.model_name, llm=self.mock_llm
        )

    def test_model_initialization(self) -> None:
        """test模型初始化."""
        assert self.model.name == self.model_name
        assert self.model.llm == self.mock_llm

    def test_model_config(self) -> None:
        """test模型配置."""
        # verify config allows arbitrary types
        # verify model config exists
        assert hasattr(BaseLLMModel, "model_config")

    @pytest.mark.asyncio
    async def test_create_completion_success(self) -> None:
        """test成功创建completion."""
        test_messages = [{"role": "user", "content": "test message"}]
        test_stream = True
        expected_response = Mock()

        # Mock OpenAI client call
        self.mock_llm.chat.completions.create = AsyncMock(
            return_value=expected_response
        )

        with patch("domain.models.base.agent_config") as mock_config:
            mock_config.default_llm_timeout = 60
            mock_config.default_llm_max_token = 10000

            result = await self.model.create_completion(test_messages, test_stream)

            # Verify results
            assert result == expected_response

            # verify call parameters
            self.mock_llm.chat.completions.create.assert_called_once_with(
                messages=test_messages,
                stream=test_stream,
                model=self.model_name,
                timeout=60,
                max_tokens=10000,
            )

    @pytest.mark.asyncio
    @patch("domain.models.base.BaseLLMModel.create_completion", new_callable=AsyncMock)
    async def test_stream_success_without_span(self, mock_create: AsyncMock) -> None:
        """test无span的流式处理成功."""
        test_messages = [{"role": "user", "content": "test message"}]
        test_stream = True

        # Mock chunk data
        mock_chunk1 = Mock()
        mock_chunk1.model_dump.return_value = {"code": 0, "content": "chunk1"}
        mock_chunk1.model_dump_json.return_value = '{"content": "chunk1"}'

        mock_chunk2 = Mock()
        mock_chunk2.model_dump.return_value = {"code": 0, "content": "chunk2"}
        mock_chunk2.model_dump_json.return_value = '{"content": "chunk2"}'

        # Mock async iterator
        async def mock_response_iterator() -> AsyncIterator[Mock]:
            yield mock_chunk1
            yield mock_chunk2

        mock_response = AsyncMock()
        mock_response.__aiter__ = AsyncMock(return_value=mock_response_iterator())

        mock_create.return_value = mock_response_iterator()

        # collect streaming results
        results = []
        async for chunk in self.model.stream(test_messages, test_stream, None):
            results.append(chunk)

        # Verify results
        assert len(results) == 2
        assert results[0] == mock_chunk1
        assert results[1] == mock_chunk2

    @pytest.mark.asyncio
    @patch("domain.models.base.BaseLLMModel.create_completion", new_callable=AsyncMock)
    async def test_stream_success_with_span(self, mock_create: AsyncMock) -> None:
        """test带span的流式处理成功."""
        test_messages = [
            {"role": "user", "content": "test user message"},
            {"role": "assistant", "content": "test assistant message"},
        ]
        test_stream = True

        # Mock span
        mock_span = Mock()
        mock_span.add_info_events = Mock()

        # Mock chunk data
        mock_chunk = Mock()
        mock_chunk.model_dump.return_value = {"code": 0, "content": "chunk"}
        mock_chunk.model_dump_json.return_value = '{"content": "chunk"}'

        async def mock_response_iterator() -> AsyncIterator[Mock]:
            yield mock_chunk

        mock_create.return_value = mock_response_iterator()

        # execute streaming processing
        results = []
        async for chunk in self.model.stream(test_messages, test_stream, mock_span):
            results.append(chunk)

        # verify span calls
        assert (
            mock_span.add_info_events.call_count >= 4
        )  # messages + model + stream + chunk

        # verify specific span calls
        calls = mock_span.add_info_events.call_args_list
        message_calls = [
            call for call in calls if "user" in str(call) or "assistant" in str(call)
        ]
        assert len(message_calls) >= 2

    @pytest.mark.asyncio
    @patch("domain.models.base.llm_plugin_error")
    @patch("domain.models.base.BaseLLMModel.create_completion", new_callable=AsyncMock)
    async def test_stream_error_chunk_handling(
        self, mock_create: AsyncMock, mock_error_handler: Mock
    ) -> None:
        """test错误chunk处理."""
        test_messages = [{"role": "user", "content": "test message"}]
        test_stream = True

        # Mock error chunk
        mock_error_chunk = Mock()
        mock_error_chunk.model_dump.return_value = {
            "code": 500,
            "message": "Internal server error",
        }

        async def mock_response_iterator() -> AsyncIterator[Mock]:
            yield mock_error_chunk

        mock_create.return_value = mock_response_iterator()

        # execute and verify error handling
        results = []
        async for chunk in self.model.stream(test_messages, test_stream, None):
            results.append(chunk)

            # verify error handler is called
            mock_error_handler.assert_called_once_with(500, "Internal server error")

    @pytest.mark.asyncio
    @patch("domain.models.base.BaseLLMModel.create_completion", new_callable=AsyncMock)
    async def test_stream_api_timeout_error(self, mock_create: AsyncMock) -> None:
        """testAPI超时错误处理."""
        test_messages = [{"role": "user", "content": "test message"}]
        test_stream = True

        # pylint: disable=import-outside-toplevel
        from openai import APITimeoutError

        # pylint: disable=import-outside-toplevel
        from exceptions.plugin_exc import PluginExc

        # create a mock request object
        mock_request = Mock()
        mock_create.side_effect = APITimeoutError(mock_request)

        # verify exception handling
        with pytest.raises(PluginExc, match="请求服务超时"):
            results = []
            async for chunk in self.model.stream(test_messages, test_stream, None):
                results.append(chunk)

    @pytest.mark.asyncio
    @patch("domain.models.base.llm_plugin_error")
    @patch("domain.models.base.BaseLLMModel.create_completion", new_callable=AsyncMock)
    async def test_stream_api_error_handling(
        self, mock_create: AsyncMock, mock_error_handler: Mock
    ) -> None:
        """testAPI错误处理."""
        test_messages = [{"role": "user", "content": "test message"}]
        test_stream = True
        mock_span = Mock()
        mock_span.add_info_events = Mock()

        # pylint: disable=import-outside-toplevel
        from openai import APIError

        # Mock API error
        api_error = APIError(
            message="API Error", request=Mock(), body={"error": "Bad request"}
        )
        api_error.code = "400"

        mock_create.side_effect = api_error

        # execute and verify error handling
        results = []
        async for chunk in self.model.stream(test_messages, test_stream, mock_span):
            results.append(chunk)

        # verify span records error information
        assert mock_span.add_info_events.call_count >= 4

        # verify error handler is called
        mock_error_handler.assert_called_once_with("400", "API Error")

    @pytest.mark.asyncio
    @patch("domain.models.base.llm_plugin_error")
    @patch("domain.models.base.BaseLLMModel.create_completion", new_callable=AsyncMock)
    async def test_stream_value_error_handling(
        self, mock_create: AsyncMock, mock_error_handler: Mock
    ) -> None:
        """test值错误处理."""
        test_messages = [{"role": "user", "content": "test message"}]
        test_stream = True
        mock_span = Mock()
        mock_span.add_info_events = Mock()

        mock_create.side_effect = ValueError("Invalid value")

        # execute and verify error handling
        results = []
        async for chunk in self.model.stream(test_messages, test_stream, mock_span):
            results.append(chunk)

        # verify span records error information
        assert mock_span.add_info_events.call_count >= 3

        # verify error handler is called
        mock_error_handler.assert_called_once_with("-1", "Invalid value")

    @pytest.mark.asyncio
    @patch("domain.models.base.BaseLLMModel.create_completion", new_callable=AsyncMock)
    async def test_stream_concurrent_access(self, mock_create: AsyncMock) -> None:
        """test并发访问流式处理."""
        test_messages = [{"role": "user", "content": "test message"}]

        # Mock chunk data
        mock_chunk = Mock()
        mock_chunk.model_dump.return_value = {"code": 0, "content": "chunk"}
        mock_chunk.model_dump_json.return_value = '{"content": "chunk"}'

        def mock_response_iterator_factory() -> AsyncIterator[Mock]:
            async def mock_response_iterator() -> AsyncIterator[Mock]:
                await asyncio.sleep(0.01)  # simulate delay
                yield mock_chunk

            return mock_response_iterator()

        mock_create.side_effect = (
            lambda *_args, **_kwargs: mock_response_iterator_factory()
        )

        # create concurrent tasks
        tasks = []
        for _ in range(3):
            task = asyncio.create_task(
                self._collect_stream_results(test_messages, True)
            )
            tasks.append(task)

        # wait for all tasks to complete
        results = await asyncio.gather(*tasks)

        # verify all tasks succeeded
        for result_list in results:
            assert len(result_list) == 1
            assert result_list[0] == mock_chunk

    async def _collect_stream_results(
        self, messages: List[Dict[str, Any]], stream: bool
    ) -> List[Any]:
        """辅助方法：collect streaming results."""
        results = []
        async for chunk in self.model.stream(messages, stream, None):
            results.append(chunk)
        return results

    @pytest.mark.asyncio
    @patch("domain.models.base.BaseLLMModel.create_completion", new_callable=AsyncMock)
    async def test_unicode_message_handling(self, mock_create: AsyncMock) -> None:
        """testUnicode消息处理."""
        unicode_messages = [
            {"role": "user", "content": "test中文消息🚀"},
            {"role": "assistant", "content": "中文回复✅"},
        ]
        test_stream = True
        mock_span = Mock()
        mock_span.add_info_events = Mock()

        # Mock chunk data
        mock_chunk = Mock()
        mock_chunk.model_dump.return_value = {"code": 0, "content": "中文响应"}
        mock_chunk.model_dump_json.return_value = '{"content": "中文响应"}'

        async def mock_response_iterator() -> AsyncIterator[Mock]:
            yield mock_chunk

        mock_create.return_value = mock_response_iterator()

        # execute streaming processing
        results = []
        async for chunk in self.model.stream(unicode_messages, test_stream, mock_span):
            results.append(chunk)

        # verify Unicode content is handled correctly
        assert len(results) == 1
        assert results[0] == mock_chunk

        # verify span recorded Unicode content
        calls = mock_span.add_info_events.call_args_list
        unicode_calls = [
            call for call in calls if any("test中文" in str(arg) for arg in call.args)
        ]
        assert len(unicode_calls) > 0

    @pytest.mark.asyncio
    @patch("domain.models.base.BaseLLMModel.create_completion", new_callable=AsyncMock)
    async def test_empty_messages_handling(self, mock_create: AsyncMock) -> None:
        """test空消息列表处理."""
        empty_messages: List[Dict[str, Any]] = []
        test_stream = True

        async def mock_empty_response() -> AsyncIterator[Any]:
            # empty async generator for simulating no response scenario
            return
            yield  # pylint: disable=unreachable

        mock_create.return_value = mock_empty_response()

        # execute streaming processing
        results = []
        async for chunk in self.model.stream(empty_messages, test_stream, None):
            results.append(chunk)

        # verify empty message handling
        assert len(results) == 0

    def test_model_attribute_access(self) -> None:
        """test模型属性访问."""
        # test name access
        assert self.model.name == self.model_name

        # test LLM client access
        assert self.model.llm == self.mock_llm

        # test attribute setting
        new_name = "new_model_name"
        self.model.name = new_name
        assert self.model.name == new_name
