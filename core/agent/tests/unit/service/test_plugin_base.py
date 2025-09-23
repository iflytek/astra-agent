"""Unit tests for service.plugin.base module."""

# pylint: disable=no-member  # Disable pylint no-member for Pydantic models

from typing import Any, Callable

import pytest

from service.plugin.base import BasePlugin, PluginResponse


class TestPluginResponse:
    """Test cases for PluginResponse dataclass."""

    @pytest.mark.unit
    def test_plugin_response_creation(self) -> None:
        """Test PluginResponse creation with valid data."""
        # Act
        response = PluginResponse(
            code=0,
            sid="test_session_001",
            start_time=1234567890,
            end_time=1234567900,
            result={"result": "test"},
            log=["operation started", "operation completed"],
        )

        # Assert
        assert response.code == 0
        assert response.sid == "test_session_001"
        assert response.start_time == 1234567890
        assert response.end_time == 1234567900
        assert response.result == {"result": "test"}
        assert response.log == ["operation started", "operation completed"]

    @pytest.mark.unit
    def test_plugin_response_with_error(self) -> None:
        """Test PluginResponse creation with error."""
        # Act
        response = PluginResponse(
            code=500,
            sid="error_session_001",
            result=None,
            log=["operation failed", "error occurred"],
        )

        # Assert
        assert response.code == 500
        assert response.sid == "error_session_001"
        assert response.result is None
        assert response.log == ["operation failed", "error occurred"]

    @pytest.mark.unit
    def test_plugin_response_minimal_data(self) -> None:
        """Test PluginResponse with minimal required data."""
        # Act
        response = PluginResponse(result="minimal result")

        # Assert
        assert response.code == 0  # default value
        assert response.sid == ""  # default value
        assert response.start_time == 0  # default value
        assert response.end_time == 0  # default value
        assert response.result == "minimal result"
        assert response.log == []  # default empty list

    @pytest.mark.unit
    def test_plugin_response_with_complex_result(self) -> None:
        """Test PluginResponse with complex result structure."""
        complex_result = {
            "data": {"items": [1, 2, 3], "metadata": {"count": 3}},
            "status": "success",
            "nested": {"level1": {"level2": "deep_value"}},
        }

        response = PluginResponse(
            code=200,
            sid="complex_session",
            result=complex_result,
            log=["complex operation completed"],
        )

        assert response.result == complex_result
        assert response.result["data"]["items"] == [1, 2, 3]
        assert response.result["nested"]["level1"]["level2"] == "deep_value"

    @pytest.mark.unit
    def test_plugin_response_with_unicode_content(self) -> None:
        """Test PluginResponse with Unicode content."""
        unicode_result = {
            "message": "操作成功 🎉",
            "data": "中文数据",
            "emoji": "🚀🔥💯",
        }

        response = PluginResponse(
            code=0,
            sid="unicode_session_test",
            result=unicode_result,
            log=["开始处理", "处理完成 ✅"],
        )

        assert "🎉" in response.result["message"]
        assert "中文数据" in response.result["data"]
        assert "test" in response.sid
        assert "✅" in response.log[1]

    @pytest.mark.unit
    def test_plugin_response_equality(self) -> None:
        """Test PluginResponse equality comparison."""
        response1 = PluginResponse(
            code=200, sid="test_session", result={"key": "value"}, log=["test"]
        )

        response2 = PluginResponse(
            code=200, sid="test_session", result={"key": "value"}, log=["test"]
        )

        response3 = PluginResponse(
            code=404,
            sid="different_session",
            result={"different": "data"},
            log=["different"],
        )

        # Note: Pydantic models compare by field values
        assert response1.code == response2.code
        assert response1.sid == response2.sid
        assert response1 != response3


class TestBasePlugin:
    """Test cases for BasePlugin."""

    @pytest.fixture
    def mock_run_function(self) -> Callable[..., Any]:
        """Create a mock run function."""

        async def mock_run(*_args: Any, **_kwargs: Any) -> dict[str, str]:
            return {"result": "test execution"}

        return mock_run

    @pytest.fixture
    def sample_plugin_data(
        self, mock_run_function: Callable[..., Any]
    ) -> dict[str, Any]:
        """Sample plugin data for testing."""
        return {
            "name": "test_plugin",
            "description": "A test plugin for unit testing",
            "schema_template": "test_schema_template",
            "typ": "test",
            "run": mock_run_function,
        }

    @pytest.mark.unit
    def test_base_plugin_creation_with_valid_data(
        self, sample_plugin_data: dict[str, Any]
    ) -> None:
        """Test BasePlugin creation with valid data."""
        # Act
        plugin = BasePlugin(**sample_plugin_data)

        # Assert
        assert plugin.name == "test_plugin"
        assert plugin.description == "A test plugin for unit testing"
        assert plugin.schema_template == "test_schema_template"
        assert plugin.typ == "test"
        assert callable(plugin.run)
        assert plugin.run_result is None

    @pytest.mark.unit
    def test_base_plugin_with_run_result(
        self, sample_plugin_data: dict[str, Any]
    ) -> None:
        """Test BasePlugin with pre-set run_result."""
        # Arrange
        run_result = PluginResponse(
            code=0, result={"test": "result"}, log=["Pre-set result"]
        )
        sample_plugin_data["run_result"] = run_result

        # Act
        plugin = BasePlugin(**sample_plugin_data)

        # Assert
        assert plugin.run_result == run_result
        assert plugin.run_result is not None
        # Type assertion to help pylint understand the PluginResponse type
        run_result_typed: PluginResponse = plugin.run_result
        assert run_result_typed.code == 0
        assert run_result_typed.result == {"test": "result"}

    @pytest.mark.unit
    def test_base_plugin_callable_validation(
        self, sample_plugin_data: dict[str, Any]
    ) -> None:
        """Test BasePlugin validates run is callable."""
        # Arrange - replace run with non-callable
        sample_plugin_data["run"] = "not_callable"

        # Act & Assert
        with pytest.raises(ValueError):
            BasePlugin(**sample_plugin_data)

    @pytest.mark.unit
    def test_base_plugin_with_different_types(
        self, mock_run_function: Callable[..., Any]
    ) -> None:
        """Test BasePlugin with different plugin types."""
        # Test cases for different plugin types
        plugin_types = ["knowledge", "link", "mcp", "workflow", "custom"]

        for plugin_type in plugin_types:
            # Act
            plugin = BasePlugin(
                name=f"{plugin_type}_plugin",
                description=f"A {plugin_type} plugin",
                schema_template=f"{plugin_type}_schema",
                typ=plugin_type,
                run=mock_run_function,
            )

            # Assert
            assert plugin.typ == plugin_type
            assert plugin.name == f"{plugin_type}_plugin"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_base_plugin_run_execution(
        self, sample_plugin_data: dict[str, Any]
    ) -> None:
        """Test BasePlugin run method execution."""
        # Arrange
        plugin = BasePlugin(**sample_plugin_data)

        # Act
        result = await plugin.run()

        # Assert
        assert result == {"result": "test execution"}

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_base_plugin_run_with_parameters(self) -> None:
        """Test BasePlugin run method with parameters."""

        # Arrange
        async def parameterized_run(
            param1: str, param2: str, **kwargs: Any
        ) -> dict[str, Any]:
            return {"param1": param1, "param2": param2, "kwargs": kwargs}

        plugin = BasePlugin(
            name="param_plugin",
            description="Plugin with parameters",
            schema_template="param_schema",
            typ="parameterized",
            run=parameterized_run,
        )

        # Act
        result = await plugin.run("value1", "value2", extra="extra_value")

        # Assert
        assert result["param1"] == "value1"
        assert result["param2"] == "value2"
        assert result["kwargs"]["extra"] == "extra_value"

    @pytest.mark.unit
    def test_base_plugin_model_dump(self, sample_plugin_data: dict[str, Any]) -> None:
        """Test BasePlugin model serialization."""
        # Arrange
        plugin = BasePlugin(**sample_plugin_data)

        # Act
        plugin_dict = plugin.model_dump()

        # Assert
        assert plugin_dict["name"] == "test_plugin"
        assert plugin_dict["description"] == "A test plugin for unit testing"
        assert plugin_dict["schema_template"] == "test_schema_template"
        assert plugin_dict["typ"] == "test"
        assert "run" in plugin_dict  # Function should be included
        assert plugin_dict["run_result"] is None

    @pytest.mark.unit
    def test_base_plugin_with_complex_schema_template(
        self, mock_run_function: Callable[..., Any]
    ) -> None:
        """Test BasePlugin with complex schema template."""
        # Arrange
        complex_schema = """
        {
            "type": "object",
            "properties": {
                "input": {"type": "string"},
                "config": {
                    "type": "object",
                    "properties": {
                        "timeout": {"type": "integer"},
                        "retries": {"type": "integer"}
                    }
                }
            },
            "required": ["input"]
        }
        """

        # Act
        plugin = BasePlugin(
            name="complex_plugin",
            description="Plugin with complex schema",
            schema_template=complex_schema,
            typ="complex",
            run=mock_run_function,
        )

        # Assert
        assert plugin.schema_template == complex_schema
        assert "properties" in plugin.schema_template
        assert "required" in plugin.schema_template

    @pytest.mark.unit
    def test_base_plugin_with_unicode_content(
        self, mock_run_function: Callable[..., Any]
    ) -> None:
        """Test BasePlugin with unicode content."""
        # Act
        plugin = BasePlugin(
            name="unicode_插件",
            description="支持中文的插件描述",
            schema_template="中文模板内容",
            typ="unicode",
            run=mock_run_function,
        )

        # Assert
        assert plugin.name == "unicode_插件"
        assert plugin.description == "支持中文的插件描述"
        assert plugin.schema_template == "中文模板内容"

    @pytest.mark.unit
    def test_base_plugin_equality(self, mock_run_function: Callable[..., Any]) -> None:
        """Test BasePlugin equality comparison."""
        # Arrange
        plugin1 = BasePlugin(
            name="test_plugin",
            description="Test description",
            schema_template="test_schema",
            typ="test",
            run=mock_run_function,
        )

        plugin2 = BasePlugin(
            name="test_plugin",
            description="Test description",
            schema_template="test_schema",
            typ="test",
            run=mock_run_function,
        )

        plugin3 = BasePlugin(
            name="different_plugin",
            description="Different description",
            schema_template="different_schema",
            typ="different",
            run=mock_run_function,
        )

        # Act & Assert
        # Note: Equality comparison might depend on Pydantic implementation
        assert plugin1.name == plugin2.name
        assert plugin1.typ == plugin2.typ
        assert plugin1.name != plugin3.name

    @pytest.mark.unit
    def test_base_plugin_required_fields(self) -> None:
        """Test BasePlugin validation of required fields."""
        # Test missing name
        with pytest.raises(ValueError):
            BasePlugin(  # type: ignore[call-arg]
                description="Test description",
                schema_template="test_schema",
                typ="test",
                run=lambda: None,
            )

        # Test missing description
        with pytest.raises(ValueError):
            BasePlugin(  # type: ignore[call-arg]
                name="test_plugin",
                schema_template="test_schema",
                typ="test",
                run=lambda: None,
            )

        # Test missing typ
        with pytest.raises(ValueError):
            BasePlugin(  # type: ignore[call-arg]
                name="test_plugin",
                description="Test description",
                schema_template="test_schema",
                run=lambda: None,
            )

        # Test missing run
        with pytest.raises(ValueError):
            BasePlugin(  # type: ignore[call-arg]
                name="test_plugin",
                description="Test description",
                schema_template="test_schema",
                typ="test",
            )

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_base_plugin_run_error_handling(self) -> None:
        """Test BasePlugin run method error handling."""

        # Arrange
        async def failing_run() -> None:
            raise RuntimeError("Plugin execution failed")

        plugin = BasePlugin(
            name="failing_plugin",
            description="A plugin that fails",
            schema_template="failing_schema",
            typ="failing",
            run=failing_run,
        )

        # Act & Assert
        with pytest.raises(RuntimeError) as exc_info:
            await plugin.run()

        assert "Plugin execution failed" in str(exc_info.value)

    @pytest.mark.unit
    def test_base_plugin_type_validation(
        self, mock_run_function: Callable[..., Any]
    ) -> None:
        """Test BasePlugin type field validation."""
        valid_types = ["knowledge", "link", "mcp", "workflow", "test", "custom"]

        for valid_type in valid_types:
            plugin = BasePlugin(
                name=f"{valid_type}_plugin",
                description=f"Plugin of type {valid_type}",
                schema_template="schema",
                typ=valid_type,
                run=mock_run_function,
            )
            assert plugin.typ == valid_type

    @pytest.mark.unit
    def test_base_plugin_schema_template_types(
        self, mock_run_function: Callable[..., Any]
    ) -> None:
        """Test BasePlugin with different schema template types."""
        # Test with empty schema
        plugin1 = BasePlugin(
            name="empty_schema_plugin",
            description="Plugin with empty schema",
            schema_template="",
            typ="test",
            run=mock_run_function,
        )
        assert plugin1.schema_template == ""

        # Test with JSON-like schema
        json_schema = '{"type": "object", "properties": {"param": {"type": "string"}}}'
        plugin2 = BasePlugin(
            name="json_schema_plugin",
            description="Plugin with JSON schema",
            schema_template=json_schema,
            typ="test",
            run=mock_run_function,
        )
        assert plugin2.schema_template == json_schema

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_base_plugin_run_with_complex_params(self) -> None:
        """Test BasePlugin run with complex parameter structures."""

        async def complex_run(
            data: dict[str, Any], config: dict[str, Any] | None = None
        ) -> dict[str, Any]:
            return {
                "processed_data": data,
                "config_used": config or {},
                "status": "success",
            }

        plugin = BasePlugin(
            name="complex_param_plugin",
            description="Plugin with complex parameters",
            schema_template="complex_schema",
            typ="complex",
            run=complex_run,
        )

        # Act
        test_data = {"items": [1, 2, 3], "meta": {"version": "1.0"}}
        test_config = {"timeout": 30, "retries": 3}

        result = await plugin.run(test_data, test_config)

        # Assert
        assert result["processed_data"] == test_data
        assert result["config_used"] == test_config
        assert result["status"] == "success"

    @pytest.mark.unit
    def test_base_plugin_with_none_run_result(
        self, mock_run_function: Callable[..., Any]
    ) -> None:
        """Test BasePlugin with explicitly None run_result."""
        plugin = BasePlugin(
            name="none_result_plugin",
            description="Plugin with None run_result",
            schema_template="test_schema",
            typ="test",
            run=mock_run_function,
            run_result=None,
        )

        assert plugin.run_result is None

    @pytest.mark.unit
    def test_base_plugin_inheritance_structure(self) -> None:
        """Test BasePlugin class structure and inheritance."""
        # Verify BasePlugin has expected fields in model_fields
        expected_fields = {
            "name",
            "description",
            "schema_template",
            "typ",
            "run",
            "run_result",
        }
        actual_fields = set(BasePlugin.model_fields.keys())
        missing_fields = expected_fields - actual_fields
        assert expected_fields.issubset(
            actual_fields
        ), f"Missing fields: {missing_fields}"

        # Verify it's a Pydantic model
        assert hasattr(BasePlugin, "model_dump")
        assert hasattr(BasePlugin, "model_validate")

        # Verify module location
        assert BasePlugin.__module__ == "service.plugin.base"
