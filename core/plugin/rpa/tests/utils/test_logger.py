"""Test logging module."""

import os
import tempfile
from typing import Any, Dict
from unittest.mock import MagicMock, patch

import orjson
from loguru import logger
from plugin.rpa.utils.log.logger import VALID_LOG_LEVELS, patching, serialize, set_log


class TestLoggerUtilities:
    """Test cases for logger utility functions."""

    def test_serialize_function(self) -> None:
        """Test serialize function."""
        # Mock log record object
        mock_record: Dict[str, Any] = {
            "time": MagicMock(),
            "level": "INFO",
            "message": "Test message",
        }
        mock_record["time"].timestamp.return_value = 1234567890.123

        result = serialize(mock_record)

        assert isinstance(result, bytes)
        # Verify result contains timestamp
        data = orjson.loads(result)
        assert "timestamp" in data
        assert data["timestamp"] == 1234567890.123

    def test_patching_function(self) -> None:
        """Test patching function."""
        # Mock log record object
        mock_record: Dict[str, Any] = {"time": MagicMock(), "extra": {}}
        mock_record["time"].timestamp.return_value = 1234567890.123

        patching(mock_record)

        assert "serialized" in mock_record["extra"]
        assert isinstance(mock_record["extra"]["serialized"], bytes)

    def test_valid_log_levels_constant(self) -> None:
        """Test valid log levels constant."""
        expected_levels = ["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"]
        assert VALID_LOG_LEVELS == expected_levels


class TestSetLog:
    """Test cases for set_log function."""

    def teardown_method(self) -> None:
        """Cleanup method, remove all log handlers."""
        logger.remove()

    @patch("utils.log.logger.logger")
    def test_set_log_with_explicit_params(self, mock_logger: MagicMock) -> None:
        """Test setting log with explicit parameters."""
        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = os.path.join(temp_dir, "test.log")

            set_log(log_level="DEBUG", log_path=log_path)

            # Verify logger methods were called
            mock_logger.remove.assert_called_once()
            mock_logger.patch.assert_called_once()
            mock_logger.add.assert_called_once()

            # Get add method call arguments
            call_args = mock_logger.add.call_args
            assert call_args[1]["level"] == "DEBUG"
            assert log_path in call_args[1]["sink"]

    @patch("utils.log.logger.logger")
    @patch.dict(os.environ, {"LOG_LEVEL": "ERROR", "LOG_PATH": "/tmp/test_logs"})
    def test_set_log_with_env_vars(self, mock_logger: MagicMock) -> None:
        """Test setting log with environment variables."""
        set_log()

        # Verify log level from environment variable was used
        call_args = mock_logger.add.call_args
        assert call_args[1]["level"] == "ERROR"
        assert "/tmp/test_logs" in call_args[1]["sink"]

    @patch("utils.log.logger.logger")
    @patch("utils.log.logger.appdirs.user_cache_dir")
    def test_set_log_default_path(
        self, mock_user_cache_dir: MagicMock, mock_logger: MagicMock
    ) -> None:
        """Test using default log path."""
        mock_user_cache_dir.return_value = "/tmp/cache"

        with patch.dict(os.environ, {}, clear=True):
            set_log(log_level="INFO")

        # Verify default path was used
        call_args = mock_logger.add.call_args
        assert "/tmp/cache/rpa-server.log" in call_args[1]["sink"]

    @patch("utils.log.logger.logger")
    def test_set_log_default_level(self, mock_logger: MagicMock) -> None:
        """Test using default log level."""
        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = os.path.join(temp_dir, "test.log")

            with patch.dict(os.environ, {}, clear=True):
                set_log(log_path=log_path)

            # Verify default level INFO was used
            call_args = mock_logger.add.call_args
            assert call_args[1]["level"] == "INFO"

    @patch("utils.log.logger.logger")
    @patch.dict(os.environ, {"LOG_LEVEL": "INVALID_LEVEL"})
    def test_set_log_invalid_env_level(self, mock_logger: MagicMock) -> None:
        """Test invalid log level in environment variable."""
        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = os.path.join(temp_dir, "test.log")

            set_log(log_path=log_path)

            # Should use default level INFO since environment variable level is invalid
            call_args = mock_logger.add.call_args
            assert call_args[1]["level"] == "INFO"

    @patch("utils.log.logger.logger")
    def test_set_log_creates_directory(self, _mock_logger: MagicMock) -> None:
        """Test log function creates non-existent directory."""
        with tempfile.TemporaryDirectory() as temp_dir:
            # Create a non-existent subdirectory path
            log_path = os.path.join(temp_dir, "nested", "dir", "test.log")

            set_log(log_level="DEBUG", log_path=log_path)

            # Verify directory was created
            assert os.path.exists(os.path.dirname(log_path))

    @patch("utils.log.logger.logger")
    def test_set_log_format_and_rotation(self, mock_logger: MagicMock) -> None:
        """Test log format and rotation configuration."""
        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = os.path.join(temp_dir, "test.log")

            set_log(log_level="WARNING", log_path=log_path)

            call_args = mock_logger.add.call_args

            # Verify log format
            expected_format = (
                "{level} | {time:YYYY-MM-DD HH:mm:ss} | {process} - {thread} "
                "| {file} - {function}: {line} {message}"
            )
            assert call_args[1]["format"] == expected_format

            # Verify rotation configuration
            assert call_args[1]["rotation"] == "10 MB"

    @patch("utils.log.logger.logger")
    @patch.dict(os.environ, {"LOG_LEVEL": "DEBUG"})
    def test_set_log_env_level_priority(self, mock_logger: MagicMock) -> None:
        """Test env variable priority: use env var when explicit param is None."""
        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = os.path.join(temp_dir, "test.log")

            set_log(log_level=None, log_path=log_path)

            call_args = mock_logger.add.call_args
            assert call_args[1]["level"] == "DEBUG"

    @patch("utils.log.logger.logger")
    @patch.dict(os.environ, {"LOG_LEVEL": "ERROR"})
    def test_set_log_explicit_level_overrides_env(self, mock_logger: MagicMock) -> None:
        """Test explicit parameter overrides environment variable."""
        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = os.path.join(temp_dir, "test.log")

            set_log(log_level="WARNING", log_path=log_path)

            call_args = mock_logger.add.call_args
            assert (
                call_args[1]["level"] == "WARNING"
            )  # Use explicit parameter, not environment variable
