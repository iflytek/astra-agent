"""Unit tests for api.v1.base_api module."""

import dataclasses
import threading
import time
from unittest.mock import Mock

import pytest

from api.v1.base_api import RunContext

# Use unified common package import module
from common_imports import BaseExc, NodeTrace, Span


class TestRunContext:  # pylint: disable=too-many-public-methods
    """Test cases for RunContext dataclass."""

    @pytest.fixture
    def mock_error(self) -> Mock:
        """Create mock error object."""
        mock_error = Mock(spec=BaseExc)
        mock_error.message = "test error"
        mock_error.error_code = "TEST_001"
        return mock_error

    @pytest.fixture
    def mock_span(self) -> Mock:
        """Create mock span object."""
        return Mock(spec=Span)

    @pytest.fixture
    def mock_node_trace(self) -> Mock:
        """Create mock node trace object."""
        return Mock(spec=NodeTrace)

    @pytest.fixture
    def mock_meter(self) -> Mock:
        """Create mock meter object."""
        return Mock()

    @pytest.fixture
    def sample_run_context(
        self,
        mock_error: Mock,
        mock_span: Mock,
        mock_node_trace: Mock,
        mock_meter: Mock,
    ) -> RunContext:
        """Create sample RunContext for testing."""
        return RunContext(
            error=mock_error,
            error_log="test error日志",
            chunk_logs=["log1", "log2"],
            span=mock_span,
            node_trace=mock_node_trace,
            meter=mock_meter,
        )

    @pytest.mark.unit
    def test_run_context_creation(
        self,
        mock_error: Mock,
        mock_span: Mock,
        mock_node_trace: Mock,
        mock_meter: Mock,
    ) -> None:
        """Test RunContext creation with valid parameters."""
        # Act
        context = RunContext(
            error=mock_error,
            error_log="test error日志",
            chunk_logs=["log1", "log2"],
            span=mock_span,
            node_trace=mock_node_trace,
            meter=mock_meter,
        )

        # Assert
        assert context.error == mock_error
        assert context.error_log == "test error日志"
        assert len(context.chunk_logs) == 2
        assert context.chunk_logs[0] == "log1"
        assert context.chunk_logs[1] == "log2"
        assert context.span == mock_span
        assert context.node_trace == mock_node_trace
        assert context.meter == mock_meter

    @pytest.mark.unit
    def test_run_context_empty_chunk_logs(
        self,
        mock_error: Mock,
        mock_span: Mock,
        mock_node_trace: Mock,
        mock_meter: Mock,
    ) -> None:
        """Test RunContext with empty chunk_logs."""
        # Act
        context = RunContext(
            error=mock_error,
            error_log="empty log test",
            chunk_logs=[],
            span=mock_span,
            node_trace=mock_node_trace,
            meter=mock_meter,
        )

        # Assert
        assert context.error == mock_error
        assert context.error_log == "empty log test"
        assert len(context.chunk_logs) == 0
        assert not context.chunk_logs

    @pytest.mark.unit
    def test_run_context_unicode_content(
        self, mock_span: Mock, mock_node_trace: Mock, mock_meter: Mock
    ) -> None:
        """Test RunContext with Unicode content."""
        # Arrange
        unicode_error = Mock(spec=BaseExc)
        unicode_error.message = "Chinese error message🚨"

        # Act
        context = RunContext(
            error=unicode_error,
            error_log="中文error log：特殊字符①②③",
            chunk_logs=["中文log1🔍", "special char log②", "Unicode test③"],
            span=mock_span,
            node_trace=mock_node_trace,
            meter=mock_meter,
        )

        # Assert
        assert getattr(context.error, "message", None) == "Chinese error message🚨"
        assert "中文error log" in context.error_log
        assert "特殊字符①②③" in context.error_log
        assert len(context.chunk_logs) == 3
        assert "🔍" in context.chunk_logs[0]
        assert "special char log②" == context.chunk_logs[1]
        assert "Unicode test③" == context.chunk_logs[2]

    @pytest.mark.unit
    def test_run_context_large_chunk_logs(
        self,
        mock_error: Mock,
        mock_span: Mock,
        mock_node_trace: Mock,
        mock_meter: Mock,
    ) -> None:
        """Test RunContext with large number of chunk_logs."""
        # Arrange
        large_chunk_logs = [f"log entry{i}" for i in range(1000)]

        # Act
        context = RunContext(
            error=mock_error,
            error_log="large log test",
            chunk_logs=large_chunk_logs,
            span=mock_span,
            node_trace=mock_node_trace,
            meter=mock_meter,
        )

        # Assert
        assert len(context.chunk_logs) == 1000
        assert context.chunk_logs[0] == "log entry0"
        assert context.chunk_logs[999] == "log entry999"
        assert context.error_log == "large log test"

    @pytest.mark.unit
    def test_run_context_none_error_handling(
        self, mock_span: Mock, mock_node_trace: Mock, mock_meter: Mock
    ) -> None:
        """Test RunContext with None error (edge case)."""
        # Act
        context = RunContext(
            error=None,  # Testing edge case with None error
            error_log="None error test",
            chunk_logs=["test log"],
            span=mock_span,
            node_trace=mock_node_trace,
            meter=mock_meter,
        )

        # Assert
        assert context.error is None
        assert context.error_log == "None error test"
        assert context.chunk_logs == ["test log"]

    @pytest.mark.unit
    def test_run_context_serialization(self, sample_run_context: RunContext) -> None:
        """Test RunContext serialization-related operations."""
        # Act & Assert
        assert hasattr(sample_run_context, "error")
        assert hasattr(sample_run_context, "error_log")
        assert hasattr(sample_run_context, "chunk_logs")
        assert hasattr(sample_run_context, "span")
        assert hasattr(sample_run_context, "node_trace")
        assert hasattr(sample_run_context, "meter")

        # Test string representation
        context_str = str(sample_run_context)
        assert "RunContext" in context_str

    @pytest.mark.unit
    def test_run_context_modification(
        self,
        mock_error: Mock,
        mock_span: Mock,
        mock_node_trace: Mock,
        mock_meter: Mock,
    ) -> None:
        """Test RunContext modification operations."""
        # Arrange
        context = RunContext(
            error=mock_error,
            error_log="modify test",
            chunk_logs=["original log"],
            span=mock_span,
            node_trace=mock_node_trace,
            meter=mock_meter,
        )

        # Act
        context.chunk_logs.append("new log")
        context.error_log = "修改后的error log"

        # Assert
        assert len(context.chunk_logs) == 2
        assert context.chunk_logs[1] == "new log"
        assert context.error_log == "修改后的error log"

    @pytest.mark.unit
    def test_run_context_memory_efficiency(
        self,
        mock_error: Mock,
        mock_span: Mock,
        mock_node_trace: Mock,
        mock_meter: Mock,
    ) -> None:
        """Test RunContext memory efficiency with multiple instances."""
        # Act
        contexts = []
        for i in range(100):
            context = RunContext(
                error=mock_error,
                error_log=f"memory test{i}",
                chunk_logs=[f"日志{i}"],
                span=mock_span,
                node_trace=mock_node_trace,
                meter=mock_meter,
            )
            contexts.append(context)

        # Assert
        assert len(contexts) == 100
        for i, context in enumerate(contexts):
            assert f"memory test{i}" in context.error_log
            assert context.chunk_logs[0] == f"日志{i}"

    @pytest.mark.unit
    def test_run_context_type_validation(self, sample_run_context: RunContext) -> None:
        """Test RunContext type validation."""
        # Assert
        assert isinstance(sample_run_context.error_log, str)
        assert isinstance(sample_run_context.chunk_logs, list)

        # Verify chunk_logs element types
        for log in sample_run_context.chunk_logs:
            assert isinstance(log, str)

        # Verify other attributes
        assert sample_run_context.error is not None
        assert sample_run_context.span is not None
        assert sample_run_context.node_trace is not None
        assert sample_run_context.meter is not None

    @pytest.mark.unit
    def test_run_context_edge_cases(
        self,
        mock_error: Mock,
        mock_span: Mock,
        mock_node_trace: Mock,
        mock_meter: Mock,
    ) -> None:
        """Test RunContext edge cases."""
        # Arrange
        very_long_log = "很长的error log " * 10000

        # Act
        context = RunContext(
            error=mock_error,
            error_log=very_long_log,
            chunk_logs=["boundary test"],
            span=mock_span,
            node_trace=mock_node_trace,
            meter=mock_meter,
        )

        # Assert
        assert len(context.error_log) == len(very_long_log)
        assert context.chunk_logs == ["boundary test"]

        # Test with empty strings
        empty_context = RunContext(
            error=mock_error,
            error_log="",
            chunk_logs=["", "non-empty log", ""],
            span=mock_span,
            node_trace=mock_node_trace,
            meter=mock_meter,
        )

        assert empty_context.error_log == ""
        assert len(empty_context.chunk_logs) == 3
        assert empty_context.chunk_logs[1] == "non-empty log"

    @pytest.mark.unit
    def test_run_context_concurrent_access(
        self,
        mock_error: Mock,
        mock_span: Mock,
        mock_node_trace: Mock,
        mock_meter: Mock,
    ) -> None:
        """Test RunContext concurrent access scenarios."""
        # Arrange
        context = RunContext(
            error=mock_error,
            error_log="concurrent test",
            chunk_logs=[],
            span=mock_span,
            node_trace=mock_node_trace,
            meter=mock_meter,
        )

        def add_log(thread_id: int) -> None:
            """Add logs from a specific thread."""
            for i in range(10):
                context.chunk_logs.append(f"thread{thread_id}-日志{i}")
                time.sleep(0.001)  # Simulate processing time

        # Act
        threads = []
        for thread_id in range(3):
            thread = threading.Thread(target=add_log, args=(thread_id,))
            threads.append(thread)
            thread.start()

        # Wait for all threads to complete
        for thread in threads:
            thread.join()

        # Assert
        assert len(context.chunk_logs) == 30  # 3 threads × 10 logs each

        # Verify all logs were added
        thread_counts = {0: 0, 1: 0, 2: 0}
        for log in context.chunk_logs:
            for thread_id in range(3):
                if f"thread{thread_id}" in log:
                    thread_counts[thread_id] += 1

        # Each thread should have added exactly 10 logs
        for thread_id, count in thread_counts.items():
            assert count == 10, f"Thread {thread_id} added {count} logs, expected 10"

    @pytest.mark.unit
    def test_run_context_dataclass_features(
        self, sample_run_context: RunContext
    ) -> None:
        """Test RunContext dataclass-specific features."""
        # Test that it's a proper dataclass
        assert hasattr(sample_run_context, "__dataclass_fields__")

        # Test field access
        fields = sample_run_context.__dataclass_fields__
        expected_fields = {
            "error",
            "error_log",
            "chunk_logs",
            "span",
            "node_trace",
            "meter",
        }
        assert set(fields.keys()) == expected_fields

        # Test repr functionality
        repr_str = repr(sample_run_context)
        assert "RunContext" in repr_str

    @pytest.mark.unit
    def test_run_context_equality(
        self,
        mock_error: Mock,
        mock_span: Mock,
        mock_node_trace: Mock,
        mock_meter: Mock,
    ) -> None:
        """Test RunContext equality comparison."""
        # Arrange
        context1 = RunContext(
            error=mock_error,
            error_log="equality test",
            chunk_logs=["log1", "log2"],
            span=mock_span,
            node_trace=mock_node_trace,
            meter=mock_meter,
        )

        context2 = RunContext(
            error=mock_error,
            error_log="equality test",
            chunk_logs=["log1", "log2"],
            span=mock_span,
            node_trace=mock_node_trace,
            meter=mock_meter,
        )

        different_context = RunContext(
            error=mock_error,
            error_log="different test",
            chunk_logs=["different log"],
            span=mock_span,
            node_trace=mock_node_trace,
            meter=mock_meter,
        )

        # Act & Assert
        assert context1 == context2
        assert context1 != different_context

    @pytest.mark.unit
    def test_run_context_with_complex_objects(
        self, mock_span: Mock, mock_node_trace: Mock, mock_meter: Mock
    ) -> None:
        """Test RunContext with complex error objects."""
        # Arrange
        complex_error = Mock(spec=BaseExc)
        complex_error.message = "complex error object"
        complex_error.error_code = "COMPLEX_001"
        complex_error.details = {"nested": {"data": "complex nested data"}}
        complex_error.timestamp = "2024-01-01T00:00:00Z"

        # Act
        context = RunContext(
            error=complex_error,
            error_log="complex object test",
            chunk_logs=["复杂test log"],
            span=mock_span,
            node_trace=mock_node_trace,
            meter=mock_meter,
        )

        # Assert
        assert getattr(context.error, "message", None) == "complex error object"
        assert getattr(context.error, "error_code", None) == "COMPLEX_001"
        error_details = getattr(context.error, "details", {})
        assert error_details.get("nested", {}).get("data") == "complex nested data"
        assert getattr(context.error, "timestamp", None) == "2024-01-01T00:00:00Z"

    @pytest.mark.unit
    def test_run_context_immutable_like_behavior(
        self, sample_run_context: RunContext
    ) -> None:
        """Test RunContext behavior for immutable-like operations."""
        # Store original values
        original_error = sample_run_context.error
        original_span = sample_run_context.span

        # Test that we can still modify mutable fields
        original_log_count = len(sample_run_context.chunk_logs)
        sample_run_context.chunk_logs.append("new log")

        # Assert mutable field was modified
        assert len(sample_run_context.chunk_logs) == original_log_count + 1
        assert sample_run_context.chunk_logs[-1] == "new log"

        # Assert immutable-like fields remain unchanged
        assert sample_run_context.error == original_error
        assert sample_run_context.span == original_span

    @pytest.mark.unit
    def test_run_context_with_none_optional_fields(self, mock_error: Mock) -> None:
        """Test RunContext with None values for optional fields."""
        # Act
        context = RunContext(
            error=mock_error,
            error_log="optional field test",
            chunk_logs=["test log"],
            span=None,  # Testing edge case with None span
            node_trace=None,  # Testing edge case with None node_trace
            meter=None,  # Testing edge case with None meter
        )

        # Assert
        assert context.error == mock_error
        assert context.error_log == "optional field test"
        assert context.chunk_logs == ["test log"]
        assert context.span is None
        assert context.node_trace is None
        assert context.meter is None

    @pytest.mark.unit
    def test_run_context_inheritance_structure(self) -> None:
        """Test RunContext class structure and inheritance."""
        # Verify class exists and is properly defined
        assert hasattr(RunContext, "__init__")
        assert hasattr(RunContext, "__dataclass_fields__")

        # Verify module location
        assert RunContext.__module__ == "api.v1.base_api"

        # Verify it's a dataclass
        assert dataclasses.is_dataclass(RunContext)
