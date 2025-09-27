"""
Detailed unit tests for ToolCrudOperation functions.

This module provides comprehensive function-level testing for all CRUD operations
including edge cases, error scenarios, and boundary conditions for each method.
"""

from unittest.mock import Mock, patch

import pytest
from plugin.link.consts import const
from plugin.link.domain.entity.tool_schema import Tools
from plugin.link.exceptions.sparklink_exceptions import ToolNotExistsException
from plugin.link.infra.tool_crud.process import ToolCrudOperation
from plugin.link.utils.errors.code import ErrCode
from sqlalchemy.exc import IntegrityError, NoResultFound


class TestToolCrudOperationInit:
    """Test suite for ToolCrudOperation initialization."""

    def test_init_with_valid_engine(self):
        """Test initialization with valid database engine."""
        mock_engine = Mock()
        crud_op = ToolCrudOperation(mock_engine)

        assert crud_op.engine == mock_engine
        assert hasattr(crud_op, "engine")

    def test_init_with_none_engine(self):
        """Test initialization with None engine."""
        crud_op = ToolCrudOperation(None)

        assert crud_op.engine is None


class TestAddToolsFunction:
    """Detailed tests for add_tools function."""

    @pytest.fixture
    def mock_engine(self):
        """Mock database engine."""
        return Mock()

    @pytest.fixture
    def crud_operation(self, mock_engine):
        """ToolCrudOperation instance."""
        return ToolCrudOperation(mock_engine)

    @pytest.fixture
    def sample_tool_info(self):
        """Sample tool information for testing."""
        return [
            {
                "app_id": "test_app_123",
                "tool_id": "tool@12345",
                "name": "Test Tool 1",
                "description": "First test tool",
                "schema": '{"openapi": "3.0.0"}',
                "version": "1.0.0",
                "is_deleted": 0,
            },
            {
                "app_id": "test_app_123",
                "tool_id": "tool@67890",
                "name": "Test Tool 2",
                "description": "Second test tool",
                "schema": '{"openapi": "3.0.1"}',
                "version": "1.1.0",
                "is_deleted": 0,
            },
        ]

    @patch("infra.tool_crud.process.session_getter")
    def test_add_tools_success_single_tool(self, mock_session_getter, crud_operation):
        """Test adding a single tool successfully."""
        # Mock session
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        tool_info = [
            {
                "app_id": "app123",
                "tool_id": "tool@123",
                "name": "Single Tool",
                "description": "A single test tool",
                "schema": '{"test": "schema"}',
            }
        ]

        crud_operation.add_tools(tool_info)

        # Verify session.add was called once
        assert mock_session.add.call_count == 1
        assert mock_session.commit.call_count == 1

        # Verify Tools object was created with correct parameters
        added_tool = mock_session.add.call_args[0][0]
        assert isinstance(added_tool, Tools)
        assert added_tool.app_id == "app123"
        assert added_tool.tool_id == "tool@123"
        assert added_tool.name == "Single Tool"
        assert added_tool.version == const.DEF_VER  # Default version
        assert added_tool.is_deleted == const.DEF_DEL  # Default deleted flag

    @patch("infra.tool_crud.process.session_getter")
    def test_add_tools_success_multiple_tools(
        self, mock_session_getter, crud_operation, sample_tool_info
    ):
        """Test adding multiple tools successfully."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        crud_operation.add_tools(sample_tool_info)

        # Verify each tool was added and committed
        assert mock_session.add.call_count == 2
        assert mock_session.commit.call_count == 2

        # Verify tool parameters
        first_call_tool = mock_session.add.call_args_list[0][0][0]
        assert first_call_tool.name == "Test Tool 1"
        assert first_call_tool.version == "1.0.0"

        second_call_tool = mock_session.add.call_args_list[1][0][0]
        assert second_call_tool.name == "Test Tool 2"
        assert second_call_tool.version == "1.1.0"

    @patch("infra.tool_crud.process.session_getter")
    def test_add_tools_with_missing_fields(self, mock_session_getter, crud_operation):
        """Test adding tools with missing optional fields."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        # Tool info with missing version and is_deleted
        tool_info = [
            {
                "app_id": "app123",
                "tool_id": "tool@123",
                "name": "Minimal Tool",
                "description": "Tool with missing fields",
                "schema": '{"minimal": "schema"}',
                # Missing version and is_deleted
            }
        ]

        crud_operation.add_tools(tool_info)

        # Verify defaults were used
        added_tool = mock_session.add.call_args[0][0]
        assert added_tool.version == const.DEF_VER
        assert added_tool.is_deleted == const.DEF_DEL

    @patch("infra.tool_crud.process.session_getter")
    def test_add_tools_with_none_values(self, mock_session_getter, crud_operation):
        """Test adding tools with None values."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        tool_info = [
            {
                "app_id": None,
                "tool_id": None,
                "name": None,
                "description": None,
                "schema": None,
            }
        ]

        crud_operation.add_tools(tool_info)

        # Verify None values are handled
        added_tool = mock_session.add.call_args[0][0]
        assert added_tool.app_id is None
        assert added_tool.tool_id is None
        assert added_tool.name is None

    @patch("infra.tool_crud.process.session_getter")
    def test_add_tools_empty_list(self, mock_session_getter, crud_operation):
        """Test adding empty tool list."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        crud_operation.add_tools([])

        # Verify no operations were performed
        mock_session.add.assert_not_called()
        mock_session.commit.assert_not_called()

    @patch("infra.tool_crud.process.session_getter")
    def test_add_tools_session_exception(self, mock_session_getter, crud_operation):
        """Test handling of session exceptions during add_tools."""
        mock_session = Mock()
        mock_session.commit.side_effect = Exception("Database error")
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        tool_info = [{"app_id": "test", "tool_id": "tool@test"}]

        with pytest.raises(Exception, match="Database error"):
            crud_operation.add_tools(tool_info)


class TestAddMcpFunction:
    """Detailed tests for add_mcp function."""

    @pytest.fixture
    def mock_engine(self):
        return Mock()

    @pytest.fixture
    def crud_operation(self, mock_engine):
        return ToolCrudOperation(mock_engine)

    @pytest.fixture
    def sample_mcp_info(self):
        return {
            "app_id": "mcp_app_123",
            "tool_id": "mcp@search123",
            "name": "MCP Search Tool",
            "description": "MCP tool for searching",
            "schema": '{"mcp": "schema"}',
            "mcp_server_url": "https://mcp.example.com",
            "version": "2.0.0",
            "is_deleted": 0,
        }

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    def test_add_mcp_new_tool_creation(
        self, mock_select, mock_session_getter, crud_operation, sample_mcp_info
    ):
        """Test creating new MCP tool when none exists."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        # Mock query to return None (no existing tool)
        mock_query = Mock()
        mock_select.return_value.where.return_value.order_by.return_value = mock_query
        mock_session.exec.return_value.first.return_value = None

        crud_operation.add_mcp(sample_mcp_info)

        # Verify new tool was created and added
        mock_session.add.assert_called_once()
        mock_session.commit.assert_called_once()

        # Verify Tools object creation
        added_tool = mock_session.add.call_args[0][0]
        assert isinstance(added_tool, Tools)
        assert added_tool.tool_id == "mcp@search123"
        assert added_tool.mcp_server_url == "https://mcp.example.com"

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    def test_add_mcp_update_existing_tool(
        self, mock_select, mock_session_getter, crud_operation, sample_mcp_info
    ):
        """Test updating existing MCP tool."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        # Mock existing tool
        existing_tool = Mock()
        existing_tool.app_id = "old_app"
        existing_tool.name = "Old Name"

        mock_query = Mock()
        mock_select.return_value.where.return_value.order_by.return_value = mock_query
        mock_session.exec.return_value.first.return_value = existing_tool

        crud_operation.add_mcp(sample_mcp_info)

        # Verify existing tool was updated
        assert existing_tool.app_id == "mcp_app_123"
        assert existing_tool.name == "MCP Search Tool"
        assert existing_tool.mcp_server_url == "https://mcp.example.com"

        mock_session.add.assert_called_once_with(existing_tool)
        mock_session.commit.assert_called_once()

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    def test_add_mcp_with_defaults(
        self, mock_select, mock_session_getter, crud_operation
    ):
        """Test add_mcp with missing optional fields using defaults."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        mock_session.exec.return_value.first.return_value = None

        minimal_mcp_info = {
            "app_id": "app123",
            "tool_id": "mcp@minimal",
            "name": "Minimal MCP",
            "description": "Basic MCP tool",
            # Missing schema, mcp_server_url, version, is_deleted
        }

        crud_operation.add_mcp(minimal_mcp_info)

        added_tool = mock_session.add.call_args[0][0]
        assert added_tool.mcp_server_url == ""  # Default empty string
        assert added_tool.version == const.DEF_VER
        assert added_tool.is_deleted == const.DEF_DEL

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    def test_add_mcp_no_result_found_exception(
        self, mock_select, mock_session_getter, crud_operation, sample_mcp_info
    ):
        """Test handling NoResultFound exception in add_mcp."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        # Mock NoResultFound exception
        mock_session.exec.return_value.first.side_effect = NoResultFound()

        crud_operation.add_mcp(sample_mcp_info)

        # Should create new tool despite exception
        mock_session.add.assert_called_once()
        added_tool = mock_session.add.call_args[0][0]
        assert isinstance(added_tool, Tools)


class TestUpdateToolsFunction:
    """Detailed tests for update_tools function."""

    @pytest.fixture
    def crud_operation(self):
        return ToolCrudOperation(Mock())

    @pytest.fixture
    def sample_update_info(self):
        return [
            {
                "tool_id": "tool@update123",
                "name": "Updated Tool Name",
                "description": "Updated description",
                "open_api_schema": '{"updated": "schema"}',
                "version": "1.0.0",
                "is_deleted": 0,
            }
        ]

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    def test_update_tools_success(
        self, mock_select, mock_session_getter, crud_operation, sample_update_info
    ):
        """Test successful tool update."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        # Mock existing tool
        existing_tool = Mock()
        existing_tool.name = "Old Name"
        existing_tool.description = "Old Description"
        existing_tool.open_api_schema = "Old Schema"

        mock_session.exec.return_value.first.return_value = existing_tool

        crud_operation.update_tools(sample_update_info)

        # Verify updates were applied
        assert existing_tool.name == "Updated Tool Name"
        assert existing_tool.description == "Updated description"
        assert existing_tool.open_api_schema == '{"updated": "schema"}'

        mock_session.add.assert_called_once_with(existing_tool)
        mock_session.commit.assert_called_once()

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    def test_update_tools_partial_update(
        self, mock_select, mock_session_getter, crud_operation
    ):
        """Test partial tool update (only some fields provided)."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        existing_tool = Mock()
        existing_tool.name = "Original Name"
        existing_tool.description = "Original Description"
        existing_tool.open_api_schema = "Original Schema"

        mock_session.exec.return_value.first.return_value = existing_tool

        # Update only name
        partial_update_info = [
            {
                "tool_id": "tool@partial",
                "name": "New Name Only",
                # No description or schema
            }
        ]

        crud_operation.update_tools(partial_update_info)

        # Verify only name was updated
        assert existing_tool.name == "New Name Only"
        assert existing_tool.description == "Original Description"  # Unchanged
        assert existing_tool.open_api_schema == "Original Schema"  # Unchanged

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    def test_update_tools_not_exists_exception(
        self, mock_select, mock_session_getter, crud_operation
    ):
        """Test ToolNotExistsException when tool doesn't exist."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        # Mock no tool found
        mock_session.exec.return_value.first.return_value = None

        update_info = [{"tool_id": "nonexistent@tool"}]

        with pytest.raises(ToolNotExistsException) as exc_info:
            crud_operation.update_tools(update_info)

        assert exc_info.value.code == ErrCode.TOOL_NOT_EXIST_ERR.code
        assert "tools don't exist!" in str(exc_info.value)

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    def test_update_tools_empty_string_fields(
        self, mock_select, mock_session_getter, crud_operation
    ):
        """Test update_tools with empty string fields (should not update)."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        existing_tool = Mock()
        existing_tool.name = "Original Name"
        existing_tool.description = "Original Description"

        mock_session.exec.return_value.first.return_value = existing_tool

        # Update with empty strings
        update_info = [
            {
                "tool_id": "tool@empty",
                "name": "",  # Empty string
                "description": "",  # Empty string
                "open_api_schema": "",  # Empty string
            }
        ]

        crud_operation.update_tools(update_info)

        # Verify no updates were applied (empty strings are falsy)
        assert existing_tool.name == "Original Name"  # Unchanged
        assert existing_tool.description == "Original Description"  # Unchanged

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    def test_update_tools_version_tuple_handling(
        self, mock_select, mock_session_getter, crud_operation
    ):
        """Test update_tools with version as tuple (edge case)."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        existing_tool = Mock()
        mock_session.exec.return_value.first.return_value = existing_tool

        # Version as tuple (edge case from line 96)
        update_info = [
            {
                "tool_id": "tool@tuple",
                "version": ("1.0.0",),  # Tuple version
                "name": "Tuple Version Tool",
            }
        ]

        crud_operation.update_tools(update_info)

        # Should handle tuple version correctly
        mock_session.add.assert_called_once()


class TestAddToolVersionFunction:
    """Detailed tests for add_tool_version function."""

    @pytest.fixture
    def crud_operation(self):
        return ToolCrudOperation(Mock())

    @patch("infra.tool_crud.process.session_getter")
    def test_add_tool_version_success(self, mock_session_getter, crud_operation):
        """Test successful tool version addition."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        version_info = [
            {
                "app_id": "app123",
                "tool_id": "tool@version",
                "name": "Version Tool",
                "description": "Tool with version",
                "open_api_schema": '{"version": "schema"}',
                "version": "2.0.0",
                "is_deleted": 0,
            }
        ]

        crud_operation.add_tool_version(version_info)

        mock_session.add.assert_called_once()
        mock_session.commit.assert_called_once()

        # Verify Tools object creation
        added_tool = mock_session.add.call_args[0][0]
        assert isinstance(added_tool, Tools)
        assert added_tool.version == "2.0.0"

    @patch("infra.tool_crud.process.session_getter")
    def test_add_tool_version_integrity_error(
        self, mock_session_getter, crud_operation
    ):
        """Test handling IntegrityError (version already exists)."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        # Mock IntegrityError on commit
        mock_session.commit.side_effect = IntegrityError("", "", "")

        version_info = [{"tool_id": "tool@duplicate", "version": "1.0.0"}]

        with pytest.raises(Exception, match="Version already exists!"):
            crud_operation.add_tool_version(version_info)

        mock_session.rollback.assert_called_once()

    @patch("infra.tool_crud.process.session_getter")
    def test_add_tool_version_multiple_tools(self, mock_session_getter, crud_operation):
        """Test adding multiple tool versions."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        version_info = [
            {"tool_id": "tool@v1", "version": "1.0.0"},
            {"tool_id": "tool@v2", "version": "2.0.0"},
        ]

        crud_operation.add_tool_version(version_info)

        assert mock_session.add.call_count == 2
        assert mock_session.commit.call_count == 2


class TestDeleteToolsFunction:
    """Detailed tests for delete_tools function."""

    @pytest.fixture
    def crud_operation(self):
        return ToolCrudOperation(Mock())

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    @patch("infra.tool_crud.process.datetime")
    def test_delete_tools_with_version(
        self, mock_datetime, mock_select, mock_session_getter, crud_operation
    ):
        """Test deleting tools with specific version."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        # Mock datetime
        mock_now = Mock()
        mock_now.timestamp.return_value = 1234567890
        mock_datetime.now.return_value = mock_now

        # Mock found tools
        tool1 = Mock()
        tool2 = Mock()
        mock_session.exec.return_value.all.return_value = [tool1, tool2]

        delete_info = [{"tool_id": "tool@delete", "version": "1.0.0", "is_deleted": 0}]

        crud_operation.delete_tools(delete_info)

        # Verify is_deleted timestamp was set
        assert tool1.is_deleted == 1234567890
        assert tool2.is_deleted == 1234567890
        mock_session.commit.assert_called_once()

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    def test_delete_tools_without_version(
        self, mock_select, mock_session_getter, crud_operation
    ):
        """Test deleting tools without specific version (deletes all versions)."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        mock_session.exec.return_value.all.return_value = [Mock()]

        delete_info = [
            {
                "tool_id": "tool@delete_all",
                "version": "",  # Empty version
                "is_deleted": 0,
            }
        ]

        crud_operation.delete_tools(delete_info)

        # Should use query without version filter
        mock_session.commit.assert_called_once()

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    def test_delete_tools_version_tuple_handling(
        self, mock_select, mock_session_getter, crud_operation
    ):
        """Test delete_tools with version as tuple."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        mock_session.exec.return_value.all.return_value = []

        delete_info = [
            {
                "tool_id": "tool@tuple_version",
                "version": ("1.0.0",),  # Tuple version
                "is_deleted": 0,
            }
        ]

        crud_operation.delete_tools(delete_info)

        # Should handle tuple version correctly
        mock_session.exec.assert_called_once()

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    def test_delete_tools_no_results_found(
        self, mock_select, mock_session_getter, crud_operation
    ):
        """Test delete_tools when no tools are found."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        # No tools found
        mock_session.exec.return_value.all.return_value = []

        delete_info = [{"tool_id": "tool@notfound", "version": "1.0.0"}]

        crud_operation.delete_tools(delete_info)

        # Should not commit if no tools found
        mock_session.commit.assert_not_called()


class TestGetToolsFunction:
    """Detailed tests for get_tools function."""

    @pytest.fixture
    def crud_operation(self):
        return ToolCrudOperation(Mock())

    @pytest.fixture
    def mock_span(self):
        span = Mock()
        span_context = Mock()
        span.start.return_value.__enter__ = Mock(return_value=span_context)
        span.start.return_value.__exit__ = Mock(return_value=None)
        return span

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    def test_get_tools_success(
        self, mock_select, mock_session_getter, crud_operation, mock_span
    ):
        """Test successful tool retrieval."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        # Mock found tools
        tool1 = Mock()
        tool1.tool_id = "tool@123"
        tool2 = Mock()
        tool2.tool_id = "tool@456"

        mock_session.exec.return_value.first.side_effect = [tool1, tool2]

        tool_info = [
            {"tool_id": "tool@123", "version": "1.0.0"},
            {"tool_id": "tool@456", "version": "1.0.0"},
        ]

        result = crud_operation.get_tools(tool_info, mock_span)

        assert len(result) == 2
        assert result[0] == tool1
        assert result[1] == tool2

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    def test_get_tools_not_found_exception(
        self, mock_select, mock_session_getter, crud_operation, mock_span
    ):
        """Test ToolNotExistsException when tool not found."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        # No tool found
        mock_session.exec.return_value.first.return_value = None

        tool_info = [{"tool_id": "tool@notfound", "version": "1.0.0"}]

        with pytest.raises(ToolNotExistsException) as exc_info:
            crud_operation.get_tools(tool_info, mock_span)

        assert exc_info.value.code == ErrCode.TOOL_NOT_EXIST_ERR.code
        assert "tool@notfound 1.0.0不存在" in str(exc_info.value)

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    def test_get_tools_version_tuple_handling(
        self, mock_select, mock_session_getter, crud_operation, mock_span
    ):
        """Test get_tools with version as tuple."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        mock_tool = Mock()
        mock_session.exec.return_value.first.return_value = mock_tool

        tool_info = [{"tool_id": "tool@tuple", "version": ("1.0.0",)}]  # Tuple version

        result = crud_operation.get_tools(tool_info, mock_span)

        assert len(result) == 1
        assert result[0] == mock_tool

    @patch("infra.tool_crud.process.session_getter")
    @patch("infra.tool_crud.process.select")
    def test_get_tools_default_values(
        self, mock_select, mock_session_getter, crud_operation, mock_span
    ):
        """Test get_tools with default version and is_deleted values."""
        mock_session = Mock()
        mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
        mock_session_getter.return_value.__exit__ = Mock(return_value=None)

        mock_tool = Mock()
        mock_session.exec.return_value.first.return_value = mock_tool

        # Tool info with missing version and is_deleted
        tool_info = [{"tool_id": "tool@defaults"}]

        result = crud_operation.get_tools(tool_info, mock_span)

        assert len(result) == 1
        # Should use default values in query

    def test_get_tools_span_context_usage(self, crud_operation):
        """Test that span context is properly used in get_tools."""
        mock_span = Mock()
        mock_span_context = Mock()
        mock_span.start.return_value.__enter__ = Mock(return_value=mock_span_context)
        mock_span.start.return_value.__exit__ = Mock(return_value=None)

        with patch("infra.tool_crud.process.session_getter") as mock_session_getter:
            mock_session = Mock()
            mock_session_getter.return_value.__enter__ = Mock(return_value=mock_session)
            mock_session_getter.return_value.__exit__ = Mock(return_value=None)

            mock_session.exec.return_value.first.return_value = Mock()

            tool_info = [{"tool_id": "tool@span"}]

            crud_operation.get_tools(tool_info, mock_span)

            # Verify span context methods were called
            assert (
                mock_span_context.add_info_event.call_count >= 2
            )  # Called at start and end
