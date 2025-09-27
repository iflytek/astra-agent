"""Test configuration exception module."""

from plugin.rpa.exceptions.config_exceptions import (
    ConfigNotFoundException,
    CreatTaskException,
    EnvNotFoundException,
    InvalidConfigException,
    QueryTaskException,
)


class TestConfigNotFoundException:
    """Test cases for ConfigNotFoundException."""

    def test_config_not_found_exception_creation(self) -> None:
        """Test creating ConfigNotFoundException."""
        path = "/path/to/config.env"
        exception = ConfigNotFoundException(path)

        assert isinstance(exception, Exception)
        assert exception.message == f"Configuration file not found at path: {path}"
        assert (
            str(exception)
            == f"[Exception] Configuration file not found at path: {path}"
        )

    def test_config_not_found_exception_inheritance(self) -> None:
        """Test ConfigNotFoundException inheritance."""
        path = "/path/to/config.env"
        exception = ConfigNotFoundException(path)

        assert isinstance(exception, Exception)
        assert isinstance(exception, ConfigNotFoundException)

    def test_config_not_found_exception_with_different_paths(self) -> None:
        """Test ConfigNotFoundException with different paths."""
        paths = [
            "/home/user/.env",
            "C:\\config\\app.env",
            "./relative/path.env",
            "/very/long/path/to/configuration/file.env",
        ]

        for path in paths:
            exception = ConfigNotFoundException(path)
            assert path in exception.message
            assert path in str(exception)

    def test_config_not_found_exception_message_format(self) -> None:
        """Test ConfigNotFoundException message format."""
        path = "/test/path"
        exception = ConfigNotFoundException(path)

        assert exception.message.startswith("Configuration file not found at path:")
        assert str(exception).startswith("[Exception]")


class TestEnvNotFoundException:
    """Test cases for EnvNotFoundException."""

    def test_env_not_found_exception_creation(self) -> None:
        """Test creating EnvNotFoundException."""
        env_key = "DATABASE_URL"
        exception = EnvNotFoundException(env_key)

        assert isinstance(exception, Exception)
        assert exception.message == f"Environment not found at key: {env_key}"
        assert str(exception) == f"[Exception] Environment not found at key: {env_key}"

    def test_env_not_found_exception_with_different_keys(self) -> None:
        """Test EnvNotFoundException with different environment variable keys."""
        keys = [
            "LOG_LEVEL",
            "API_SECRET_KEY",
            "REDIS_URL",
            "SMTP_PASSWORD",
            "JWT_SECRET",
        ]

        for key in keys:
            exception = EnvNotFoundException(key)
            assert key in exception.message
            assert key in str(exception)

    def test_env_not_found_exception_message_format(self) -> None:
        """Test EnvNotFoundException message format."""
        env_key = "TEST_KEY"
        exception = EnvNotFoundException(env_key)

        assert exception.message.startswith("Environment not found at key:")
        assert str(exception).startswith("[Exception]")


class TestInvalidConfigException:
    """Test cases for InvalidConfigException."""

    def test_invalid_config_exception_creation(self) -> None:
        """Test creating InvalidConfigException."""
        details = "Port number must be between 1 and 65535"
        exception = InvalidConfigException(details)

        assert isinstance(exception, Exception)
        assert exception.message == f"Invalid configuration: {details}"
        assert str(exception) == f"[Exception] Invalid configuration: {details}"

    def test_invalid_config_exception_with_different_details(self) -> None:
        """Test InvalidConfigException with different details."""
        details_list = [
            "URL is not valid",
            "Database connection failed",
            "Missing required field: username",
            "Invalid JSON format in config file",
            "Timeout value cannot be negative",
        ]

        for details in details_list:
            exception = InvalidConfigException(details)
            assert details in exception.message
            assert details in str(exception)

    def test_invalid_config_exception_message_format(self) -> None:
        """Test InvalidConfigException message format."""
        details = "Test details"
        exception = InvalidConfigException(details)

        assert exception.message.startswith("Invalid configuration:")
        assert str(exception).startswith("[Exception]")


class TestCreatTaskException:
    """Test cases for CreatTaskException."""

    def test_create_task_exception_creation(self) -> None:
        """Test creating CreatTaskException."""
        details = "API endpoint returned 500 error"
        exception = CreatTaskException(details)

        assert isinstance(exception, Exception)
        assert exception.message == f"Task creation failed: {details}"
        assert str(exception) == f"[Exception] Task creation failed: {details}"

    def test_create_task_exception_with_different_details(self) -> None:
        """Test CreatTaskException with different details."""
        details_list = [
            "Network timeout occurred",
            "Invalid project ID provided",
            "Insufficient permissions",
            "Rate limit exceeded",
            "Server maintenance in progress",
        ]

        for details in details_list:
            exception = CreatTaskException(details)
            assert details in exception.message
            assert details in str(exception)

    def test_create_task_exception_message_format(self) -> None:
        """Test CreatTaskException message format."""
        details = "Test error details"
        exception = CreatTaskException(details)

        assert exception.message.startswith("Task creation failed:")
        assert str(exception).startswith("[Exception]")


class TestQueryTaskException:
    """Test cases for QueryTaskException."""

    def test_query_task_exception_creation(self) -> None:
        """Test creating QueryTaskException."""
        details = "Task ID not found in database"
        exception = QueryTaskException(details)

        assert isinstance(exception, Exception)
        assert exception.message == f"Querying task status failed: {details}"
        assert str(exception) == f"[Exception] Querying task status failed: {details}"

    def test_query_task_exception_with_different_details(self) -> None:
        """Test QueryTaskException with different details."""
        details_list = [
            "Database connection lost",
            "Task has been deleted",
            "Invalid task ID format",
            "Access denied for this task",
            "Query timeout exceeded",
        ]

        for details in details_list:
            exception = QueryTaskException(details)
            assert details in exception.message
            assert details in str(exception)

    def test_query_task_exception_message_format(self) -> None:
        """Test QueryTaskException message format."""
        details = "Test query error"
        exception = QueryTaskException(details)

        assert exception.message.startswith("Querying task status failed:")
        assert str(exception).startswith("[Exception]")


class TestExceptionInteroperability:
    """Test exception interoperability."""

    def test_all_exceptions_are_exception_subclasses(self) -> None:
        """Test that all custom exceptions inherit from Exception."""
        exceptions = [
            ConfigNotFoundException("test"),
            EnvNotFoundException("test"),
            InvalidConfigException("test"),
            CreatTaskException("test"),
            QueryTaskException("test"),
        ]

        for exception in exceptions:
            assert isinstance(exception, Exception)

    def test_exception_catching_with_base_exception(self) -> None:
        """Test catching custom exceptions with base Exception class."""
        exceptions = [
            ConfigNotFoundException("test"),
            EnvNotFoundException("test"),
            InvalidConfigException("test"),
            CreatTaskException("test"),
            QueryTaskException("test"),
        ]

        for exception in exceptions:
            try:
                raise exception
            except (
                ConfigNotFoundException,
                EnvNotFoundException,
                InvalidConfigException,
                CreatTaskException,
                QueryTaskException,
            ) as e:
                assert isinstance(e, type(exception))

    def test_exception_message_consistency(self) -> None:
        """Test exception message consistency."""
        exceptions = [
            ConfigNotFoundException("test_path"),
            EnvNotFoundException("test_key"),
            InvalidConfigException("test_details"),
            CreatTaskException("test_details"),
            QueryTaskException("test_details"),
        ]

        for exception in exceptions:
            # All exceptions should have a message attribute
            assert hasattr(exception, "message")
            assert isinstance(exception.message, str)
            assert len(exception.message.strip()) > 0

            # All exceptions' __str__ method should return formatted strings
            str_repr = str(exception)
            assert str_repr.startswith("[Exception]")
            assert exception.message in str_repr

    def test_exception_args_parameter(self) -> None:
        """Test exception args parameter passing."""
        test_message = "test message"
        exceptions = [
            ConfigNotFoundException("test_path"),
            EnvNotFoundException("test_key"),
            InvalidConfigException(test_message),
            CreatTaskException(test_message),
            QueryTaskException(test_message),
        ]

        for exception in exceptions:
            # Exceptions should correctly pass messages to parent class
            assert len(exception.args) >= 1
            assert exception.message in exception.args[0]  # type: ignore[attr-defined]
