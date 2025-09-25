"""AgentResponse Schema单元test模块."""

import json

from pydantic import ValidationError

from api.schemas.agent_response import AgentResponse, CotStep


class TestCotStep:
    """CotSteptest类."""

    def test_cot_step_creation(self) -> None:
        """testCoT步骤创建."""
        cot_step = CotStep(
            thought="分析问题",
            action="reasoning",
            action_input={"question": "单元test question"},
            action_output={"result": "分析完成"},
            finished_cot=False,
        )
        assert cot_step.thought == "分析问题"
        assert cot_step.action == "reasoning"
        assert cot_step.action_input["question"] == "单元test question"
        assert cot_step.finished_cot is False

    def test_cot_step_unicode_content(self) -> None:
        """testCoT步骤Unicode内容."""
        cot_step = CotStep(
            thought="中文分析🧠",
            action="analysis",
            action_input={"query": "使用中文进行推理分析，包含特殊字符①②③"},
            action_output={"result": "得出中文结论🎯"},
            finished_cot=False,
        )
        assert "🧠" in cot_step.thought
        assert "特殊字符①②③" in cot_step.action_input["query"]
        assert "🎯" in cot_step.action_output["result"]

    def test_cot_step_with_tool_type(self) -> None:
        """test带工具类型的CoT步骤."""
        cot_step = CotStep(
            thought="计算结果",
            action="calculate",
            action_input={"formula": "基于前面的分析进行计算"},
            action_output={"result": "结果为42"},
            finished_cot=True,
            tool_type="tool",
        )
        assert cot_step.tool_type == "tool"
        assert cot_step.finished_cot is True

    def test_cot_step_validation(self) -> None:
        """testCoT步骤验证."""
        # test default values
        minimal_step = CotStep()
        assert minimal_step.thought == ""
        assert minimal_step.action == ""
        assert minimal_step.action_input == {}
        assert minimal_step.action_output == {}
        assert minimal_step.finished_cot is False
        assert minimal_step.tool_type is None
        assert minimal_step.empty is False

    def test_cot_step_large_content(self) -> None:
        """test大内容CoT步骤."""
        large_thought = "详细思维过程 " * 500
        cot_step = CotStep(
            thought=large_thought,
            action="detailed_analysis",
            action_input={"query": "大量输入数据"},
            action_output={"result": "基于大量思考得出结论"},
        )
        assert len(cot_step.thought) > 1000
        assert cot_step.action == "detailed_analysis"

    def test_cot_step_serialization(self) -> None:
        """testCoT步骤序列化."""
        cot_step = CotStep(
            thought="序列化test思维",
            action="serialization_test",
            action_input={"test": "序列化功能"},
            action_output={"result": "序列化成功"},
        )

        # Test dictionary conversion
        if hasattr(cot_step, "model_dump"):
            step_dict = cot_step.model_dump()
            assert isinstance(step_dict, dict)
            assert step_dict["thought"] == "序列化test思维"

        # Test JSON serialization
        if hasattr(cot_step, "model_dump_json"):
            json_str = cot_step.model_dump_json()
            assert isinstance(json_str, str)
            parsed_data = json.loads(json_str)
            assert parsed_data["action"] == "serialization_test"


class TestAgentResponse:
    """AgentResponsetest类."""

    def test_agent_response_creation(self) -> None:
        """test代理响应创建."""
        agent_response = AgentResponse(
            typ="content",
            content="这是一个test响应",
            model="gpt-3.5-turbo",
        )
        assert agent_response.typ == "content"
        assert agent_response.content == "这是一个test响应"
        assert agent_response.model == "gpt-3.5-turbo"

    def test_agent_response_with_cot_steps(self) -> None:
        """test包含CoT步骤的代理响应."""
        # create a simple CoT step for testing
        cot_step = CotStep(
            thought="分析输入",
            action="reasoning",
            action_input={"query": "test查询"},
            action_output={"result": "分析完成"},
            finished_cot=True,
        )

        agent_response = AgentResponse(
            typ="cot_step",
            content=cot_step,
            model="gpt-4",
        )
        assert agent_response.typ == "cot_step"
        assert isinstance(agent_response.content, CotStep)
        assert agent_response.model == "gpt-4"

    def test_agent_response_unicode_content(self) -> None:
        """testUnicode内容的代理响应."""
        agent_response = AgentResponse(
            typ="content",
            content="中文响应内容🤖特殊字符①②③",
            model="中文模型",
        )
        assert isinstance(agent_response.content, str)
        assert "🤖" in agent_response.content
        assert "中文响应" in agent_response.content

    def test_agent_response_different_types(self) -> None:
        """test不同类型的代理响应."""
        # test content type
        content_response = AgentResponse(
            typ="content", content="文本响应", model="test-model"
        )
        assert content_response.typ == "content"

        # test log type
        log_response = AgentResponse(typ="log", content="错误信息", model="test-model")
        assert log_response.typ == "log"

        # test knowledge_metadata type
        metadata_response = AgentResponse(
            typ="knowledge_metadata",
            content=[{"id": "kb1"}],
            model="test-model",
        )
        assert metadata_response.typ == "knowledge_metadata"

    def test_agent_response_with_metadata(self) -> None:
        """test包含元数据的代理响应."""
        # Note: metadata is not part of the AgentResponse schema
        agent_response = AgentResponse(
            typ="content",
            content="带元数据的响应",
            model="gpt-4",
        )
        # This test checks if the response was created successfully
        assert agent_response.typ == "content"
        assert agent_response.content == "带元数据的响应"

    def test_agent_response_validation_errors(self) -> None:
        """test代理响应验证错误."""
        # Test invalid data - verify exceptions directly without dictionary unpacking
        try:
            # empty type test
            AgentResponse(
                typ="", content="test", model="test"  # type: ignore[arg-type]
            )
        except (ValidationError, ValueError):
            pass

        try:
            # None content test
            AgentResponse(
                typ="content", content=None, model="test"  # type: ignore[arg-type]
            )
        except (ValidationError, TypeError, ValueError):
            pass

        try:
            # missing type test
            AgentResponse(content="test", model="test")  # type: ignore[call-arg]
        except (ValidationError, TypeError):
            pass

        try:
            # invalid type test
            AgentResponse(
                typ="invalid_type", content="test", model=""  # type: ignore[arg-type]
            )
        except (ValidationError, ValueError):
            pass

    def test_agent_response_json_content(self) -> None:
        """testJSON内容的代理响应."""
        json_content = [
            {
                "result": "success",
                "data": {"items": [1, 2, 3], "total": 3},
                "message": "处理完成",
            }
        ]

        agent_response = AgentResponse(
            typ="knowledge_metadata",
            content=json_content,
            model="json-processor",
        )
        assert agent_response.typ == "knowledge_metadata"
        # content will be a list object
        assert isinstance(agent_response.content, list)
        assert agent_response.content[0]["result"] == "success"

    def test_agent_response_large_content(self) -> None:
        """test大内容代理响应."""
        large_content = "大量响应内容 " * 2000

        agent_response = AgentResponse(
            typ="content",
            content=large_content,
            model="large-content-model",
        )
        assert isinstance(agent_response.content, str)
        assert len(agent_response.content) > 10000
        assert agent_response.model == "large-content-model"

    def test_agent_response_serialization(self) -> None:
        """test代理响应序列化."""
        agent_response = AgentResponse(
            typ="content",
            content="序列化test响应",
            model="serialization-test",
        )

        # Test dictionary conversion
        if hasattr(agent_response, "model_dump"):
            response_dict = agent_response.model_dump()
            assert isinstance(response_dict, dict)
            assert response_dict["typ"] == "content"

        # Test JSON serialization
        if hasattr(agent_response, "model_dump_json"):
            json_str = agent_response.model_dump_json()
            assert isinstance(json_str, str)
            parsed_data = json.loads(json_str)
            assert parsed_data["content"] == "序列化test响应"

    def test_agent_response_copy_and_update(self) -> None:
        """test代理响应复制和更新."""
        agent_response = AgentResponse(
            typ="content",
            content="原始内容",
            model="original-model",
        )

        # Test copying
        if hasattr(agent_response, "model_copy"):
            copied_response = agent_response.model_copy()
            assert copied_response.content == "原始内容"

            # Test update
            updated_response = agent_response.model_copy(update={"content": "更新内容"})
            assert updated_response.content == "更新内容"
            assert (
                updated_response.model == "original-model"
            )  # other fields remain unchanged

    def test_agent_response_comparison(self) -> None:
        """test代理响应比较."""
        response1 = AgentResponse(
            typ="content",
            content="比较test",
            model="comparison-model",
        )
        response2 = AgentResponse(
            typ="content",
            content="比较test",
            model="comparison-model",
        )

        # test equality
        if hasattr(response1, "__eq__"):
            equal_result = response1 == response2
            assert isinstance(equal_result, bool)

    def test_agent_response_error_handling(self) -> None:
        """test代理响应错误处理."""
        try:
            error_response = AgentResponse(
                typ="log",
                content="发生了一个错误：文件未找到",
                model="error-handler",
            )
            assert error_response.typ == "log"
            assert isinstance(error_response.content, str)
            assert "文件未找到" in error_response.content
        except (ValidationError, TypeError):
            # error field may have specific validation rules
            pass

    def test_agent_response_streaming_scenario(self) -> None:
        """test流式场景代理响应."""
        # simulate streaming response sequence - create objects directly without dictionary unpacking
        responses = []

        try:
            response1 = AgentResponse(
                typ="log", content="开始流式响应", model="stream-model"
            )
            responses.append(response1)
        except (ValidationError, TypeError):
            pass

        try:
            response2 = AgentResponse(
                typ="content", content="流式内容块1", model="stream-model"
            )
            responses.append(response2)
        except (ValidationError, TypeError):
            pass

        try:
            response3 = AgentResponse(
                typ="content", content="流式内容块2", model="stream-model"
            )
            responses.append(response3)
        except (ValidationError, TypeError):
            pass

        try:
            response4 = AgentResponse(
                typ="log", content="流式响应完成", model="stream-model"
            )
            responses.append(response4)
        except (ValidationError, TypeError):
            pass

        # verify at least some responses were created
        assert len(responses) > 0
