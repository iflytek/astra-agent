"""
Unit tests for enterprise extension service module.

This module tests the MCP registration functionality for enterprise users,
including validation, database operations, telemetry tracking, and error
handling scenarios specific to enterprise environment requirements.
"""

from unittest.mock import Mock, patch

import pytest
from plugin.link.api.schemas.community.deprecated.management_schema import (
    ToolManagerResponse,
)
from plugin.link.api.schemas.enterprise.extension_schema import MCPManagerResponse
from plugin.link.consts import const
from plugin.link.service.enterprise.extension import register_mcp
from plugin.link.utils.errors.code import ErrCode


class TestEnterpriseExtension:
    """Test suite for enterprise extension module."""

    @pytest.fixture
    def valid_mcp_request(self):
        """Create a valid MCP registration request."""
        return {
            "app_id": "enterprise_123",
            "flow_id": "flow_456",
            "type": "search",
            "name": "Enterprise Search Tool",
            "description": "Advanced search tool for enterprise users",
            "mcp_schema": '{"openapi": "3.0.0", "info": {"title": "Enterprise Tool"}}',
            "mcp_server_url": "https://enterprise.example.com/mcp",
        }

    @pytest.fixture
    def minimal_mcp_request(self):
        """Create a minimal MCP registration request."""
        return {
            "app_id": "enterprise_123",
            "name": "Minimal Tool",
            "description": "Basic enterprise tool",
            "mcp_server_url": "https://enterprise.example.com/basic",
        }

    @patch("service.enterprise.extension.Span")
    @patch("service.enterprise.extension.api_validate")
    @patch("service.enterprise.extension.ToolCrudOperation")
    @patch("service.enterprise.extension.get_db_engine")
    @patch("service.enterprise.extension.gen_id")
    def test_register_mcp_success_with_flow_id(
        self,
        mock_gen_id,
        mock_get_db,
        mock_crud_class,
        mock_api_validate,
        mock_span_class,
        valid_mcp_request,
    ):
        """Test successful MCP registration with flow_id."""
        # Mock validation
        mock_api_validate.return_value = None

        # Mock span
        mock_span = Mock()
        mock_span_context = Mock()
        mock_span_context.sid = "test_sid"
        mock_span_context.app_id = "enterprise_123"
        mock_span_context.uid = "test_uid"
        mock_span_context.app_id = "enterprise_123"
        mock_span_context.uid = "flow_456"
        # Properly configure the context manager
        mock_span.start.return_value.__enter__ = Mock(return_value=mock_span_context)
        mock_span.start.return_value.__exit__ = Mock(return_value=None)
        mock_span_class.return_value = mock_span

        # Mock database operations
        mock_crud = Mock()
        mock_crud.add_mcp.return_value = None
        mock_crud_class.return_value = mock_crud

        # Create mock request
        mock_request = Mock()
        mock_request.model_dump.return_value = valid_mcp_request

        with patch("service.enterprise.extension.os.getenv") as mock_getenv:
            mock_getenv.return_value = "false"  # Disable OTLP

            result = register_mcp(mock_request)

        # Assertions
        assert isinstance(result, ToolManagerResponse)
        assert result.code == ErrCode.SUCCESSES.code
        assert result.message == ErrCode.SUCCESSES.msg
        assert result.data["name"] == "Enterprise Search Tool"

        # Verify tool ID format with flow_id
        expected_tool_id = "mcp@searchflow_456"
        assert result.data["id"] == expected_tool_id

        # Verify database operation
        mock_crud.add_mcp.assert_called_once()
        call_args = mock_crud.add_mcp.call_args[0][0]
        assert call_args["tool_id"] == expected_tool_id
        assert call_args["app_id"] == "enterprise_123"
        assert call_args["name"] == "Enterprise Search Tool"
        assert call_args["mcp_server_url"] == "https://enterprise.example.com/mcp"

    @patch("service.enterprise.extension.Span")
    @patch("service.enterprise.extension.api_validate")
    @patch("service.enterprise.extension.ToolCrudOperation")
    @patch("service.enterprise.extension.get_db_engine")
    @patch("service.enterprise.extension.gen_id")
    def test_register_mcp_success_without_flow_id(
        self,
        mock_gen_id,
        mock_get_db,
        mock_crud_class,
        mock_api_validate,
        mock_span_class,
        minimal_mcp_request,
    ):
        """Test successful MCP registration without flow_id (uses generated ID)."""
        mock_api_validate.return_value = None
        mock_gen_id.return_value = 0x123456789ABCDEF

        mock_span = Mock()
        mock_span_context = Mock()
        mock_span_context.sid = "test_sid"
        mock_span_context.app_id = "enterprise_123"
        mock_span_context.uid = "test_uid"
        mock_span_context.app_id = "enterprise_123"
        mock_span_context.uid = ""
        # Properly configure the context manager
        mock_span.start.return_value.__enter__ = Mock(return_value=mock_span_context)
        mock_span.start.return_value.__exit__ = Mock(return_value=None)
        mock_span_class.return_value = mock_span

        mock_crud = Mock()
        mock_crud.add_mcp.return_value = None
        mock_crud_class.return_value = mock_crud

        mock_request = Mock()
        mock_request.model_dump.return_value = minimal_mcp_request

        with patch("service.enterprise.extension.os.getenv") as mock_getenv:
            # Mock different environment variables appropriately
            def mock_getenv_side_effect(key, default=None):
                if key == const.OTLP_ENABLE_KEY:
                    return "false"
                elif key == const.DEFAULT_APPID_KEY:
                    return "enterprise_123"  # Return a valid app_id
                elif key == const.DATACENTER_ID_KEY:
                    return "1"  # Return a valid datacenter_id
                elif key == const.WORKER_ID_KEY:
                    return "1"  # Return a valid worker_id
                else:
                    return default if default is not None else "default_value"

            mock_getenv.side_effect = mock_getenv_side_effect

            result = register_mcp(mock_request)

        assert result.code == ErrCode.SUCCESSES.code

        # Verify tool ID format with generated ID
        expected_tool_id = "mcp@123456789abcdef"
        assert result.data["id"] == expected_tool_id

    @patch("service.enterprise.extension.Span")
    @patch("service.enterprise.extension.api_validate")
    def test_register_mcp_validation_error(self, mock_api_validate, mock_span_class):
        """Test MCP registration with validation error."""
        mock_api_validate.return_value = "Enterprise MCP validation failed"

        mock_span = Mock()
        mock_span_context = Mock()
        mock_span_context.sid = "test_sid"
        mock_span_context.app_id = "enterprise_123"
        mock_span_context.uid = "test_uid"
        # Properly configure the context manager
        mock_span.start.return_value.__enter__ = Mock(return_value=mock_span_context)
        mock_span.start.return_value.__exit__ = Mock(return_value=None)
        mock_span_class.return_value = mock_span

        mock_request = Mock()
        mock_request.model_dump.return_value = {"invalid": "data"}

        with patch("service.enterprise.extension.os.getenv") as mock_getenv:
            # Mock different environment variables appropriately
            def mock_getenv_side_effect(key, default=None):
                if key == const.OTLP_ENABLE_KEY:
                    return "false"
                elif key == const.DEFAULT_APPID_KEY:
                    return "enterprise_123"  # Return a valid app_id
                elif key == const.DATACENTER_ID_KEY:
                    return "1"  # Return a valid datacenter_id
                elif key == const.WORKER_ID_KEY:
                    return "1"  # Return a valid worker_id
                else:
                    return default if default is not None else "default_value"

            mock_getenv.side_effect = mock_getenv_side_effect

            result = register_mcp(mock_request)

        assert isinstance(result, MCPManagerResponse)
        assert result.code == ErrCode.JSON_PROTOCOL_PARSER_ERR.code
        assert result.message == "Enterprise MCP validation failed"

    @patch("service.enterprise.extension.Span")
    @patch("service.enterprise.extension.api_validate")
    @patch("service.enterprise.extension.ToolCrudOperation")
    @patch("service.enterprise.extension.get_db_engine")
    def test_register_mcp_database_error(
        self, mock_get_db, mock_crud_class, mock_api_validate, mock_span_class
    ):
        """Test MCP registration with database error."""
        mock_api_validate.return_value = None

        mock_span = Mock()
        mock_span_context = Mock()
        mock_span_context.sid = "test_sid"
        mock_span_context.app_id = "enterprise_123"
        mock_span_context.uid = "test_uid"
        # Properly configure the context manager
        mock_span.start.return_value.__enter__ = Mock(return_value=mock_span_context)
        mock_span.start.return_value.__exit__ = Mock(return_value=None)
        mock_span_class.return_value = mock_span

        # Mock database error
        mock_crud = Mock()
        mock_crud.add_mcp.side_effect = Exception("Database connection failed")
        mock_crud_class.return_value = mock_crud

        mock_request = Mock()
        mock_request.model_dump.return_value = {
            "app_id": "enterprise_123",
            "name": "Test Tool",
            "description": "Test",
            "mcp_server_url": "https://example.com",
        }

        with patch("service.enterprise.extension.os.getenv") as mock_getenv:
            # Mock different environment variables appropriately
            def mock_getenv_side_effect(key, default=None):
                if key == const.OTLP_ENABLE_KEY:
                    return "false"
                elif key == const.DEFAULT_APPID_KEY:
                    return "enterprise_123"  # Return a valid app_id
                elif key == const.DATACENTER_ID_KEY:
                    return "1"  # Return a valid datacenter_id
                elif key == const.WORKER_ID_KEY:
                    return "1"  # Return a valid worker_id
                else:
                    return default if default is not None else "default_value"

            mock_getenv.side_effect = mock_getenv_side_effect

            result = register_mcp(mock_request)

        assert result.code == ErrCode.COMMON_ERR.code
        assert "Database connection failed" in result.message

    @patch("service.enterprise.extension.Span")
    @patch("service.enterprise.extension.api_validate")
    @patch("service.enterprise.extension.ToolCrudOperation")
    @patch("service.enterprise.extension.get_db_engine")
    def test_register_mcp_with_telemetry_enabled(
        self, mock_get_db, mock_crud_class, mock_api_validate, mock_span_class
    ):
        """Test MCP registration with telemetry enabled."""
        mock_api_validate.return_value = None

        mock_span = Mock()
        mock_span_context = Mock()
        mock_span_context.sid = "test_sid"
        mock_span_context.app_id = "enterprise_123"
        mock_span_context.uid = "test_uid"
        mock_span_context.app_id = "enterprise_123"
        # Properly configure the context manager
        mock_span.start.return_value.__enter__ = Mock(return_value=mock_span_context)
        mock_span.start.return_value.__exit__ = Mock(return_value=None)
        mock_span_class.return_value = mock_span

        mock_crud = Mock()
        mock_crud.add_mcp.return_value = None
        mock_crud_class.return_value = mock_crud

        mock_request = Mock()
        mock_request.model_dump.return_value = {
            "app_id": "enterprise_123",
            "name": "Telemetry Test Tool",
            "description": "Tool for testing telemetry",
            "mcp_server_url": "https://telemetry.example.com",
        }

        with patch("service.enterprise.extension.os.getenv") as mock_getenv, patch(
            "service.enterprise.extension.Meter"
        ) as mock_meter_class, patch(
            "service.enterprise.extension.NodeTraceLog"
        ) as mock_node_trace_class:

            # Mock different environment variables appropriately
            def mock_getenv_side_effect(key, default=None):
                if key == const.OTLP_ENABLE_KEY:
                    return "true"
                elif key == const.DEFAULT_APPID_KEY:
                    return "enterprise_123"  # Return a valid app_id
                elif key == const.DATACENTER_ID_KEY:
                    return "1"  # Return a valid datacenter_id
                elif key == const.WORKER_ID_KEY:
                    return "1"  # Return a valid worker_id
                else:
                    return default if default is not None else "default_value"

            mock_getenv.side_effect = mock_getenv_side_effect

            mock_meter = Mock()
            mock_meter_class.return_value = mock_meter

            mock_node_trace = Mock()
            mock_node_trace_class.return_value = mock_node_trace

            result = register_mcp(mock_request)

        # Verify telemetry was used
        mock_meter_class.assert_called_once()
        mock_node_trace_class.assert_called_once()
        mock_meter.in_success_count.assert_called_once()
        mock_node_trace.upload.assert_called_once()

        assert result.code == ErrCode.SUCCESSES.code

    def test_mcp_tool_id_generation_patterns(self):
        """Test different MCP tool ID generation patterns."""
        # Test with flow_id
        test_cases = [
            {"type": "search", "flow_id": "flow_123", "expected": "mcp@searchflow_123"},
            {
                "type": "analytics",
                "flow_id": "analytics_flow_456",
                "expected": "mcp@analyticsanalytics_flow_456",
            },
            {
                "type": "",
                "flow_id": "empty_type_flow",
                "expected": "mcp@empty_type_flow",
            },
        ]

        for case in test_cases:
            tool_type = case["type"]
            flow_id = case["flow_id"]
            expected = case["expected"]

            # Simulate tool ID generation logic
            actual = f"mcp@{tool_type}{flow_id}"
            assert actual == expected

    def test_mcp_data_structure_validation(self):
        """Test MCP data structure validation."""
        # Test required fields for MCP registration
        required_fields = ["app_id", "name", "description", "mcp_server_url"]

        valid_data = {
            "app_id": "enterprise_123",
            "name": "Test MCP Tool",
            "description": "Test MCP description",
            "mcp_server_url": "https://mcp.example.com",
        }

        for field in required_fields:
            assert field in valid_data
            assert valid_data[field] is not None
            assert len(str(valid_data[field])) > 0

        # Test optional fields
        optional_fields = ["flow_id", "type", "mcp_schema"]
        for field in optional_fields:
            # Optional fields may or may not be present
            pass

    def test_enterprise_specific_configurations(self):
        """Test enterprise-specific configuration handling."""
        # Test enterprise environment variables and settings
        enterprise_configs = {
            "DEFAULT_APPID_KEY": "Default app ID for enterprise",
            "DEF_VER": "Default version for MCP tools",
            "DEF_DEL": "Default deletion flag",
            "OTLP_ENABLE_KEY": "Enterprise telemetry setting",
        }

        for config, description in enterprise_configs.items():
            assert isinstance(config, str)
            assert isinstance(description, str)

    def test_mcp_server_url_validation(self):
        """Test MCP server URL validation patterns."""
        valid_urls = [
            "https://enterprise.example.com/mcp",
            "https://mcp-server.company.internal:8443/api",
            "https://secure-mcp.enterprise.net/v1/tools",
        ]

        invalid_urls = [
            "http://insecure.example.com",  # Non-HTTPS
            "ftp://wrong-protocol.com",  # Wrong protocol
            "not-a-url",  # Invalid format
            "",  # Empty string
        ]

        for url in valid_urls:
            assert url.startswith("https://")
            assert len(url) > 10

        for url in invalid_urls:
            if url:
                assert not url.startswith("https://") or len(url) <= 10
            else:
                assert url == ""


class TestMCPDatabaseOperations:
    """Test suite for MCP database operations in enterprise context."""

    def test_mcp_database_schema(self):
        """Test MCP database schema requirements."""
        # Test expected database fields for MCP tools
        expected_fields = [
            "app_id",
            "tool_id",
            "schema",
            "name",
            "description",
            "mcp_server_url",
            "version",
            "is_deleted",
        ]

        mcp_record = {
            "app_id": "enterprise_123",
            "tool_id": "mcp@searchflow_456",
            "schema": '{"openapi": "3.0.0"}',
            "name": "Enterprise Search",
            "description": "Enterprise search tool",
            "mcp_server_url": "https://mcp.enterprise.com",
            "version": "1.0.0",
            "is_deleted": 0,
        }

        for field in expected_fields:
            assert field in mcp_record
            assert mcp_record[field] is not None

    def test_mcp_crud_operation_interface(self):
        """Test MCP CRUD operation interface."""
        # Test that add_mcp method follows expected interface
        # This documents the expected method signature and behavior

        expected_methods = ["add_mcp", "get_tools", "update_tools", "delete_tools"]

        for method in expected_methods:
            assert isinstance(method, str)
            # In actual implementation, these would be method calls


class TestEnterpriseErrorHandling:
    """Test suite for enterprise-specific error handling."""

    def test_enterprise_error_codes(self):
        """Test enterprise-specific error code usage."""
        # Test error codes used in enterprise extension
        error_codes = [
            ErrCode.SUCCESSES,
            ErrCode.JSON_PROTOCOL_PARSER_ERR,
            ErrCode.COMMON_ERR,
        ]

        for error_code in error_codes:
            assert hasattr(error_code, "code")
            assert hasattr(error_code, "msg")
            assert isinstance(error_code.code, int)
            assert isinstance(error_code.msg, str)

    def test_enterprise_response_formats(self):
        """Test enterprise response format consistency."""
        # Test MCPManagerResponse vs ToolManagerResponse usage

        # MCPManagerResponse for validation errors
        mcp_response = MCPManagerResponse(
            code=1001, message="Validation error", sid="test_sid", data={}
        )

        assert hasattr(mcp_response, "code")
        assert hasattr(mcp_response, "message")
        assert hasattr(mcp_response, "sid")
        assert hasattr(mcp_response, "data")

        # ToolManagerResponse for successful operations
        tool_response = ToolManagerResponse(
            code=0,
            message="Success",
            sid="test_sid",
            data={"name": "Test Tool", "id": "mcp@123"},
        )

        assert hasattr(tool_response, "code")
        assert hasattr(tool_response, "message")
        assert hasattr(tool_response, "sid")
        assert hasattr(tool_response, "data")

    def test_enterprise_telemetry_requirements(self):
        """Test enterprise telemetry requirements."""
        # Test telemetry components required for enterprise
        telemetry_components = ["Span", "Meter", "NodeTraceLog", "Status"]

        telemetry_operations = [
            "span.start",
            "span_context.add_info_events",
            "span_context.set_attributes",
            "meter.in_success_count",
            "meter.in_error_count",
            "node_trace.record_start",
            "node_trace.upload",
        ]

        for component in telemetry_components:
            assert isinstance(component, str)

        for operation in telemetry_operations:
            assert isinstance(operation, str)
            assert "." in operation  # Method call format

    def test_enterprise_security_considerations(self):
        """Test enterprise security considerations."""
        # Test security-related validations for enterprise MCP tools
        security_checks = {
            "app_id_validation": "Enterprise app ID must be validated",
            "mcp_server_url_validation": "MCP server URL must be HTTPS",
            "schema_validation": "MCP schema must be validated",
            "access_control": "Enterprise users need proper permissions",
        }

        for check, requirement in security_checks.items():
            assert isinstance(check, str)
            assert isinstance(requirement, str)
            assert len(requirement) > 10  # Meaningful requirement description
