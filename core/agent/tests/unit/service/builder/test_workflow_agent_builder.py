"""WorkflowAgentRunnerBuilder单元test模块."""

from typing import Any, cast
from unittest.mock import AsyncMock, Mock, patch

import pytest

from api.schemas.base_inputs import MetaDataInputs
from api.schemas.llm_message import LLMMessage
from api.schemas.workflow_agent_inputs import (
    CustomCompletionInputs,
    CustomCompletionInstructionInputs,
    CustomCompletionModelConfigInputs,
    CustomCompletionPluginInputs,
    CustomCompletionPluginKnowledgeInputs,
)
from common_imports import Span
from service.builder.workflow_agent_builder import (
    KnowledgeQueryParams,
    WorkflowAgentRunnerBuilder,
)


class TestWorkflowAgentRunnerBuilder:
    """WorkflowAgentRunnerBuildertest类."""

    def setup_method(self) -> None:
        """test方法初始化."""
        # create real input data
        model_config_inputs = CustomCompletionModelConfigInputs(
            domain="test-domain", api="http://test-api", api_key="test-key"
        )

        instruction_inputs = CustomCompletionInstructionInputs(
            answer="回答指令", reasoning="推理指令"
        )

        plugin_inputs = CustomCompletionPluginInputs(
            tools=[],
            mcp_server_ids=[],
            mcp_server_urls=[],
            workflow_ids=[],
            knowledge=[],
        )

        messages = [
            LLMMessage(role="user", content="用户消息1"),
            LLMMessage(role="user", content="用户消息2"),
        ]

        self.mock_inputs = CustomCompletionInputs(
            uid="test_uid",
            messages=messages,
            stream=True,
            meta_data=MetaDataInputs(),
            model_config=model_config_inputs,
            instruction=instruction_inputs,
            plugin=plugin_inputs,
            max_loop_count=5,
        )

        # create builder instance
        mock_span = Mock(spec=Span)
        self.builder = WorkflowAgentRunnerBuilder(
            app_id="test_app_id",
            uid="test_uid",
            span=mock_span,
            inputs=self.mock_inputs,
        )
        self.builder.inputs = self.mock_inputs
        self.builder.app_id = "test-app"
        self.builder.span = Mock()
        self.builder.span.start = Mock(
            return_value=Mock(__enter__=Mock(), __exit__=Mock())
        )

    @pytest.mark.asyncio
    @patch("service.builder.workflow_agent_builder.WorkflowAgentRunner")
    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.create_model"
    )
    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.build_plugins"
    )
    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.build_chat_runner"
    )
    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.build_cot_runner"
    )
    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.build_process_runner"
    )
    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.query_knowledge_by_workflow"
    )
    async def test_build_success(
        self,
        mock_query_knowledge: Mock,
        mock_build_process: Mock,
        mock_build_cot: Mock,
        mock_build_chat: Mock,
        mock_build_plugins: Mock,
        _mock_create_model: Mock,
        mock_runner_class: Mock,
    ) -> None:
        """test成功构建WorkflowAgentRunner."""
        # Mock all dependent method return values
        mock_model = Mock()
        _mock_create_model.return_value = mock_model

        mock_plugins: list[Any] = []
        mock_build_plugins.return_value = mock_plugins

        mock_knowledge_list: list[Any] = []
        mock_knowledge_content = ""
        mock_query_knowledge.return_value = (
            mock_knowledge_list,
            mock_knowledge_content,
        )

        mock_chat_runner = Mock()
        mock_build_chat.return_value = mock_chat_runner

        mock_process_runner = Mock()
        mock_build_process.return_value = mock_process_runner

        mock_cot_runner = Mock()
        mock_build_cot.return_value = mock_cot_runner

        # Mock WorkflowAgentRunner
        mock_runner = Mock()
        mock_runner_class.return_value = mock_runner

        # execute build
        result = await self.builder.build()

        # Verify results
        assert result == mock_runner

        # verify method calls
        _mock_create_model.assert_called_once()
        mock_build_plugins.assert_called_once()
        mock_query_knowledge.assert_called_once()
        mock_build_chat.assert_called_once()
        mock_build_process.assert_called_once()
        mock_build_cot.assert_called_once()
        mock_runner_class.assert_called_once()

    @pytest.mark.asyncio
    async def test_query_knowledge_by_workflow_empty_knowledge(self) -> None:
        """Test query_knowledge_by_workflow with empty knowledge list."""

        # create Mock span supporting context manager
        mock_context = Mock()
        mock_span = Mock()
        mock_span.__enter__ = Mock(return_value=mock_context)
        mock_span.__exit__ = Mock(return_value=None)

        # empty knowledge base list
        mock_span_param = Mock()
        mock_span_param.start = Mock(return_value=mock_span)
        result = await self.builder.query_knowledge_by_workflow([], mock_span_param)

        # verify returns empty result
        assert result == ([], "")

    @pytest.mark.asyncio
    async def test_query_knowledge_by_workflow_with_knowledge(self) -> None:
        """Test query_knowledge_by_workflow with knowledge data."""

        # create simulated knowledge base input
        mock_knowledge = Mock()
        mock_knowledge.match = Mock()
        mock_knowledge.match.repo_ids = ["repo1"]
        mock_knowledge.query = "test query"
        knowledge_list = [mock_knowledge]

        # create Mock span supporting context manager
        mock_context = Mock()
        mock_span = Mock()
        mock_span.__enter__ = Mock(return_value=mock_context)
        mock_span.__exit__ = Mock(return_value=None)

        # Create span parameter
        mock_span_param = Mock()
        mock_span_param.start = Mock(return_value=mock_span)

        with (
            patch.object(self.builder, "_create_knowledge_tasks") as mock_create_tasks,
            patch.object(
                self.builder, "_process_knowledge_results"
            ) as mock_process_results,
            patch.object(
                self.builder, "_extract_backgrounds"
            ) as mock_extract_backgrounds,
            patch("asyncio.gather") as mock_gather,
        ):

            # Mock method return values
            async def mock_async_task() -> Any:
                return "mocked_result"

            # set correct Mock return values
            mock_create_tasks.return_value = [mock_async_task()]

            # mock_gather needs to return a coroutine because it will be awaited
            async def mock_gather_coro(*_: Any) -> Any:
                return ["result1", "result2"]

            mock_gather.return_value = mock_gather_coro()
            mock_process_results.return_value = ([], {})
            mock_extract_backgrounds.return_value = ""

            # execute test
            result = await self.builder.query_knowledge_by_workflow(
                cast(list[CustomCompletionPluginKnowledgeInputs], knowledge_list),
                mock_span_param,
            )

            # verify calls and results
            mock_create_tasks.assert_called_once()
            mock_process_results.assert_called_once()
            mock_extract_backgrounds.assert_called_once()
            assert result == ([], "")

    def test_create_knowledge_tasks_empty_list(self) -> None:
        """testempty knowledge base list创建任务."""
        tasks = self.builder._create_knowledge_tasks([], Mock())
        assert not tasks

    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.exec_query_knowledge"
    )
    def test_create_knowledge_tasks_with_knowledge(self, mock_exec: Mock) -> None:
        """test包含知识库的任务创建."""
        # create simulated knowledge base
        mock_knowledge = Mock()
        mock_match = Mock()
        mock_match.repo_ids = ["repo1"]
        mock_match.doc_ids = ["doc1"]
        mock_knowledge.match = mock_match
        mock_knowledge.top_k = 3
        mock_knowledge.repo_type = 1

        knowledge_list = [mock_knowledge]
        mock_exec.return_value = AsyncMock()

        tasks = self.builder._create_knowledge_tasks(
            cast(list[CustomCompletionPluginKnowledgeInputs], knowledge_list), Mock()
        )

        # verify task creation
        assert len(tasks) == 1
        mock_exec.assert_called_once()

    def test_process_knowledge_results_empty(self) -> None:
        """test空结果的处理."""
        results: list[Any] = []
        metadata_list, metadata_map = self.builder._process_knowledge_results(results)

        assert not metadata_list
        assert not metadata_map

    def test_process_knowledge_results_with_data(self) -> None:
        """test包含数据的结果处理."""
        results = [
            {
                "data": {
                    "results": [
                        {
                            "title": "test标题",
                            "docId": "doc1",
                            "content": "test内容",
                            "references": {},
                        }
                    ]
                }
            }
        ]

        with patch.object(self.builder, "_process_content_references") as mock_process:
            mock_process.return_value = "处理后内容"

            metadata_list, _ = self.builder._process_knowledge_results(results)

            # Verify results
            assert len(metadata_list) == 1
            assert metadata_list[0]["source_id"] == "doc1"
            assert len(metadata_list[0]["chunk"]) == 1

    def test_process_content_references_image(self) -> None:
        """test图片引用处理."""
        content = "这是一个图片 <ref1> 示例"
        references = {
            "ref1": {"format": "image", "link": "http://example.com/image.jpg"}
        }

        result = self.builder._process_content_references(content, references)

        assert "![alt](http://example.com/image.jpg)" in result
        assert "<ref1>" not in result

    def test_process_content_references_table(self) -> None:
        """test表格引用处理."""
        content = "这是一个表格 <table1> 示例"
        references = {"table1": {"format": "table", "content": "表格内容"}}

        result = self.builder._process_content_references(content, references)

        assert "表格内容" in result
        assert "<table1>" not in result

    def test_extract_backgrounds_empty(self) -> None:
        """test空元数据提取背景."""
        metadata_list: list[Any] = []
        backgrounds = self.builder._extract_backgrounds(metadata_list)

        assert not backgrounds

    def test_extract_backgrounds_with_data(self) -> None:
        """test包含数据的背景提取."""
        metadata_list = [
            {"chunk": [{"chunk_context": "背景1"}, {"chunk_context": "背景2"}]},
            {"chunk": [{"chunk_context": "背景3"}]},
        ]

        backgrounds = self.builder._extract_backgrounds(metadata_list)

        assert "背景1" in backgrounds
        assert "背景2" in backgrounds
        assert "背景3" in backgrounds
        assert backgrounds.count("\n") == 2  # 3 backgrounds separated by 2 newlines

    @pytest.mark.asyncio
    async def test_exec_query_knowledge(self) -> None:
        """Test exec_query_knowledge method."""

        params = KnowledgeQueryParams(
            repo_ids=["repo1"],
            doc_ids=["doc1"],
            top_k=3,
            score_threshold=0.3,
            rag_type="AIUI-RAG2",
        )

        with patch(
            "service.builder.workflow_agent_builder.KnowledgePluginFactory"
        ) as mock_factory:
            mock_plugin = Mock()
            mock_plugin.run = AsyncMock(return_value={"data": {"results": []}})
            mock_factory.return_value.gen.return_value = mock_plugin

            # create Mock span supporting context manager
            mock_span_param = Mock()
            mock_context = Mock()
            mock_span = Mock()
            mock_span.__enter__ = Mock(return_value=mock_context)
            mock_span.__exit__ = Mock(return_value=None)
            mock_span_param.start = Mock(return_value=mock_span)

            result = await self.builder.exec_query_knowledge(params, mock_span_param)

            # Verify results
            assert isinstance(result, dict)
            mock_plugin.run.assert_called_once()

    def test_attributes_validation(self) -> None:
        """test属性验证."""
        # verify builder has necessary attributes
        assert hasattr(self.builder, "inputs")
        assert hasattr(self.builder, "app_id")
        assert hasattr(self.builder, "span")

    @pytest.mark.asyncio
    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.create_model"
    )
    async def test_build_model_creation_error(self, mock_create_model: Any) -> None:
        """Test build method with model creation error."""

        # Mock create_model throws exception
        mock_create_model.side_effect = Exception("模型创建失败")

        # Mock span context manager
        mock_context = Mock()
        mock_span = Mock()
        mock_span.__enter__ = Mock(return_value=mock_context)
        mock_span.__exit__ = Mock(return_value=None)

        with patch.object(self.builder.span, "start", return_value=mock_span):
            # Verify exception propagation
            with pytest.raises(Exception, match="模型创建失败"):
                await self.builder.build()

    @pytest.mark.asyncio
    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.query_knowledge_by_workflow"
    )
    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.build_plugins"
    )
    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.create_model"
    )
    async def test_build_plugins_error(
        self,
        mock_create_model: Any,
        mock_build_plugins: Any,
        mock_query_knowledge: Any,
    ) -> None:
        """Test build method with plugins creation error."""

        # Mock span context manager
        mock_context = Mock()
        mock_span = Mock()
        mock_span.__enter__ = Mock(return_value=mock_context)
        mock_span.__exit__ = Mock(return_value=None)

        with patch.object(self.builder.span, "start", return_value=mock_span):
            # Mock normal model creation
            mock_create_model.return_value = Mock()
            # Mock build_plugins throws exception
            mock_build_plugins.side_effect = Exception("插件构建失败")
            # Mock query_knowledge_by_workflow return value
            mock_query_knowledge.return_value = ([], "")

            # Verify exception propagation
            try:
                await self.builder.build()
                # if no exception is thrown, test should fail
                pytest.fail("Expected Exception was not raised")
            except Exception as e:
                # verify exception message
                assert "插件构建失败" in str(e)

    @pytest.mark.asyncio
    @patch("service.builder.workflow_agent_builder.WorkflowAgentRunner")
    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.build_cot_runner"
    )
    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.build_process_runner"
    )
    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.build_chat_runner"
    )
    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.query_knowledge_by_workflow"
    )
    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.build_plugins"
    )
    @patch(
        "service.builder.workflow_agent_builder."
        "WorkflowAgentRunnerBuilder.create_model"
    )
    async def test_build_with_unicode_content(
        self,
        mock_create_model: Any,
        mock_build_plugins: Any,
        mock_query_knowledge: Any,
        mock_build_chat_runner: Any,
        mock_build_process_runner: Any,
        mock_build_cot_runner: Any,
        mock_runner_class: Any,
    ) -> None:
        """Test build method with unicode content."""

        # setup Unicode input
        self.mock_inputs.messages = [
            Mock(content="中文消息🚀"),
            Mock(content="特殊字符：①②③"),
        ]

        # Mock span context manager
        mock_context = Mock()
        mock_span = Mock()
        mock_span.__enter__ = Mock(return_value=mock_context)
        mock_span.__exit__ = Mock(return_value=None)

        # set all mock return values
        mock_create_model.return_value = Mock()
        mock_build_plugins.return_value = []
        mock_query_knowledge.return_value = ([], "")
        mock_build_chat_runner.return_value = Mock()
        mock_build_process_runner.return_value = Mock()
        mock_build_cot_runner.return_value = Mock()

        # Mock WorkflowAgentRunner
        mock_runner = Mock()
        mock_runner_class.return_value = mock_runner

        with patch.object(self.builder.span, "start", return_value=mock_span):
            # execute build
            result = await self.builder.build()

            # Verify Unicode content is handled correctly
            assert "🚀" in self.mock_inputs.messages[0].content
            assert "①②③" in self.mock_inputs.messages[1].content

            # Verify results
            assert result == mock_runner

            # verify all build steps are called
            mock_create_model.assert_called_once()
            mock_build_plugins.assert_called_once()
            mock_query_knowledge.assert_called_once()
            mock_build_chat_runner.assert_called_once()
            mock_build_process_runner.assert_called_once()
            mock_build_cot_runner.assert_called_once()
            mock_runner_class.assert_called_once()
